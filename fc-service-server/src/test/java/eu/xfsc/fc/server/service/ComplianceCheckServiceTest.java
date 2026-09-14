package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.ComplianceCheckRequest;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.exception.ServiceErrorException;
import eu.xfsc.fc.core.exception.ServiceUnavailableException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkProfileResolver;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceCheckOrchestrator;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceResultStore;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceResultStoreImpl;
import eu.xfsc.fc.core.service.trustframework.compliance.FailureCategory;
import eu.xfsc.fc.core.service.trustframework.compliance.IssuedAttestation;
import eu.xfsc.fc.core.service.trustframework.compliance.JwtVcComplianceClient;
import eu.xfsc.fc.core.service.trustframework.compliance.TrustFrameworkClientRegistry;
import eu.xfsc.fc.core.service.trustframework.compliance.TrustFrameworkProfileConfig;
import eu.xfsc.fc.core.service.trustframework.compliance.UnverifiableAttestation;
import eu.xfsc.fc.core.service.validation.ValidationResultRecord;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceCheckServiceTest {

  private static final String ASSET_ID = "urn:example:asset-001";
  private static final String PROFILE_ID = "gaia-x-2511";
  private static final String FAMILY_ID = "gaia-x";
  private static final String CANNED_VC_JWT = "eyJhbGciOiJub25lIn0.e30.";
  private static final String CREDENTIAL = "eyJhbGciOiJub25lIn0.payload.";
  private static final String CLIENT_TYPE = "jwt-vc-compliance";
  private static final String COMPLIANCE_PATH = "/api/credential-offers/standard-compliance";
  // Response delay used to force a real client-side read timeout; must exceed the 2s
  // timeoutSeconds configured in serviceWithRealOrchestrator.
  private static final int SLOW_RESPONSE_DELAY_SECONDS = 3;
  // VP JWT with payload {"id":"urn:example:asset-001"} — matches ASSET_ID so the real
  // JwtVcComplianceClient can extract a subject id and actually issue an HTTP request.
  private static final String VP_JWT_WITH_ASSET_ID =
      "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
          + ".eyJpZCI6InVybjpleGFtcGxlOmFzc2V0LTAwMSJ9.";

  @Mock
  private ComplianceCheckOrchestrator orchestrator;
  @Mock
  private ComplianceResultStore resultStore;
  @Mock
  private TrustFrameworkService trustFrameworkService;
  @Mock
  private TrustFrameworkRegistry registry;
  @Mock
  private TrustFrameworkProfileResolver profileResolver;

  @InjectMocks
  private ComplianceCheckService service;

  @Test
  void runComplianceCheck_issuedAttestation_returnsConformsTrueAndCredential() {
    var outcome = new IssuedAttestation(CANNED_VC_JWT, null);
    when(orchestrator.check(ASSET_ID, PROFILE_ID, CREDENTIAL)).thenReturn(outcome);
    when(profileResolver.getProfileConfig(PROFILE_ID)).thenReturn(Optional.empty());

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
    when(profileResolver.getProfileConfig(PROFILE_ID)).thenReturn(Optional.empty());

    var response = service.runComplianceCheck(ASSET_ID, request(PROFILE_ID, CREDENTIAL));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getConforms()).isFalse();
    assertThat(response.getBody().getFailureCategory()).isEqualTo("UNVERIFIABLE_ATTESTATION");
    assertThat(response.getBody().getAttestationCredential()).isNull();
  }

  @Test
  void runComplianceCheck_profileConfigPresent_storeReceivesFamilyIdFromRegistry() {
    var profileConfig = new TrustFrameworkProfileConfig(
        PROFILE_ID, FAMILY_ID, "jwt-vc-compliance", null, "/api/credential-offers/standard-compliance", "1.0", 30);
    var outcome = new IssuedAttestation(CANNED_VC_JWT, null);
    when(orchestrator.check(any(), any(), any())).thenReturn(outcome);
    when(profileResolver.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(profileConfig));

    service.runComplianceCheck(ASSET_ID, request(PROFILE_ID, CREDENTIAL));

    verify(resultStore).store(ASSET_ID, PROFILE_ID, FAMILY_ID, outcome);
  }

  @Test
  void runComplianceCheck_profileConfigAbsent_storeReceivesProfileIdAsFamilyId() {
    var outcome = new IssuedAttestation(CANNED_VC_JWT, null);
    when(orchestrator.check(any(), any(), any())).thenReturn(outcome);
    when(profileResolver.getProfileConfig(PROFILE_ID)).thenReturn(Optional.empty());

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
  void runComplianceCheck_trustServiceUnreachable_persistsFailedAttemptRecord() throws IOException {
    try (MockWebServer unreachableServer = new MockWebServer()) {
      unreachableServer.start();
      unreachableServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));
      ValidationResultStore validationResultStore = mock(ValidationResultStore.class);
      ComplianceCheckService serviceUnderTest = serviceWithRealOrchestrator(unreachableServer, validationResultStore);

      assertThrows(ServiceUnavailableException.class,
          () -> serviceUnderTest.runComplianceCheck(ASSET_ID, request(PROFILE_ID, VP_JWT_WITH_ASSET_ID)));

      ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
      // A failed attempt must never reach the graph: it is not a claim about the asset.
      verify(validationResultStore).storeWithoutGraphSync(captor.capture());
      verify(validationResultStore, never()).store(any());
      ValidationResultRecord record = captor.getValue();
      assertThat(record.assetIds()).containsExactly(ASSET_ID);
      assertThat(record.validatorIds()).contains(PROFILE_ID);
      assertThat(record.validatorIds()).contains(FAMILY_ID);
      assertThat(record.conforms()).isFalse();
      assertThat(record.validatedAt()).isNotNull();
      assertThat(failureCategoryOf(record.report())).isEqualTo(FailureCategory.SERVICE_UNREACHABLE.name());
    }
  }

  @Test
  void runComplianceCheck_trustServiceRespondsWithServerError_persistsServiceErrorRecord() throws IOException {
    try (MockWebServer erroringServer = new MockWebServer()) {
      erroringServer.start();
      erroringServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));
      ValidationResultStore validationResultStore = mock(ValidationResultStore.class);
      ComplianceCheckService serviceUnderTest = serviceWithRealOrchestrator(erroringServer, validationResultStore);

      assertThrows(ServiceErrorException.class,
          () -> serviceUnderTest.runComplianceCheck(ASSET_ID, request(PROFILE_ID, VP_JWT_WITH_ASSET_ID)));

      ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
      // A failed attempt must never reach the graph: it is not a claim about the asset.
      verify(validationResultStore).storeWithoutGraphSync(captor.capture());
      verify(validationResultStore, never()).store(any());
      ValidationResultRecord record = captor.getValue();
      assertThat(record.assetIds()).containsExactly(ASSET_ID);
      assertThat(record.validatorIds()).contains(PROFILE_ID);
      assertThat(record.validatorIds()).contains(FAMILY_ID);
      assertThat(record.conforms()).isFalse();
      assertThat(record.validatedAt()).isNotNull();
      // The trust service was reached but errored — must be distinguished from SERVICE_UNREACHABLE.
      assertThat(failureCategoryOf(record.report())).isEqualTo(FailureCategory.SERVICE_ERROR.name());
    }
  }

  @Test
  void runComplianceCheck_trustServiceTimesOut_persistsFailedAttemptRecord() throws IOException {
    try (MockWebServer slowServer = new MockWebServer()) {
      slowServer.start();
      slowServer.enqueue(new MockResponse()
          .setBodyDelay(SLOW_RESPONSE_DELAY_SECONDS, TimeUnit.SECONDS)
          .setResponseCode(201)
          .setBody(CANNED_VC_JWT));
      ValidationResultStore validationResultStore = mock(ValidationResultStore.class);
      ComplianceCheckService serviceUnderTest = serviceWithRealOrchestrator(slowServer, validationResultStore);

      assertThrows(TimeoutException.class,
          () -> serviceUnderTest.runComplianceCheck(ASSET_ID, request(PROFILE_ID, VP_JWT_WITH_ASSET_ID)));

      ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
      // A failed attempt must never reach the graph: it is not a claim about the asset.
      verify(validationResultStore).storeWithoutGraphSync(captor.capture());
      verify(validationResultStore, never()).store(any());
      ValidationResultRecord record = captor.getValue();
      assertThat(record.assetIds()).containsExactly(ASSET_ID);
      assertThat(record.validatorIds()).contains(PROFILE_ID);
      assertThat(record.validatorIds()).contains(FAMILY_ID);
      assertThat(record.conforms()).isFalse();
      assertThat(record.validatedAt()).isNotNull();
      assertThat(failureCategoryOf(record.report())).isEqualTo(FailureCategory.SERVICE_TIMEOUT.name());
    }
  }

  @Test
  void runComplianceCheck_trustServiceReachableAndCompliant_persistsIssuedAttestationRecordAsBefore()
      throws IOException {
    try (MockWebServer reachableServer = new MockWebServer()) {
      reachableServer.start();
      reachableServer.enqueue(new MockResponse()
          .setResponseCode(201)
          .setBody(CANNED_VC_JWT)
          .addHeader("Content-Type", "text/plain"));
      ValidationResultStore validationResultStore = mock(ValidationResultStore.class);
      when(validationResultStore.store(any())).thenReturn(7L);
      ComplianceCheckService serviceUnderTest = serviceWithRealOrchestrator(reachableServer, validationResultStore);

      var response = serviceUnderTest.runComplianceCheck(ASSET_ID, request(PROFILE_ID, VP_JWT_WITH_ASSET_ID));

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(response.getBody().getConforms()).isTrue();
      ArgumentCaptor<ValidationResultRecord> captor = ArgumentCaptor.forClass(ValidationResultRecord.class);
      verify(validationResultStore).store(captor.capture());
      ValidationResultRecord record = captor.getValue();
      assertThat(record.assetIds()).containsExactly(ASSET_ID);
      assertThat(record.validatorIds()).contains(PROFILE_ID);
      assertThat(record.conforms()).isTrue();
      assertThat(record.validatedAt()).isNotNull();
      assertThat(record.report()).contains(CANNED_VC_JWT);
    }
  }

  /**
   * Wires a {@link ComplianceCheckService} with a real {@link ComplianceCheckOrchestrator},
   * a real {@link JwtVcComplianceClient} pointed at the given local HTTP stub, and a real
   * {@link ComplianceResultStoreImpl} backed by the given (mocked) {@link ValidationResultStore}.
   * Only the trust-framework configuration/enablement lookups are stubbed.
   */
  private ComplianceCheckService serviceWithRealOrchestrator(MockWebServer server,
                                                             ValidationResultStore validationResultStore) {
    var profileConfig = new TrustFrameworkProfileConfig(
        PROFILE_ID, FAMILY_ID, CLIENT_TYPE, server.url("").toString(), COMPLIANCE_PATH, "1.0", 2);
    var profileResolverStub = mock(TrustFrameworkProfileResolver.class);
    when(profileResolverStub.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(profileConfig));
    var tfServiceStub = mock(TrustFrameworkService.class);
    when(tfServiceStub.isEnabled(FAMILY_ID)).thenReturn(true);
    var clientRegistryStub = mock(TrustFrameworkClientRegistry.class);
    when(clientRegistryStub.resolve(CLIENT_TYPE)).thenReturn(new JwtVcComplianceClient());
    var realOrchestrator = new ComplianceCheckOrchestrator(profileResolverStub, tfServiceStub, clientRegistryStub);
    var realResultStore = new ComplianceResultStoreImpl(validationResultStore, new ObjectMapper());
    return new ComplianceCheckService(realOrchestrator, realResultStore, profileResolverStub);
  }

  private static ComplianceCheckRequest request(String profileId, String credential) {
    return new ComplianceCheckRequest().frameworkProfileId(profileId).credential(credential);
  }

  /**
   * Extracts the {@code failureCategory} value from a persisted compliance report, so tests
   * assert the actual discriminator value rather than a substring that could also match
   * unrelated report text.
   */
  private static String failureCategoryOf(String report) throws JsonProcessingException {
    return new ObjectMapper().readTree(report).get("failureCategory").asText();
  }

}
