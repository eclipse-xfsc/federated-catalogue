package eu.xfsc.fc.core.service.trustframework;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
// Function used for typed accessors of TrustFrameworkBundleConfig fields.

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBundleConfig;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBundleConfigRepository;
import eu.xfsc.fc.core.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Mediates writes to the per-bundle external client identifier overrides. The bundle must
 * be registered in {@link TrustFrameworkRegistry}; otherwise the write is rejected with
 * {@link NotFoundException}.
 *
 * <p>Reads happen through {@link TrustFrameworkProfileResolver}, which combines the YAML
 * baseline with rows persisted here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustFrameworkBundleConfigService {

  private final TrustFrameworkRegistry registry;
  private final TrustFrameworkBundleConfigRepository repository;

  /**
   * Applies a merge-patch to the per-bundle overrides. RFC 7396 semantics:
   * <ul>
   *   <li>A field name present in {@code overrides} sets the column to that value.</li>
   *   <li>A field name present in {@code fieldsToClear} sets the column back to NULL,
   *       letting the YAML value flow through again.</li>
   *   <li>A field name absent from both maps is left unchanged.</li>
   * </ul>
   * The same field name MUST NOT appear in both arguments; the caller is responsible
   * for partitioning the payload. Empty payloads are rejected here as a defensive
   * fallback to the HTTP-layer {@code minProperties: 1}.
   *
   * @param bundleId      registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @param overrides     values to persist; keyed by the field name (clientType,
   *                      serviceUrl, compliancePath, apiVersion, timeoutSeconds,
   *                      trustAnchorUrl)
   * @param fieldsToClear field names whose override should be removed
   * @throws NotFoundException        when no bundle is registered for the given ID
   * @throws IllegalArgumentException when both maps are empty
   */
  @Transactional
  public void applyPatch(String bundleId, Overrides overrides, Set<Field> fieldsToClear) {
    if (registry.getBundle(bundleId).isEmpty()) {
      throw new NotFoundException("Unknown trust-framework bundle: " + bundleId);
    }
    if (overrides.isEmpty() && fieldsToClear.isEmpty()) {
      throw new IllegalArgumentException("Patch must set or clear at least one field");
    }

    TrustFrameworkBundleConfig row = repository.findById(bundleId)
        .orElseGet(() -> TrustFrameworkBundleConfig.builder().bundleId(bundleId).build());

    overrides.applyTo(row);
    for (Field field : fieldsToClear) {
      field.clearer.accept(row, null);
    }

    repository.save(row);
    log.info("applyPatch; bundle '{}' overrides set={} cleared={}",
        bundleId, overrides.fieldNames(), fieldsToClear);
  }

  /**
   * Clears every persisted override for the given bundle, restoring the YAML baseline
   * as the sole source of compliance configuration. Idempotent — calling this when no
   * row exists is a no-op.
   *
   * @param bundleId registry bundle profile ID
   * @throws NotFoundException when no bundle is registered for the given ID
   */
  @Transactional
  public void clear(String bundleId) {
    if (registry.getBundle(bundleId).isEmpty()) {
      throw new NotFoundException("Unknown trust-framework bundle: " + bundleId);
    }
    if (repository.existsById(bundleId)) {
      repository.deleteById(bundleId);
      log.info("clear; bundle '{}' configuration overrides removed", bundleId);
    }
  }

  /**
   * Enumeration of the override fields that a merge-patch can address. Each constant
   * binds the wire JSON property name to setter / clearer accessors on
   * {@link TrustFrameworkBundleConfig}.
   */
  public enum Field {
    CLIENT_TYPE("clientType", "client_type",
        TrustFrameworkBundleConfig::getClientType,
        s -> s,
        (row, value) -> row.setClientType((String) value)),
    SERVICE_URL("serviceUrl", "service_url",
        TrustFrameworkBundleConfig::getServiceUrl,
        s -> s,
        (row, value) -> row.setServiceUrl((String) value)),
    COMPLIANCE_PATH("compliancePath", "compliance_path",
        TrustFrameworkBundleConfig::getCompliancePath,
        s -> s,
        (row, value) -> row.setCompliancePath((String) value)),
    API_VERSION("apiVersion", "api_version",
        TrustFrameworkBundleConfig::getApiVersion,
        s -> s,
        (row, value) -> row.setApiVersion((String) value)),
    TIMEOUT_SECONDS("timeoutSeconds", "timeout_seconds",
        TrustFrameworkBundleConfig::getTimeoutSeconds,
        Integer::parseInt,
        (row, value) -> row.setTimeoutSeconds((Integer) value)),
    TRUST_ANCHOR_URL("trustAnchorUrl", "trust_anchor_url",
        TrustFrameworkBundleConfig::getTrustAnchorUrl,
        s -> s,
        (row, value) -> row.setTrustAnchorUrl((String) value));

    private final String jsonName;
    private final String yamlKey;
    private final Function<TrustFrameworkBundleConfig, Object> overrideReader;
    private final Function<String, Object> yamlParser;
    private final BiConsumer<TrustFrameworkBundleConfig, Object> writer;
    private final BiConsumer<TrustFrameworkBundleConfig, Object> clearer;

    Field(String jsonName, String yamlKey,
          Function<TrustFrameworkBundleConfig, Object> overrideReader,
          Function<String, Object> yamlParser,
          BiConsumer<TrustFrameworkBundleConfig, Object> writer) {
      this.jsonName = jsonName;
      this.yamlKey = yamlKey;
      this.overrideReader = overrideReader;
      this.yamlParser = yamlParser;
      this.writer = writer;
      this.clearer = writer;
    }

    public String jsonName() {
      return jsonName;
    }

    public String yamlKey() {
      return yamlKey;
    }

    /**
     * Reads the persisted override value for this field, returning {@code null} when
     * the row's column is NULL (i.e. no override is active for this field).
     */
    public Object readOverride(TrustFrameworkBundleConfig row) {
      return overrideReader.apply(row);
    }

    /**
     * Coerces a raw YAML string value into the column type ({@code String} for most
     * fields, {@code Integer} for {@code timeoutSeconds}).
     */
    public Object parseYamlValue(String raw) {
      return raw == null ? null : yamlParser.apply(raw);
    }

    public static Field fromJsonName(String jsonName) {
      for (Field field : values()) {
        if (field.jsonName.equals(jsonName)) {
          return field;
        }
      }
      throw new IllegalArgumentException("Unknown bundle config field: " + jsonName);
    }
  }

  /**
   * Effective configuration of a bundle after merging persisted overrides over the YAML
   * baseline. Values are the typed payload (Strings for text fields, Integer for
   * {@code timeoutSeconds}).
   *
   * @param values           effective value per field; null when neither override nor YAML supplied one
   * @param overriddenFields fields whose effective value comes from the override row
   */
  public record EffectiveBundleConfig(
      Map<Field, Object> values,
      Set<Field> overriddenFields) {
  }

  /**
   * Resolves the effective configuration for a registered bundle by combining the
   * persisted override row (if any) with the YAML baseline read from
   * {@link TrustFrameworkBundle#config()}.
   *
   * @param bundleId registry bundle profile ID
   * @return effective configuration; {@link Optional#empty()} when the bundle is not registered
   */
  public Optional<EffectiveBundleConfig> getEffectiveConfig(String bundleId) {
    return registry.getBundle(bundleId).map(bundle -> {
      Map<String, String> yamlProps = bundle.config().properties();
      Optional<TrustFrameworkBundleConfig> override = repository.findById(bundleId);

      Map<Field, Object> values = new LinkedHashMap<>();
      Set<Field> overridden = java.util.EnumSet.noneOf(Field.class);
      for (Field field : Field.values()) {
        Object overrideValue = override.map(field::readOverride).orElse(null);
        if (overrideValue != null) {
          values.put(field, overrideValue);
          overridden.add(field);
        } else {
          String yamlRaw = yamlProps.get(field.yamlKey());
          if (yamlRaw != null) {
            values.put(field, field.parseYamlValue(yamlRaw));
          }
        }
      }
      return new EffectiveBundleConfig(values, overridden);
    });
  }

  /**
   * Type-safe carrier for the "set" half of a bundle-config merge-patch. Values are
   * pre-coerced to the column types ({@code String} or {@code Integer}); use the
   * static factory {@link Overrides#empty()} as a starting point and {@code put} to
   * accumulate.
   */
  public static final class Overrides {

    private final java.util.EnumMap<Field, Object> values = new java.util.EnumMap<>(Field.class);

    private Overrides() {
    }

    public static Overrides empty() {
      return new Overrides();
    }

    public Overrides put(Field field, Object value) {
      values.put(field, value);
      return this;
    }

    public boolean isEmpty() {
      return values.isEmpty();
    }

    public Set<String> fieldNames() {
      return values.keySet().stream().map(Field::jsonName)
          .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    void applyTo(TrustFrameworkBundleConfig row) {
      values.forEach((field, value) -> field.writer.accept(row, value));
    }
  }
}
