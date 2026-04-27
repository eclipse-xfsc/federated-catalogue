package eu.xfsc.fc.core.pojo;

import java.time.Instant;
import java.util.List;

/**
 * POJO Class for holding validation results.
 */
public class CredentialVerificationResultOffering extends CredentialVerificationResult {

  /**
   * Constructor for the CredentialVerificationResultOffering
   *
   * @param id id of credential
   * @param claims List of claims in the credential
   * @param validators Validators, signing parts of the credential
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus status according to GAIA-X lifecycle
   * @param issuer Issuer of the offering
   * @param issuedDateTime issuing date of the credential
   */
  public CredentialVerificationResultOffering(Instant verificationTimestamp, String lifecycleStatus, String issuer, Instant issuedDateTime,
                                              String id, List<RdfClaim> claims, List<Validator> validators) {
    super(verificationTimestamp, lifecycleStatus, issuer, issuedDateTime, id, claims, validators);
  }

  @Override
  public String toString() {
    List<RdfClaim> claims = getClaims();
    String cls = claims == null ? "null" : "" + claims.size();
    List<Validator> validators = getValidators();
    String vls = validators == null ? "null" : "" + validators.size();
    return "CredentialVerificationResultOffering [id=" + getId() + ", issuer=" + getIssuer() + ", validatorDids=" + getValidatorDids()
            + ", issuedDateTime=" + getIssuedDateTime() + ", claims=" + cls + ", validators=" + vls
            + ", verificationTimestamp=" + getVerificationTimestamp() + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
  
}
