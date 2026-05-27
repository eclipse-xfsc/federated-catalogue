package eu.xfsc.fc.server.service;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.TrustFrameworkBundleEntry;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEntry;
import eu.xfsc.fc.api.generated.model.TrustFrameworkPatch;
import eu.xfsc.fc.api.generated.model.TrustFrameworkRolePatch;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundleConfigService;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundleConfigService.Field;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundleConfigService.Overrides;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.server.generated.controller.TrustFrameworkAdminApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP delegate for the trust framework admin endpoints. Delegates persistence operations
 * to {@link TrustFrameworkService}; only HTTP-shape concerns (status codes, DTO mapping)
 * live here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustFrameworkAdminService implements TrustFrameworkAdminApiDelegate {

  private final TrustFrameworkService trustFrameworkService;
  private final TrustFrameworkRegistry trustFrameworkRegistry;
  private final TrustFrameworkBundleConfigService bundleConfigService;

  @Value("${federated-catalogue.enabled-trust-frameworks:}")
  private List<String> enabledTrustFrameworkFamilies;

  /**
   * Flips DB rows to {@code enabled=true} for each trust framework family listed in
   * {@code federated-catalogue.enabled-trust-frameworks} (env:
   * {@code FEDERATED_CATALOGUE_ENABLED_TRUST_FRAMEWORKS}). Unknown family IDs are ignored.
   * This is the deployment-time override path; runtime changes go through the admin API.
   */
  @PostConstruct
  private void seedEnabledFrameworksFromConfig() {
    for (String family : enabledTrustFrameworkFamilies) {
      if (family == null || family.isBlank()) {
        continue;
      }
      String id = family.trim();
      try {
        trustFrameworkService.setEnabled(id, true);
        log.info("seedEnabledFrameworksFromConfig; enabled trust framework '{}' from config", id);
      } catch (NotFoundException e) {
        log.warn("seedEnabledFrameworksFromConfig; trust framework '{}' not registered — override ignored", id);
      }
    }
  }

  @Override
  public ResponseEntity<List<TrustFrameworkEntry>> getTrustFrameworks() {
    List<TrustFrameworkEntry> entries = trustFrameworkService.findAll().stream()
        .map(this::toEntry)
        .toList();
    return ResponseEntity.ok(entries);
  }

  /**
   * Applies a merge-patch to the identified trust framework family. Supports toggling the
   * enabled state. Returns 404 if the family identifier is not registered.
   *
   * @param id    trust framework family identifier
   * @param patch fields to update; only non-null fields are applied
   * @return 200 on success, 404 if unknown id
   */
  @Override
  public ResponseEntity<Void> patchTrustFramework(String id, TrustFrameworkPatch patch) {
    if (patch.getEnabled() == null) {
      throw new ClientException("Patch body must contain at least one field");
    }
    trustFrameworkService.setEnabled(id, patch.getEnabled());
    return ResponseEntity.ok().build();
  }

  /**
   * Applies a merge-patch (RFC 7396) to the external client identifiers of a single
   * bundle.
   * <ul>
   *   <li>A property with a non-null value overrides the YAML for that field.</li>
   *   <li>A property explicitly set to JSON null clears the override and lets the YAML
   *       value flow through again.</li>
   *   <li>An omitted property leaves the existing state unchanged.</li>
   * </ul>
   * The change takes effect on the next compliance call — no restart required.
   *
   * @param bundleId registry bundle profile ID
   * @param body     decoded merge-patch object (set of property → value)
   * @return 200 on success, 400 if the body is empty or contains unknown / mistyped
   * properties, 404 if the bundle is not registered
   */
  @Override
  public ResponseEntity<Void> patchTrustFrameworkBundleConfig(
      String bundleId, Map<String, Object> patch) {
    if (patch == null || patch.isEmpty()) {
      throw new ClientException("Patch body must contain at least one field");
    }

    Overrides overrides = Overrides.empty();
    Set<Field> fieldsToClear = EnumSet.noneOf(Field.class);
    for (Map.Entry<String, Object> entry : patch.entrySet()) {
      Field field = parseField(entry.getKey());
      Object value = entry.getValue();
      if (value == null) {
        fieldsToClear.add(field);
      } else {
        overrides.put(field, coerce(field, value));
      }
    }

    bundleConfigService.applyPatch(bundleId, overrides, fieldsToClear);
    return ResponseEntity.ok().build();
  }

  private static Field parseField(String jsonName) {
    try {
      return Field.fromJsonName(jsonName);
    } catch (IllegalArgumentException e) {
      throw new ClientException("Unknown bundle config property: " + jsonName);
    }
  }

  private static Object coerce(Field field, Object value) {
    return switch (field) {
      case TIMEOUT_SECONDS -> {
        if (value instanceof Integer i) {
          yield i;
        }
        if (value instanceof Number n) {
          yield n.intValue();
        }
        throw new ClientException("Property '" + field.jsonName()
            + "' must be a JSON integer");
      }
      case CLIENT_TYPE, SERVICE_URL, COMPLIANCE_PATH, API_VERSION, TRUST_ANCHOR_URL -> {
        if (value instanceof String s) {
          yield s;
        }
        throw new ClientException("Property '" + field.jsonName()
            + "' must be a JSON string");
      }
    };
  }

  /**
   * Clears every persisted override for the given bundle, reverting the runtime
   * configuration to the YAML baseline. Idempotent.
   *
   * @param bundleId registry bundle profile ID
   * @return 200 on success, 404 if the bundle is not registered
   */
  @Override
  public ResponseEntity<Void> deleteTrustFrameworkBundleConfig(String bundleId) {
    bundleConfigService.clear(bundleId);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> patchTrustFrameworkRole(String bundleId, String roleName,
                                                      TrustFrameworkRolePatch patch) {
    if (patch.getEnabled() == null) {
      throw new ClientException("Patch body must contain at least one field");
    }
    trustFrameworkService.setRoleEnabled(bundleId, roleName, patch.getEnabled());
    return ResponseEntity.ok().build();
  }

  private TrustFrameworkEntry toEntry(TrustFrameworkConfig config) {
    TrustFrameworkEntry entry = new TrustFrameworkEntry();
    entry.setId(config.id());
    entry.setName(config.name());
    entry.setEnabled(config.enabled());
    entry.setBundles(buildBundleEntries(config.id()));
    return entry;
  }

  /**
   * Builds a list of {@link TrustFrameworkBundleEntry} for each bundle belonging to the given
   * family, carrying the per-bundle role enabled states so the UI can address role-toggle requests
   * by the correct bundle profile ID.
   *
   * @param familyId trust framework family identifier (e.g. {@code gaia-x})
   * @return list of bundle entries in registry order; empty if no bundles belong to the family
   */
  private List<TrustFrameworkBundleEntry> buildBundleEntries(String familyId) {
    List<TrustFrameworkBundleEntry> result = new ArrayList<>();
    for (TrustFrameworkBundle bundle : trustFrameworkRegistry.getAllBundles()) {
      if (!familyId.equals(bundle.config().family())) {
        continue;
      }
      TrustFrameworkBundleEntry bundleEntry = new TrustFrameworkBundleEntry();
      bundleEntry.setId(bundle.config().id());
      bundleEntry.setRoles(trustFrameworkService.getRoleStates(bundle.config().id()));
      result.add(bundleEntry);
    }
    return result;
  }

}
