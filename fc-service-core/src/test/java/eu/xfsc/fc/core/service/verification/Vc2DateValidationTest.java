package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.danubetech.verifiablecredentials.VerifiableCredential;
import eu.xfsc.fc.core.exception.ClientException;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class Vc2DateValidationTest {

  @Test
  void validate_validValidFrom_returnsEmpty() {
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("@context", List.of("https://www.w3.org/ns/credentials/v2"),
            "validFrom", "2020-01-01T00:00:00Z"));

    String errors = Vc2DateValidation.validate(vc, 0);

    assertEquals("", errors);
  }

  @Test
  void validate_missingValidFrom_returnsError() {
    VerifiableCredential vc = VerifiableCredential.fromMap(Map.of());

    String errors = Vc2DateValidation.validate(vc, 0);

    assertTrue(errors.contains("validFrom"));
  }

  @Test
  void validate_validFromInFuture_returnsError() {
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("validFrom", "2099-01-01T00:00:00Z"));

    String errors = Vc2DateValidation.validate(vc, 0);

    assertTrue(errors.contains("validFrom"));
    assertTrue(errors.contains("past"));
  }

  @Test
  void validate_expiredValidUntil_returnsError() {
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("validFrom", "2020-01-01T00:00:00Z",
            "validUntil", "2021-01-01T00:00:00Z"));

    String errors = Vc2DateValidation.validate(vc, 0);

    assertTrue(errors.contains("validUntil"));
    assertTrue(errors.contains("future"));
  }

  @Test
  void validate_malformedValidFrom_throwsClientException() {
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("validFrom", "not-a-date"));

    assertThrows(ClientException.class, () -> Vc2DateValidation.validate(vc, 0));
  }
}
