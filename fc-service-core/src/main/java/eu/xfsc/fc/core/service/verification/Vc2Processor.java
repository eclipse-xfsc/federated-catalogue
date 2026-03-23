package eu.xfsc.fc.core.service.verification;

import com.danubetech.verifiablecredentials.VerifiableCredential;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * VC 2.0 pre-processing strategy.
 *
 * <p>Delegates to {@link JwtContentPreprocessor#unwrap(ContentAccessor)} to convert
 * JWT-wrapped VC 2.0 / VP 2.0 payloads to JSON-LD before the main verification
 * pipeline processes them. Non-JWT payloads are returned unchanged by {@code unwrap()}.
 */
@Component
@RequiredArgsConstructor
public class Vc2Processor implements VersionedCredentialProcessor {

  private final JwtContentPreprocessor jwtPreprocessor;

  /**
   * Unwraps a JWT-wrapped VC 2.0 or VP 2.0 payload to JSON-LD.
   *
   * <p>Delegates to {@link JwtContentPreprocessor#unwrap(ContentAccessor)}.
   * If the payload is not JWT-wrapped, it is returned unchanged.
   *
   * @param payload the incoming credential content
   * @return JWT-unwrapped JSON-LD content, or the original content if not JWT-wrapped
   */
  @Override
  public ContentAccessor preProcess(ContentAccessor payload) {
    return jwtPreprocessor.unwrap(payload);
  }

  /**
   * Validates VC 2.0 date fields: {@code validFrom} (required, must be in the past)
   * and {@code validUntil} (optional, must be in the future if present).
   *
   * @param credential the credential to validate
   * @param idx the index of this credential within its presentation
   * @return error string; empty if valid
   * @throws ClientException if a date field value cannot be parsed as an ISO-8601 instant
   */
  @Override
  public String validateDates(VerifiableCredential credential, int idx) {
    StringBuilder sb = new StringBuilder();
    String sep = System.lineSeparator();
    Instant now = Instant.now();

    Object validFromObj = credential.getJsonObject().get("validFrom");
    if (validFromObj == null) {
      sb.append(" - VerifiableCredential[").append(idx)
          .append("] must contain 'validFrom' property").append(sep);
    } else {
      try {
        if (Instant.parse(validFromObj.toString()).isAfter(now)) {
          sb.append(" - 'validFrom' of VerifiableCredential[").append(idx)
              .append("] must be in the past").append(sep);
        }
      } catch (DateTimeParseException ex) {
        throw new ClientException("Invalid 'validFrom' date format: " + validFromObj);
      }
    }

    Object validUntilObj = credential.getJsonObject().get("validUntil");
    if (validUntilObj != null) {
      try {
        if (Instant.parse(validUntilObj.toString()).isBefore(now)) {
          sb.append(" - 'validUntil' of VerifiableCredential[").append(idx)
              .append("] must be in the future").append(sep);
        }
      } catch (DateTimeParseException ex) {
        throw new ClientException("Invalid 'validUntil' date format: " + validUntilObj);
      }
    }
    return sb.toString();
  }
}
