package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;

/**
 * Tests the caller-parameter-driven {@code requireBaseClass} gate on {@link VerificationServiceImpl}.
 *
 * <p>The gate is invoked via explicit caller parameters in the overloads:
 * - {@code verifyCredential(payload)} → requireBaseClass=false (no gate)
 * - {@code verifyCredential(payload, requireBaseClass)} → gate fires iff requireBaseClass=true
 * - {@code verifyCredential(payload, ..., requireBaseClass)} → 5-arg gate fires iff requireBaseClass=true
 *
 * <p>No Spring context is started; uses pure Mockito.
 */
@ExtendWith(MockitoExtension.class)
class VerificationServiceRequireBaseClassTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @InjectMocks
  private VerificationServiceImpl verificationServiceImpl;

  @Mock
  private CredentialIngestionStrategy credentialStrategy;

  @Mock
  private NonCredentialIngestionStrategy nonCredentialStrategy;

  @Mock
  private CredentialFormatDetector formatDetector;

  @Mock
  private TrustFrameworkRegistry trustFrameworkRegistry;

  private ContentAccessor jwtPayload;

  private CredentialVerificationResult nullBaseClassResult;

  @BeforeEach
  void setUp() {
    jwtPayload = mock(ContentAccessor.class);
    when(jwtPayload.getContentAsString()).thenReturn("eyJhbGciOiJFUzI1NiJ9.payload.sig");

    nullBaseClassResult = new CredentialVerificationResult(
        NOW, "active", "did:web:example.com", NOW,
        "did:web:example.com", List.of(), List.of(), null, null);
  }

  @Test
  void verifyCredential_defaultOverload_unknownType_returnsResultWithoutThrowing() throws Exception {
    when(credentialStrategy.ingest(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(nullBaseClassResult);

    CredentialVerificationResult result = verificationServiceImpl.verifyCredential(jwtPayload);

    assertSame(nullBaseClassResult, result);
    assertNull(result.getBaseClass());
  }

  @Test
  void verifyCredential_requireBaseClassFalse_unknownType_returnsResultWithoutThrowing() throws Exception {
    when(credentialStrategy.ingest(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(nullBaseClassResult);

    CredentialVerificationResult result = verificationServiceImpl.verifyCredential(jwtPayload, false);

    assertSame(nullBaseClassResult, result);
    assertNull(result.getBaseClass());
  }

  @Test
  void verifyCredential_requireBaseClassTrue_unknownType_throwsClientException() {
    when(credentialStrategy.ingest(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(nullBaseClassResult);
    when(trustFrameworkRegistry.getActiveBundles()).thenReturn(List.of());

    ClientException thrown = assertThrowsExactly(ClientException.class,
        () -> verificationServiceImpl.verifyCredential(jwtPayload, true));

    assertTrue(thrown.getMessage().contains("Credential type is not resolvable"));
  }

  @Test
  void verifyCredential_5argRequireBaseClassTrue_unknownType_throwsClientException() {
    when(credentialStrategy.ingest(any(), anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(nullBaseClassResult);
    when(trustFrameworkRegistry.getActiveBundles()).thenReturn(List.of());

    assertThrowsExactly(ClientException.class,
        () -> verificationServiceImpl.verifyCredential(jwtPayload, true, false, false, true));
  }

}
