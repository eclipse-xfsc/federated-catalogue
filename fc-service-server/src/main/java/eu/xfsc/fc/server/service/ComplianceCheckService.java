package eu.xfsc.fc.server.service;

import java.util.List;

import eu.xfsc.fc.core.service.trustframework.compliance.TrustFrameworkProfileConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.ComplianceCheckRequest;
import eu.xfsc.fc.api.generated.model.ComplianceCheckResult;
import eu.xfsc.fc.api.generated.model.StoredValidationResult;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkProfileResolver;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceCheckOrchestrator;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceCheckOutcome;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceResultStore;
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

    ComplianceCheckOutcome outcome = orchestrator.check(assetId, request.getFrameworkProfileId(),
        request.getCredential());

    String familyId = profileResolver.getProfileConfig(request.getFrameworkProfileId())
        .map(TrustFrameworkProfileConfig::familyId)
        .orElse(request.getFrameworkProfileId());

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
