package eu.xfsc.fc.core.pojo;

import java.time.Instant;
import java.util.List;

/**
 * POJO Class for holding validation results.
 */
public class VerificationResultResource extends VerificationResult {

  /**
   * Constructor for the VerificationResultParticipant
   *
   * @param id id of SD
   * @param claims List of claims in the SD
   * @param validators Validators, signing parts of the SD
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus status according to GAIA-X lifecycle
   * @param issuer Issuer of the offering
   * @param issuedDate issuing date of the SD
   */
  public VerificationResultResource(Instant verificationTimestamp, String lifecycleStatus, String issuer, Instant issuedDateTime,
          String id, List<SdClaim> claims, List<Validator> validators) {
    super(verificationTimestamp, lifecycleStatus, issuer, issuedDateTime, id, claims, validators);
  }

  @Override
  public String toString() {
    List<SdClaim> claims = getClaims();
    String cls = claims == null ? "null" : "" + claims.size();
    List<Validator> validators = getValidators();
    String vls = validators == null ? "null" : "" + validators.size();
    return "VerificationResultResource [id=" + getId() + ", issuer=" + getIssuer() + ", validatorDids=" + getValidatorDids()
            + ", issuedDateTime=" + getIssuedDateTime() + ", claims=" + cls + ", validators=" + vls
            + ", verificationTimestamp=" + getVerificationTimestamp() + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
  
}

