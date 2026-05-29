package eu.xfsc.fc.core.service.trustframework;

import java.util.Optional;

import org.springframework.stereotype.Service;

import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBundleConfig;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBundleConfigRepository;
import eu.xfsc.fc.core.service.trustframework.compliance.TrustFrameworkProfileConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves the effective {@link TrustFrameworkProfileConfig} for a given bundle profile ID
 * by combining the YAML baseline from {@link TrustFrameworkRegistry} with optional
 * per-bundle overrides persisted in {@link TrustFrameworkBundleConfigRepository}.
 *
 * <p>Precedence per field is DB override (non-null column) > YAML value > hard-coded
 * default. Absence of an override row, or a NULL column on an existing row, lets the
 * YAML value flow through.
 *
 * <p>Override rows for bundle IDs that are not registered with the registry are ignored
 * with a warning so a renamed or removed bundle does not break compliance resolution.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustFrameworkProfileResolver {

  private final TrustFrameworkRegistry registry;
  private final TrustFrameworkBundleConfigRepository overrideRepository;

  /**
   * Returns the effective compliance configuration for the given bundle profile ID, with
   * any persisted per-bundle overrides applied on top of the YAML baseline.
   *
   * @param profileId registry bundle profile ID (e.g. {@code gaia-x-2511})
   * @return effective configuration; empty when no bundle is registered for the ID
   */
  public Optional<TrustFrameworkProfileConfig> getProfileConfig(String profileId) {
    Optional<TrustFrameworkProfileConfig> baseline = registry.getProfileConfig(profileId);
    if (baseline.isEmpty()) {
      overrideRepository.findById(profileId).ifPresent(orphan ->
          log.warn("getProfileConfig; override row for unknown bundle '{}' ignored", profileId));
      return baseline;
    }
    return overrideRepository.findById(profileId)
        .map(override -> applyOverride(baseline.get(), override))
        .or(() -> baseline);
  }

  private TrustFrameworkProfileConfig applyOverride(
      TrustFrameworkProfileConfig base, TrustFrameworkBundleConfig override) {
    return new TrustFrameworkProfileConfig(
        base.frameworkProfileId(),
        base.familyId(),
        nonBlankOr(override.getClientType(), base.clientType()),
        nonBlankOr(override.getServiceUrl(), base.serviceUrl()),
        nonBlankOr(override.getCompliancePath(), base.compliancePath()),
        nonBlankOr(override.getApiVersion(), base.apiVersion()),
        override.getTimeoutSeconds() != null ? override.getTimeoutSeconds() : base.timeoutSeconds()
    );
  }

  private static String nonBlankOr(String candidate, String fallback) {
    return candidate != null && !candidate.isBlank() ? candidate : fallback;
  }
}
