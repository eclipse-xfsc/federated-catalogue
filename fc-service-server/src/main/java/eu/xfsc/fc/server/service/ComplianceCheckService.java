package eu.xfsc.fc.server.service;

import java.util.List;

import eu.xfsc.fc.core.service.trustframework.compliance.TrustFrameworkProfileConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.ComplianceCheckRequest;
import eu.xfsc.fc.api.generated.model.ComplianceCheckResult;
import eu.xfsc.fc.api.generated.model.StoredValidationResult;
import eu.xfsc.fc.core.exception.ServiceUnavailableException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkProfileResolver;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceCheckOrchestrator;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceCheckOutcome;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceResultStore;
import eu.xfsc.fc.core.service.trustframework.compliance.FailureCategory;
import eu.xfsc.fc.core.service.trustframework.compliance.IssuedAttestation;
import eu.xfsc.fc.core.service.trustframework.compliance.UnverifiableAttestation;
import eu.xfsc.fc.server.generated.controller.ComplianceApiDelegate;
import eu.xfsc.fc.server.util.OffsetBasedPageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP delegate for compliance check endpoints. Orchestrates a compliance check,
 * persists the result, and maps outcomes to API DTOs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceCheckService implements ComplianceApiDelegate {

  private final ComplianceCheckOrchestrator orchestrator;
  private final ComplianceResultStore resultStore;
  private final TrustFrameworkProfileResolver profileResolver;

  @Override
  public ResponseEntity<ComplianceCheckResult> runComplianceCheck(String assetId,
                                                                  ComplianceCheckRequest request) {
    log.debug("runComplianceCheck; assetId={}, frameworkProfileId={}", assetId,
        request.getFrameworkProfileId());

    ComplianceCheckOutcome outcome;
    try {
      outcome = orchestrator.check(assetId, request.getFrameworkProfileId(), request.getCredential());
    } catch (ServiceUnavailableException | TimeoutException e) {
      // The trust service was never reached, so no ComplianceCheckOutcome exists to store.
      // Persist a failed-attempt audit record, then rethrow unchanged so the client still
      // receives the original 503/504 response.
      persistFailedAttempt(assetId, request.getFrameworkProfileId(), e);
      throw e;
    }

    String familyId = resolveFamilyId(request.getFrameworkProfileId());
    resultStore.store(assetId, request.getFrameworkProfileId(), familyId, outcome);

    return ResponseEntity.ok(toDto(outcome));
  }

  @Override
  public ResponseEntity<List<StoredValidationResult>> getComplianceChecks(String assetId,
                                                                          Integer offset, Integer limit) {
    log.debug("getComplianceChecks; assetId={}", assetId);

    int effectiveOffset = offset != null ? offset : 0;
    int effectiveLimit = limit != null ? limit : 100;

    List<StoredValidationResult> results =
        resultStore.findByAssetId(assetId,
                new OffsetBasedPageRequest(effectiveOffset, effectiveLimit))
            .stream()
            .map(ValidationResultMapper::toDto)
            .toList();

    return ResponseEntity.ok(results);
  }

  private String resolveFamilyId(String frameworkProfileId) {
    return profileResolver.getProfileConfig(frameworkProfileId)
        .map(TrustFrameworkProfileConfig::familyId)
        .orElse(frameworkProfileId);
  }

  /**
   * Persists a failed-attempt audit record for a compliance check that could not reach the trust
   * service. A failure to persist this record is logged and swallowed rather than propagated: the
   * original service-unavailable/timeout failure is the one the caller must see, and letting a
   * secondary persistence error replace or mask it would hide the real cause from the client and
   * from this audit trail alike.
   */
  private void persistFailedAttempt(String assetId, String frameworkProfileId, RuntimeException cause) {
    try {
      FailureCategory category = cause instanceof TimeoutException
          ? FailureCategory.SERVICE_TIMEOUT
          : FailureCategory.SERVICE_UNREACHABLE;
      String familyId = resolveFamilyId(frameworkProfileId);
      resultStore.storeFailedAttempt(assetId, frameworkProfileId, familyId, category, cause.getMessage());
    } catch (RuntimeException persistError) {
      log.error("Failed to persist failed-attempt audit record for assetId={}, frameworkProfileId={}; "
          + "original failure was: {}", assetId, frameworkProfileId, cause.getMessage(), persistError);
    }
  }

  private ComplianceCheckResult toDto(ComplianceCheckOutcome outcome) {
    ComplianceCheckResult result = new ComplianceCheckResult();
    result.setConforms(outcome.compliant());
    switch (outcome) {
      case IssuedAttestation ia -> result.setAttestationCredential(ia.attestationCredential());
      case UnverifiableAttestation ua -> result.setFailureCategory(ua.failureCategory().name());
    }
    return result;
  }
}
