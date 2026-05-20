package eu.xfsc.fc.server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import eu.xfsc.fc.api.generated.model.ComplianceCheckRequest;
import eu.xfsc.fc.api.generated.model.TrustFrameworkPublicEntry;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.core.service.trustframework.ValidationType;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceCheckOrchestrator;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceResultStore;
import eu.xfsc.fc.core.service.trustframework.compliance.FailureCategory;
import eu.xfsc.fc.core.service.trustframework.compliance.IssuedAttestation;
import eu.xfsc.fc.core.service.trustframework.compliance.TrustFrameworkProfileConfig;
import eu.xfsc.fc.core.service.trustframework.compliance.UnverifiableAttestation;

@ExtendWith(MockitoExtension.class)
class ComplianceCheckServiceTest {

  private static final String ASSET_ID = "urn:example:asset-001";
  private static final String PROFILE_ID = "gaia-x-2511";
  private static final String FAMILY_ID = "gaia-x";
  private static final String CANNED_VC_JWT = "eyJhbGciOiJub25lIn0.e30.";
  private static final String CREDENTIAL = "eyJhbGciOiJub25lIn0.payload.";

  @Mock
  private ComplianceCheckOrchestrator orchestrator;
  @Mock
  private ComplianceResultStore resultStore;
  @Mock
  private TrustFrameworkService trustFrameworkService;
  @Mock
  private TrustFrameworkRegistry registry;

  @InjectMocks
  private ComplianceCheckService service;

  @Test
  void runComplianceCheck_issuedAttestation_returnsConformsTrueAndCredential() {
    var outcome = new IssuedAttestation(CANNED_VC_JWT, null);
    when(orchestrator.check(ASSET_ID, PROFILE_ID, CREDENTIAL)).thenReturn(outcome);
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.empty());

