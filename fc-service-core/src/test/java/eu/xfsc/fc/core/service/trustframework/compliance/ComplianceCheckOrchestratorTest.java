package eu.xfsc.fc.core.service.trustframework.compliance;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.ServiceUnavailableException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;

/**
 * Unit tests for {@link ComplianceCheckOrchestrator}. All dependencies are mocked.
 * No Spring context required.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceCheckOrchestratorTest {

  private static final String PROFILE_ID = "mock-2026";
  private static final String FAMILY_ID = "mock";
  private static final String ASSET_ID = "https://example.com/asset-001";
  private static final String ASSET_PAYLOAD = "test-asset-payload";
  // JWT with payload {"id":"urn:example:test-asset-001"}
  private static final String VP_JWT_ASSET_ID = "urn:example:test-asset-001";
  private static final String VP_JWT_MATCHING =
      "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
          + ".eyJpZCI6InVybjpleGFtcGxlOnRlc3QtYXNzZXQtMDAxIn0.";
  // JWT with payload {"id":"urn:example:different-asset"} — id differs from ASSET_ID
  private static final String VP_JWT_MISMATCHED_ID =
      "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
          + ".eyJpZCI6InVybjpleGFtcGxlOmRpZmZlcmVudC1hc3NldCJ9.";
  private static final TrustFrameworkProfileConfig MOCK_CONFIG = new TrustFrameworkProfileConfig(
      PROFILE_ID, FAMILY_ID, "gxdch-loire", "http://localhost", "loire", 30);

  @Mock
  private TrustFrameworkRegistry registry;

  @Mock
  private TrustFrameworkService tfService;

  @Mock
  private TrustFrameworkClientRegistry clientRegistry;

  @Mock
  private TrustFrameworkClient mockClient;

  private ComplianceCheckOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator = new ComplianceCheckOrchestrator(registry, tfService, clientRegistry);
  }

  @Test
  void check_nullFrameworkProfileId_throwsClientException() {
    assertThrows(ClientException.class,
        () -> orchestrator.check(ASSET_ID, null, ASSET_PAYLOAD));
  }

  @Test
  void check_nullAssetPayload_throwsClientException() {
    assertThrows(ClientException.class,
        () -> orchestrator.check(ASSET_ID, PROFILE_ID, null));
  }

  @Test
  void check_unknownProfileId_throwsClientException() {
    when(registry.getProfileConfig("unknown-id")).thenReturn(Optional.empty());

    assertThrows(ClientException.class,
        () -> orchestrator.check(ASSET_ID, "unknown-id", ASSET_PAYLOAD));
  }

  @Test
  void check_familyDisabled_throwsConflictException() {
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(MOCK_CONFIG));
    when(tfService.isEnabled(FAMILY_ID)).thenReturn(false);

    assertThrows(ConflictException.class,
        () -> orchestrator.check(ASSET_ID, PROFILE_ID, ASSET_PAYLOAD));
  }

  @Test
  void check_clientRegistryThrowsIllegalArgument_throwsClientException() {
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(MOCK_CONFIG));
    when(tfService.isEnabled(FAMILY_ID)).thenReturn(true);
    when(clientRegistry.resolve("gxdch-loire")).thenThrow(new IllegalArgumentException("unknown clientType"));

    assertThrows(ClientException.class,
        () -> orchestrator.check(ASSET_ID, PROFILE_ID, ASSET_PAYLOAD));
  }

  @Test
  void check_familyEnabled_delegatesToClientAndReturnsOutcome() {
    var expected = new IssuedAttestation("some-jwt", null);
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(MOCK_CONFIG));
    when(tfService.isEnabled(FAMILY_ID)).thenReturn(true);
    when(clientRegistry.resolve("gxdch-loire")).thenReturn(mockClient);
    when(mockClient.check(any(), any())).thenReturn(expected);

    ComplianceCheckOutcome result = orchestrator.check(ASSET_ID, PROFILE_ID, ASSET_PAYLOAD);

    assertInstanceOf(IssuedAttestation.class, result);
  }

  @Test
  void check_clientThrowsTimeoutException_propagates() {
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(MOCK_CONFIG));
    when(tfService.isEnabled(FAMILY_ID)).thenReturn(true);
    when(clientRegistry.resolve("gxdch-loire")).thenReturn(mockClient);
    when(mockClient.check(any(), any())).thenThrow(new TimeoutException("timed out"));

    assertThrows(TimeoutException.class,
        () -> orchestrator.check(ASSET_ID, PROFILE_ID, ASSET_PAYLOAD));
  }

  @Test
  void check_clientThrowsServiceUnavailableException_propagates() {
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(MOCK_CONFIG));
    when(tfService.isEnabled(FAMILY_ID)).thenReturn(true);
    when(clientRegistry.resolve("gxdch-loire")).thenReturn(mockClient);
    when(mockClient.check(any(), any())).thenThrow(new ServiceUnavailableException("unreachable"));

    assertThrows(ServiceUnavailableException.class,
        () -> orchestrator.check(ASSET_ID, PROFILE_ID, ASSET_PAYLOAD));
  }

  @Test
  void check_clientReturnsNull_throwsServiceUnavailableException() {
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(MOCK_CONFIG));
    when(tfService.isEnabled(FAMILY_ID)).thenReturn(true);
    when(clientRegistry.resolve("gxdch-loire")).thenReturn(mockClient);
    when(mockClient.check(any(), any())).thenReturn(null);

    assertThrows(ServiceUnavailableException.class,
        () -> orchestrator.check(ASSET_ID, PROFILE_ID, ASSET_PAYLOAD));
  }

  @Test
  void check_vpIdMismatch_returnsUnverifiableAttestation() {
    ComplianceCheckOutcome outcome = orchestrator.check(ASSET_ID, PROFILE_ID, VP_JWT_MISMATCHED_ID);

    assertInstanceOf(UnverifiableAttestation.class, outcome);
  }

  @Test
  void check_vpIdMatches_delegatesToClient() {
    var expected = new IssuedAttestation("cred-jwt", null);
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(MOCK_CONFIG));
    when(tfService.isEnabled(FAMILY_ID)).thenReturn(true);
    when(clientRegistry.resolve("gxdch-loire")).thenReturn(mockClient);
    when(mockClient.check(any(), any())).thenReturn(expected);

    ComplianceCheckOutcome result = orchestrator.check(VP_JWT_ASSET_ID, PROFILE_ID, VP_JWT_MATCHING);

    assertInstanceOf(IssuedAttestation.class, result);
  }

  @Test
  void check_clientThrowsClientException_propagates() {
    var cause = new ClientException("business error from compliance service");
    when(registry.getProfileConfig(PROFILE_ID)).thenReturn(Optional.of(MOCK_CONFIG));
    when(tfService.isEnabled(FAMILY_ID)).thenReturn(true);
    when(clientRegistry.resolve("gxdch-loire")).thenReturn(mockClient);
    when(mockClient.check(any(), any())).thenThrow(cause);

    assertThrows(ClientException.class,
        () -> orchestrator.check(ASSET_ID, PROFILE_ID, ASSET_PAYLOAD));
  }
}
