package eu.xfsc.fc.core.pojo;

import java.time.Instant;
import java.util.List;

/**
 * Verification result for non-credential raw RDF content (e.g. Turtle, N-Triples, RDF/XML).
 *
 * <p>Unlike {@link CredentialVerificationResult}, non-credential RDF has no issuer, issuance date,
 * or credential ID. Those fields are always {@code null} for this subtype.</p>
 */
public class NonCredentialVerificationResult extends CredentialVerificationResult {

  /**
   * Constructor for a non-credential RDF verification result.
   *
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus       status according to GAIA-X lifecycle
   * @param claims                extracted RDF claims (may be empty, not null)
   */
  public NonCredentialVerificationResult(Instant verificationTimestamp, String lifecycleStatus, List<RdfClaim> claims) {
    super(verificationTimestamp, lifecycleStatus, null, null, null, claims, null);
  }

  @Override
  public String toString() {
    List<RdfClaim> claims = getClaims();
    String cls = claims == null ? "null" : "" + claims.size();
    return "NonCredentialVerificationResult [claims=" + cls
        + ", verificationTimestamp=" + getVerificationTimestamp()
        + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
}