    var response = service.runComplianceCheck(ASSET_ID, request(PROFILE_ID, CREDENTIAL));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getConforms()).isTrue();
    assertThat(response.getBody().getAttestationCredential()).isEqualTo(CANNED_VC_JWT);
    assertThat(response.getBody().getFailureCategory()).isNull();
  }

  @Test
  void runComplianceCheck_unverifiableAttestation_returnsConformsFalseAndFailureCategory() {
    var outcome = new UnverifiableAttestation(FailureCategory.UNVERIFIABLE_ATTESTATION, "raw", "bad sig");
    when(orchestrator.check(ASSET_ID, PROFILE_ID, CREDENTIAL)).thenReturn(outcome);
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.empty());

    var response = service.runComplianceCheck(ASSET_ID, request(PROFILE_ID, CREDENTIAL));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getConforms()).isFalse();
    assertThat(response.getBody().getFailureCategory()).isEqualTo("UNVERIFIABLE_ATTESTATION");
    assertThat(response.getBody().getAttestationCredential()).isNull();
  }

  @Test
  void runComplianceCheck_profileConfigPresent_storeReceivesFamilyIdFromRegistry() {
    var profileConfig = new TrustFrameworkProfileConfig(PROFILE_ID, FAMILY_ID, "gxdch", null, "1.0", 30);
    var outcome = new IssuedAttestation(CANNED_VC_JWT, null);
    when(orchestrator.check(any(), any(), any())).thenReturn(outcome);
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(profileConfig));

    service.runComplianceCheck(ASSET_ID, request(PROFILE_ID, CREDENTIAL));

    verify(resultStore).store(ASSET_ID, PROFILE_ID, FAMILY_ID, outcome);
  }

  @Test
  void runComplianceCheck_profileConfigAbsent_storeReceivesProfileIdAsFamilyId() {
    var outcome = new IssuedAttestation(CANNED_VC_JWT, null);
    when(orchestrator.check(any(), any(), any())).thenReturn(outcome);
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.empty());

    service.runComplianceCheck(ASSET_ID, request(PROFILE_ID, CREDENTIAL));

    verify(resultStore).store(ASSET_ID, PROFILE_ID, PROFILE_ID, outcome);
  }

  @Test
  void getComplianceChecks_nullOffsetAndLimit_usesDefaultPagination() {
    when(resultStore.findByAssetId(eq(ASSET_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.getComplianceChecks(ASSET_ID, null, null);

    var captor = ArgumentCaptor.forClass(Pageable.class);
    verify(resultStore).findByAssetId(eq(ASSET_ID), captor.capture());
    assertThat(captor.getValue().getOffset()).isZero();
    assertThat(captor.getValue().getPageSize()).isEqualTo(100);
  }

  @Test
  void getComplianceChecks_explicitOffsetAndLimit_usesProvidedValues() {
    when(resultStore.findByAssetId(eq(ASSET_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of()));

    service.getComplianceChecks(ASSET_ID, 5, 25);

    var captor = ArgumentCaptor.forClass(Pageable.class);
    verify(resultStore).findByAssetId(eq(ASSET_ID), captor.capture());
    assertThat(captor.getValue().getOffset()).isEqualTo(5L);
    assertThat(captor.getValue().getPageSize()).isEqualTo(25);
  }

  @Test
  void getComplianceChecks_resultsMappedToDtos() {
    var entity = new ValidationResult();
    entity.setAssetIds(new String[] {ASSET_ID});
    entity.setValidatorIds(new String[] {PROFILE_ID});
    entity.setValidatorType(ValidatorType.TRUST_FRAMEWORK);
    entity.setConforms(true);
    entity.setValidatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    entity.setContentHash("abc123");
    when(resultStore.findByAssetId(eq(ASSET_ID), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));

    var response = service.getComplianceChecks(ASSET_ID, null, null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().getFirst().getConforms()).isTrue();
  }

  @Test
  void getTrustFrameworksPublic_disabledFrameworkExcluded() {
    var enabled = tfConfig("gaia-x", "GAIA-X", true);
    var disabled = tfConfig("untp", "UNTP", false);
    when(trustFrameworkService.findAll()).thenReturn(List.of(enabled, disabled));
    when(registry.getActiveBundles()).thenReturn(List.of());

    var response = service.getTrustFrameworksPublic();

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).extracting(TrustFrameworkPublicEntry::getId)
        .containsExactly("gaia-x")
        .doesNotContain("untp");
  }

  @Test
  void getTrustFrameworksPublic_profilesMatchedByFamilyId() {
    var tfCfg = tfConfig("gaia-x", "GAIA-X", true);
    when(trustFrameworkService.findAll()).thenReturn(List.of(tfCfg));
    var matchingBundle = bundle("gaia-x-2511", "gaia-x");
    var otherBundle = bundle("untp-v1", "untp");
    when(registry.getActiveBundles()).thenReturn(List.of(matchingBundle, otherBundle));

    var response = service.getTrustFrameworksPublic();

    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().getFirst().getProfiles())
        .containsExactly("gaia-x-2511")
        .doesNotContain("untp-v1");
  }

  @Test
  void getTrustFrameworksPublic_noActiveBundles_emptyProfilesList() {
    when(trustFrameworkService.findAll()).thenReturn(List.of(tfConfig("gaia-x", "GAIA-X", true)));
    when(registry.getActiveBundles()).thenReturn(List.of());

    var response = service.getTrustFrameworksPublic();

    assertThat(response.getBody()).hasSize(1);
    assertThat(response.getBody().getFirst().getProfiles()).isEmpty();
  }

  private static ComplianceCheckRequest request(String profileId, String credential) {
    return new ComplianceCheckRequest().frameworkProfileId(profileId).credential(credential);
  }

  private static TrustFrameworkConfig tfConfig(String id, String name, boolean enabled) {
    return new TrustFrameworkConfig(id, name, null, "1.0", 30, enabled, null, null);
  }

  private static TrustFrameworkBundle bundle(String profileId, String familyId) {
    var config = new FrameworkBundleConfig(profileId, familyId, "https://example.org/",
        ValidationType.SHACL, Map.of(), Map.of());
    return new TrustFrameworkBundle(config, null, null);
  }
}
