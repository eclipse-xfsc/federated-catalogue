package eu.xfsc.fc.core.service.verification;

import com.danubetech.verifiablecredentials.VerifiableCredential;
import eu.xfsc.fc.core.exception.ClientException;

import java.time.Instant;
import java.time.format.DateTimeParseException;

/**
 * Validates VC 2.0 date fields: {@code validFrom} (required, must be in the past) and
 * {@code validUntil} (optional, must be in the future if present).
 *
 * <p>Stateless utility — there is only one credential-data-model version this catalogue
 * accepts (VC 2.0), so version-dispatch was collapsed into a static call.
 */
final class Vc2DateValidation {

  private Vc2DateValidation() {
    // utility class
  }

  /**
   * @param credential the credential to validate
   * @param idx        the index of this credential within its presentation (0 for standalone VCs)
   * @return an error string (potentially multi-line) describing any violations;
   * empty string if all date fields are valid
   * @throws ClientException if a date field value cannot be parsed as an ISO-8601 instant
   */
  static String validate(VerifiableCredential credential, int idx) {
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
