package eu.xfsc.fc.core.service.verification;

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
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;

@ExtendWith(MockitoExtension.class)
class VerificationServiceImplTest {

  private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

  @InjectMocks
  private VerificationServiceImpl verificationServiceImpl;

  @Mock
  private VerificationStrategy credentialStrategy;

  @Mock
  private SchemaValidationService schemaValidationService;

  @Mock
  private TrustFrameworkRegistry trustFrameworkRegistry;

  @Test
  void verifyCredential_strategyReturnsUnresolvedRole_throwsClientException() {
    ContentAccessor payload = mock(ContentAccessor.class);
    CredentialVerificationResult unknownRoleResult = new CredentialVerificationResult(
        NOW, "active", "did:web:example.com", NOW,
        "did:web:example.com", List.of(), List.of(), null, null);

    when(credentialStrategy.verifyCredential(any(),
        anyBoolean(), anyBoolean(), anyBoolean(), anyBoolean()))
        .thenReturn(unknownRoleResult);

    assertThrowsExactly(ClientException.class,
        () -> verificationServiceImpl.verifyCredential(payload));
  }

}
