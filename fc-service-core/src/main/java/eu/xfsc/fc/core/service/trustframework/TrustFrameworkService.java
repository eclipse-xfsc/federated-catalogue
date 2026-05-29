package eu.xfsc.fc.core.service.trustframework;

import eu.xfsc.fc.core.dao.trustframework.TrustFramework;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBaseClassState;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBaseClassStateId;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBaseClassStateRepository;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkMapper;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRepository;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedSet;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Public service for the trust framework bounded context. Mediates all access to the
 * persisted enabled/config state so callers in other contexts (verification, admin API)
 * do not inject the repository directly.
 */
@Service
@RequiredArgsConstructor
public class TrustFrameworkService {

  private final TrustFrameworkRepository trustFrameworkRepository;
  private final TrustFrameworkBaseClassStateRepository baseClassStateRepository;
  private final TrustFrameworkRegistry trustFrameworkRegistry;

  /**
   * Returns true if the trust framework identified by the given family ID has its enabled
   * flag set in persistence. Returns false when the family is not registered.
   */
  public boolean isEnabled(String family) {
    return trustFrameworkRepository.findById(family)
        .map(TrustFramework::isEnabled)
        .orElse(false);
  }

  /**
   * Returns true when at least one trust framework is enabled. Used by the verification
   * pipeline to switch between strict mode (credentials must declare a recognized class)
   * and permissive mode (untyped credentials are accepted).
   */
  public boolean hasAnyEnabled() {
    return trustFrameworkRepository.countByEnabledTrue() > 0;
  }

  /**
   * Sets the enabled flag for the trust framework identified by the given family ID.
   * Throws when no record exists for the family.
   */
  @Transactional
  public void setEnabled(String family, boolean enabled) {
    TrustFramework entity = trustFrameworkRepository.findById(family)
        .orElseThrow(() -> new NotFoundException("Trust framework not found: " + family));
    entity.setEnabled(enabled);
    trustFrameworkRepository.save(entity);
  }

  /**
   * Returns the configuration for the trust framework identified by the given family ID,
   * or empty when the family is not registered.
   */
  public Optional<TrustFrameworkConfig> findByFamily(String family) {
    return trustFrameworkRepository.findById(family)
        .map(TrustFrameworkMapper::toConfig);
  }

  /**
   * Returns the configuration for every registered trust framework.
   */
  public List<TrustFrameworkConfig> findAll() {
    return trustFrameworkRepository.findAll().stream()
        .map(TrustFrameworkMapper::toConfig)
        .toList();
  }

  /**
   * Returns true if the named base class in the given bundle profile is enabled.
   * Absence of a persisted state row means the base class is enabled by default.
   *
   * <p><strong>Bundle-off dominance:</strong> when the bundle's family is
   * disabled in persistence, this method returns {@code true} regardless of the base class's
   * persisted state. The base-class-toggle layer has no effect while the bundle is off —
   * rejection is handled by the bundle-disabled pathway in the caller. This prevents a
   * double-rejection and makes base-class-toggle state semantically inert for disabled bundles.
   *
   * <p>This method does not validate that the bundle or base class is registered —
   * call sites that need validation should use {@link #setBaseClassEnabled} or
   * {@link #getBaseClassStates}, which both throw on unknown input.
   *
   * @param frameworkId   registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @param baseClassName base-class name from the bundle (e.g. {@code Participant})
   * @return true if enabled (including default when no row exists, or when bundle family is
   *         disabled), false if explicitly disabled and the bundle family is enabled
   */
  @Transactional(readOnly = true)
  public boolean isBaseClassEnabled(String frameworkId, String baseClassName) {
    // Bundle-off dominance: if the bundle's family is disabled, the base-class-toggle is bypassed.
    // Unknown frameworkId → no bundle → no family to look up → fall through to base-class-state check.
    boolean familyDisabled = trustFrameworkRegistry.getBundle(frameworkId)
        .map(bundle -> bundle.config().family())
        .flatMap(trustFrameworkRepository::findById)
        .map(tf -> !tf.isEnabled())
        .orElse(false);
    if (familyDisabled) {
      return true;
    }
    return baseClassStateRepository.findByFrameworkIdAndBaseClassName(frameworkId, baseClassName)
        .map(TrustFrameworkBaseClassState::isEnabled)
        .orElse(true);
  }

  /**
   * Persists the enabled/disabled state for a named base class in the given bundle profile.
   * Creates or updates the state row (upsert). Validates that the bundle and base class both
   * exist in the registry before writing.
   *
   * @param frameworkId   registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @param baseClassName base-class name from the bundle (e.g. {@code Participant})
   * @param enabled       new enabled state
   * @throws NotFoundException when the bundle profile ID or the base-class name is not registered
   */
  @Transactional
  public void setBaseClassEnabled(String frameworkId, String baseClassName, boolean enabled) {
    if (trustFrameworkRegistry.getBundle(frameworkId).isEmpty()) {
      throw new NotFoundException("Trust framework bundle not found in registry: " + frameworkId);
    }
    SequencedSet<String> knownBaseClasses = trustFrameworkRegistry.getEffectiveBaseClasses(frameworkId);
    if (!knownBaseClasses.contains(baseClassName)) {
      throw new NotFoundException(
          "Base class '%s' not declared in bundle '%s'".formatted(baseClassName, frameworkId));
    }
    var id = new TrustFrameworkBaseClassStateId(frameworkId, baseClassName);
    var state = baseClassStateRepository.findById(id)
        .map(existing -> {
          existing.setEnabled(enabled);
          return existing;
        })
        .orElseGet(() -> new TrustFrameworkBaseClassState(frameworkId, baseClassName, enabled));
    baseClassStateRepository.save(state);
  }

  /**
   * Returns the full map of base-class name → effective enabled state for all base classes declared
   * in the given bundle. Merges persisted overrides with the default-true for absent rows.
   *
   * @param frameworkId registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @return map of base-class name → effective enabled state; never null; preserves declaration order
   * @throws NotFoundException when the bundle profile ID is not registered in the registry
   */
  @Transactional(readOnly = true)
  public Map<String, Boolean> getBaseClassStates(String frameworkId) {
    if (trustFrameworkRegistry.getBundle(frameworkId).isEmpty()) {
      throw new NotFoundException("Trust framework bundle not found in registry: " + frameworkId);
    }
    SequencedSet<String> knownBaseClasses = trustFrameworkRegistry.getEffectiveBaseClasses(frameworkId);
    // Index persisted overrides by base-class name for O(1) lookup
    Map<String, Boolean> persisted = baseClassStateRepository.findByFrameworkId(frameworkId).stream()
        .collect(Collectors.toMap(
            TrustFrameworkBaseClassState::getBaseClassName,
            TrustFrameworkBaseClassState::isEnabled));

    Map<String, Boolean> result = new LinkedHashMap<>();
    for (String baseClassName : knownBaseClasses) {
      result.put(baseClassName, persisted.getOrDefault(baseClassName, true));
    }
    return result;
  }
}
