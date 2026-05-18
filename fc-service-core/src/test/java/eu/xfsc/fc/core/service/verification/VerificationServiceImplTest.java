package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.NonCredentialVerificationResult;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;

@ExtendWith(MockitoExtension.class)
class VerificationServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @InjectMocks
  private VerificationServiceImpl verificationServiceImpl;

  @Mock
  private CredentialVerificationStrategy credentialStrategy;

  @Mock
  private NonCredentialIngestionStrategy nonCredentialStrategy;

  @Mock
  private CredentialFormatDetector formatDetector;

  @Mock
  private TrustFrameworkRegistry trustFrameworkRegistry;

  @Test
  void verifyCredential_jwtPayload_strategyReturnsNullRole_throwsClientException() {
    ContentAccessor payload = mock(ContentAccessor.class);
    // JWT-prefixed body routes straight to credentialStrategy — no format-detector peek needed.
    when(payload.getContentAsString()).thenReturn("eyJhbGciOiJFUzI1NiJ9.payload.sig");
    CredentialVerificationResult nullRoleResult = new CredentialVerificationResult(
        NOW, "active", "did:web:example.com", NOW,
        "did:web:example.com", List.of(), List.of(), null, null);

    when(credentialStrategy.ingest(any(),
        anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(nullRoleResult);

    assertThrowsExactly(ClientException.class,
        () -> verificationServiceImpl.verifyCredential(payload));
  }

  @Test
  void verifyCredential_nonJwtUnknownFormat_delegatesToNonCredentialStrategy_returnsResult() {
    ContentAccessor payload = mock(ContentAccessor.class);
    when(payload.getContentAsString()).thenReturn("{\"@context\": \"https://www.w3.org/ns/credentials/v2\"}");
    when(formatDetector.detect(payload)).thenReturn(CredentialFormat.UNKNOWN);
    NonCredentialVerificationResult nonCredentialResult =
        new NonCredentialVerificationResult(NOW, "active", List.of());

    when(nonCredentialStrategy.ingest(any(),
        anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(nonCredentialResult);

    CredentialVerificationResult result = verificationServiceImpl.verifyCredential(payload);

    assertSame(nonCredentialResult, result);
  }

  @Test
  void verifyCredential_nonJwtKnownFormat_strategyReturnsNullRole_throwsClientException() {
    ContentAccessor payload = mock(ContentAccessor.class);
    when(payload.getContentAsString()).thenReturn("{\"@context\": \"https://www.w3.org/ns/credentials/v2\"}");
    when(formatDetector.detect(payload)).thenReturn(CredentialFormat.GAIAX_V2_LOIRE);
    CredentialVerificationResult nullRoleResult = new CredentialVerificationResult(
        NOW, "active", "did:web:example.com", NOW,
        "did:web:example.com", List.of(), List.of(), null, null);

    when(credentialStrategy.ingest(any(),
        anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(nullRoleResult);

    assertThrowsExactly(ClientException.class,
        () -> verificationServiceImpl.verifyCredential(payload));
  }

  @Test
  void verifyCredential_nonJwtKnownFormat_strategyReturnsResolvedRole_returnsResult() {
    ContentAccessor payload = mock(ContentAccessor.class);
    when(payload.getContentAsString()).thenReturn("{\"@context\": \"https://www.w3.org/ns/credentials/v2\"}");
    when(formatDetector.detect(payload)).thenReturn(CredentialFormat.GAIAX_V2_LOIRE);
    CredentialVerificationResult resolvedResult = new CredentialVerificationResult(
        NOW, "active", "did:web:example.com", NOW,
        "did:web:example.com", List.of(), List.of(), "SomeRole", "some-bundle");

    when(credentialStrategy.ingest(any(),
        anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(resolvedResult);

    CredentialVerificationResult result = verificationServiceImpl.verifyCredential(payload);

    assertSame(resolvedResult, result);
  }

}
