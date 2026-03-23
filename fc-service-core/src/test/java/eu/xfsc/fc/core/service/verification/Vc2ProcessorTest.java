package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.danubetech.verifiablecredentials.VerifiableCredential;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Vc2ProcessorTest {

  @Test
  void preProcess_jwtWrappedPayload_delegatesToJwtPreprocessor() {
    JwtContentPreprocessor mockPreprocessor = mock(JwtContentPreprocessor.class);
    Vc2Processor processor = new Vc2Processor(mockPreprocessor);
    ContentAccessor jwtPayload = new ContentAccessorDirect("eyJhbGciOiJFUzI1NiJ9.payload.sig");
    ContentAccessor unwrapped = new ContentAccessorDirect(
        "{\"@context\": [\"https://www.w3.org/ns/credentials/v2\"]}");
    when(mockPreprocessor.unwrap(jwtPayload)).thenReturn(unwrapped);

    ContentAccessor result = processor.preProcess(jwtPayload);

    assertSame(unwrapped, result);
    verify(mockPreprocessor).unwrap(jwtPayload);
  }

  /**
   * Locks in the JwtContentPreprocessor.unwrap() no-op contract for non-JWT input.
   * Uses the real JwtContentPreprocessor — if unwrap() ever stops returning the original
   * for non-JWT content, this test will catch the regression.
   */
  @Test
  void preProcess_nonJwtVc2Payload_passthroughFromRealUnwrap() {
    JwtContentPreprocessor realPreprocessor = new JwtContentPreprocessor();
    Vc2Processor processor = new Vc2Processor(realPreprocessor);
    String json = "{\"@context\": [\"https://www.w3.org/ns/credentials/v2\"]}";
    ContentAccessor nonJwtPayload = new ContentAccessorDirect(json);

    ContentAccessor result = processor.preProcess(nonJwtPayload);

    assertSame(nonJwtPayload, result);
  }

  @Test
  void validateDates_validValidFrom_returnsEmpty() {
    Vc2Processor processor = new Vc2Processor(new JwtContentPreprocessor());
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("@context", List.of("https://www.w3.org/ns/credentials/v2"),
            "validFrom", "2020-01-01T00:00:00Z"));

    String errors = processor.validateDates(vc, 0);

    assertEquals("", errors);
  }

  @Test
  void validateDates_missingValidFrom_returnsError() {
    Vc2Processor processor = new Vc2Processor(new JwtContentPreprocessor());
    VerifiableCredential vc = VerifiableCredential.fromMap(Map.of());

    String errors = processor.validateDates(vc, 0);

    assertTrue(errors.contains("validFrom"));
  }

  @Test
  void validateDates_validFromInFuture_returnsError() {
    Vc2Processor processor = new Vc2Processor(new JwtContentPreprocessor());
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("validFrom", "2099-01-01T00:00:00Z"));

    String errors = processor.validateDates(vc, 0);

    assertTrue(errors.contains("validFrom"));
    assertTrue(errors.contains("past"));
  }

  @Test
  void validateDates_expiredValidUntil_returnsError() {
    Vc2Processor processor = new Vc2Processor(new JwtContentPreprocessor());
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("validFrom", "2020-01-01T00:00:00Z",
            "validUntil", "2021-01-01T00:00:00Z"));

    String errors = processor.validateDates(vc, 0);

    assertTrue(errors.contains("validUntil"));
    assertTrue(errors.contains("future"));
  }

  @Test
  void validateDates_malformedValidFrom_throwsClientException() {
    Vc2Processor processor = new Vc2Processor(new JwtContentPreprocessor());
    VerifiableCredential vc = VerifiableCredential.fromMap(
        Map.of("validFrom", "not-a-date"));

    assertThrows(ClientException.class, () -> processor.validateDates(vc, 0));
  }
}
