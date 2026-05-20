package eu.xfsc.fc.core.service.trustframework;

import eu.xfsc.fc.core.dao.trustframework.TrustFramework;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkMapper;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRepository;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRoleState;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRoleStateId;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRoleStateRepository;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
  private final TrustFrameworkRoleStateRepository roleStateRepository;
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
   * Replaces the service URL, API version, and timeout for the trust framework identified
   * by the given family ID. Returns the updated config, or empty when no record exists.
   */
  @Transactional
  public Optional<TrustFrameworkConfig> updateConfig(String family, String serviceUrl, String apiVersion,
                                                     int timeoutSeconds) {
    return trustFrameworkRepository.findById(family).map(entity -> {
      entity.setServiceUrl(serviceUrl);
      entity.setApiVersion(apiVersion);
      entity.setTimeoutSeconds(timeoutSeconds);
      trustFrameworkRepository.save(entity);
      return TrustFrameworkMapper.toConfig(entity);
    });
  }

  /**
   * Returns true if the named role in the given bundle profile is enabled.
   * Absence of a persisted state row means the role is enabled by default.
   *
   * <p><strong>Bundle-off dominance (AC-1 bullet 4):</strong> when the bundle's family is
   * disabled in persistence, this method returns {@code true} regardless of the role's
   * persisted state. The role-toggle layer has no effect while the bundle is off — rejection
   * is handled by the bundle-disabled pathway in the caller. This prevents a double-rejection
   * and makes role-toggle state semantically inert for disabled bundles.
   *
   * <p>This method does not validate that the bundle or role is registered —
   * call sites that need validation should use {@link #setRoleEnabled} or
   * {@link #getRoleStates}, which both throw on unknown input.
   *
   * @param frameworkId registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @param roleName    role name from the bundle (e.g. {@code Participant})
   * @return true if enabled (including default when no row exists, or when bundle family is
   *         disabled), false if explicitly disabled and the bundle family is enabled
   */
  @Transactional(readOnly = true)
  public boolean isRoleEnabled(String frameworkId, String roleName) {
    // Bundle-off dominance: if the bundle's family is disabled, the role-toggle is bypassed.
    // Unknown frameworkId → no bundle → no family to look up → fall through to role-state check.
    boolean familyDisabled = trustFrameworkRegistry.getBundle(frameworkId)
        .map(bundle -> bundle.config().family())
        .flatMap(trustFrameworkRepository::findById)
        .map(tf -> !tf.isEnabled())
        .orElse(false);
    if (familyDisabled) {
      return true;
    }
    return roleStateRepository.findByFrameworkIdAndRoleName(frameworkId, roleName)
        .map(TrustFrameworkRoleState::isEnabled)
        .orElse(true);
  }

  /**
   * Persists the enabled/disabled state for a named role in the given bundle profile.
   * Creates or updates the state row (upsert). Validates that the bundle and role both
   * exist in the registry before writing.
   *
   * @param frameworkId registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @param roleName    role name from the bundle (e.g. {@code Participant})
   * @param enabled     new enabled state
   * @throws NotFoundException when the bundle profile ID or the role name is not registered
   */
  @Transactional
  public void setRoleEnabled(String frameworkId, String roleName, boolean enabled) {
    if (trustFrameworkRegistry.getBundle(frameworkId).isEmpty()) {
      throw new NotFoundException("Trust framework bundle not found in registry: " + frameworkId);
    }
    Set<String> knownRoles = trustFrameworkRegistry.getEffectiveRoles(frameworkId);
    if (!knownRoles.contains(roleName)) {
      throw new NotFoundException(
          "Role '" + roleName + "' not declared in bundle '" + frameworkId + "'");
    }
    var id = new TrustFrameworkRoleStateId(frameworkId, roleName);
    var state = roleStateRepository.findById(id)
        .orElseGet(() -> new TrustFrameworkRoleState(frameworkId, roleName, enabled));
    state.setEnabled(enabled);
    roleStateRepository.save(state);
  }

  /**
   * Returns the full map of role name → effective enabled state for all roles declared in the
   * given bundle. Merges persisted overrides with the default-true for absent rows.
   *
   * @param frameworkId registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @return map of role name → effective enabled state; never null; preserves declaration order
   * @throws NotFoundException when the bundle profile ID is not registered in the registry
   */
  @Transactional(readOnly = true)
  public Map<String, Boolean> getRoleStates(String frameworkId) {
    if (trustFrameworkRegistry.getBundle(frameworkId).isEmpty()) {
      throw new NotFoundException("Trust framework bundle not found in registry: " + frameworkId);
    }
    Set<String> knownRoles = trustFrameworkRegistry.getEffectiveRoles(frameworkId);
    // Index persisted overrides by role name for O(1) lookup
    Map<String, Boolean> persisted = roleStateRepository.findByFrameworkId(frameworkId).stream()
        .collect(Collectors.toMap(
            TrustFrameworkRoleState::getRoleName,
            TrustFrameworkRoleState::isEnabled));

    Map<String, Boolean> result = new LinkedHashMap<>();
    for (String role : knownRoles) {
      result.put(role, persisted.getOrDefault(role, true));
    }
    return result;
  }
}
