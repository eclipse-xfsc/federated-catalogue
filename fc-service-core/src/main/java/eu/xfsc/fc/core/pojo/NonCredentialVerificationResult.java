package eu.xfsc.fc.core.pojo;

import java.time.Instant;
import java.util.List;

/**
 * Verification result for non-credential raw RDF content (e.g. Turtle, N-Triples, RDF/XML).
 *
 * <p>Unlike {@link CredentialVerificationResult}, non-credential RDF has no issuer, issuance date,
 * credential ID, role, or framework profile. Those fields are intentionally {@code null} for this
 * subtype. The {@code @NotNull} constraints declared on those fields in the parent API model apply
 * only to credential-based results and are not enforced for this subtype.</p>
 */
public class NonCredentialVerificationResult extends CredentialVerificationResult {

  /**
   * Constructor for a non-credential RDF verification result.
   *
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus       status according to GAIA-X lifecycle
   * @param graphClaims           extracted RDF claims (may be empty, not null)
   */
  public NonCredentialVerificationResult(Instant verificationTimestamp, String lifecycleStatus,
                                         List<RdfClaim> graphClaims) {
    super(verificationTimestamp, lifecycleStatus, null, null, null, graphClaims, null, null, null);
  }

  @Override
  public String toString() {
    List<RdfClaim> graphClaims = getGraphClaims();
    int claimCount = graphClaims == null ? 0 : graphClaims.size();
    return "NonCredentialVerificationResult [graphClaims=" + claimCount
        + ", verificationTimestamp=" + getVerificationTimestamp()
        + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
}
