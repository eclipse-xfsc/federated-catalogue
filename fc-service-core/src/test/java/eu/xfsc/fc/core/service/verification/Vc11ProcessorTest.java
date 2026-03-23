package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.danubetech.verifiablecredentials.VerifiableCredential;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Vc11ProcessorTest {

  private final Vc11Processor processor = new Vc11Processor();

  @Test
  void preProcess_anyPayload_returnsSameInstance() {
    ContentAccessor payload = new ContentAccessorDirect(
        "{\"@context\": [\"https://www.w3.org/2018/credentials/v1\"]}");

    ContentAccessor result = processor.preProcess(payload);

    assertSame(payload, result);
  }

  @Test
  void preProcess_vc11Payload_contentUnchanged() {
    String json = "{\"@context\": [\"https://www.w3.org/2018/credentials/v1\"],"
        + "\"issuanceDate\": \"2024-01-01T00:00:00Z\"}";
    ContentAccessor payload = new ContentAccessorDirect(json);

    ContentAccessor result = processor.preProcess(payload);

    assertEquals(json, result.getContentAsString());
  }

  @Test
  void validateDates_validIssuanceDate_returnsEmpty() {
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("issuanceDate", "2020-01-01T00:00:00Z"));

    String errors = processor.validateDates(vc, 0);

    assertEquals("", errors);
  }

  @Test
  void validateDates_missingIssuanceDate_returnsError() {
    VerifiableCredential vc = VerifiableCredential.fromMap(Map.of());

    String errors = processor.validateDates(vc, 0);

    assertTrue(errors.contains("issuanceDate"));
  }

  @Test
  void validateDates_issuanceDateInFuture_returnsError() {
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("issuanceDate", "2099-01-01T00:00:00Z"));

    String errors = processor.validateDates(vc, 0);

    assertTrue(errors.contains("issuanceDate"));
    assertTrue(errors.contains("past"));
  }

  @Test
  void validateDates_expiredExpirationDate_returnsError() {
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("issuanceDate", "2020-01-01T00:00:00Z",
            "expirationDate", "2021-01-01T00:00:00Z"));

    String errors = processor.validateDates(vc, 0);

    assertTrue(errors.contains("expirationDate"));
    assertTrue(errors.contains("future"));
  }
}
