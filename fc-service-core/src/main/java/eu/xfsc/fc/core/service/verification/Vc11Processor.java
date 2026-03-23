package eu.xfsc.fc.core.service.verification;

import com.danubetech.verifiablecredentials.VerifiableCredential;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import java.time.Instant;
import java.util.Date;
import org.springframework.stereotype.Component;

/**
 * VC 1.1 pre-processing strategy.
 *
 * <p>Currently a structural placeholder: returns the payload unchanged.
 * Intended to receive VC 1.1-specific date validation and normalisation logic
 * (e.g. {@code issuanceDate} / {@code expirationDate} handling) when the shared
 * verification pipeline is separated from the VC 2.0 path.
 */
@Component
public class Vc11Processor implements VersionedCredentialProcessor {

  /**
   * Returns the payload unchanged; VC 1.1 requires no pre-parse normalisation.
   *
   * @param payload the incoming credential content
   * @return the original payload, unmodified
   */
  @Override
  public ContentAccessor preProcess(ContentAccessor payload) {
    return payload;
  }

  /**
   * Validates VC 1.1 date fields: {@code issuanceDate} (required, must be in the past)
   * and {@code expirationDate} (optional, must be in the future if present).
   *
   * @param credential the credential to validate
   * @param idx the index of this credential within its presentation
   * @return error string; empty if valid
   */
  @Override
  public String validateDates(VerifiableCredential credential, int idx) {
    StringBuilder sb = new StringBuilder();
    String sep = System.lineSeparator();
    Date today = Date.from(Instant.now());

    Date issDate = credential.getIssuanceDate();
    if (issDate == null) {
      sb.append(" - VerifiableCredential[").append(idx)
          .append("] must contain 'issuanceDate' property").append(sep);
    } else if (issDate.after(today)) {
      sb.append(" - 'issuanceDate' of VerifiableCredential[").append(idx)
          .append("] must be in the past").append(sep);
    }

    Date expDate = credential.getExpirationDate();
    if (expDate != null && expDate.before(today)) {
      sb.append(" - 'expirationDate' of VerifiableCredential[").append(idx)
          .append("] must be in the future").append(sep);
    }
    return sb.toString();
  }
}
