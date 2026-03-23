package eu.xfsc.fc.core.service.verification;

import com.danubetech.verifiablecredentials.VerifiableCredential;
import eu.xfsc.fc.core.pojo.ContentAccessor;

/**
 * Strategy interface for version-specific processing of Verifiable Credential payloads.
 *
 * <p>Implementations encapsulate two concerns that differ between VC specification versions:
 * <ul>
 *   <li><b>Pre-processing</b> ({@link #preProcess}): format normalisation before JSON-LD parse
 *       (e.g. JWT unwrapping for VC 2.0).</li>
 *   <li><b>Date validation</b> ({@link #validateDates}): semantic validation of version-specific
 *       date fields ({@code issuanceDate}/{@code expirationDate} for VC 1.1;
 *       {@code validFrom}/{@code validUntil} for VC 2.0).</li>
 * </ul>
 *
 * <p>The strategy is selected in {@link CredentialVerificationStrategy} after version detection
 * from the credential's {@code @context} array.
 *
 * <p>{@link #preProcess} implementations must be idempotent.
 */
public interface VersionedCredentialProcessor {

  /**
   * Pre-processes the given payload according to the rules of a specific VC version.
   *
   * @param payload the incoming credential content; may be JSON-LD or JWT-wrapped
   * @return the pre-processed content (e.g. JWT-unwrapped); may be the original instance
   *     unchanged if no processing is required
   */
  ContentAccessor preProcess(ContentAccessor payload);

  /**
   * Validates the version-specific date fields of a parsed Verifiable Credential.
   *
   * @param credential the credential to validate
   * @param idx the index of this credential within its presentation (0 for standalone VCs)
   * @return an error string (potentially multi-line) describing any violations;
   *     empty string if all date fields are valid
   */
  String validateDates(VerifiableCredential credential, int idx);
}
