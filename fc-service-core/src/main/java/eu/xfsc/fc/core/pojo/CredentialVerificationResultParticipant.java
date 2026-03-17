package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;

/**
 * POJO Class for holding validation results.
 */
@lombok.Getter
@lombok.Setter
public class CredentialVerificationResultParticipant extends CredentialVerificationResult {

  /**
   * The Name of the Participant.
   */
  @JsonIgnore
  private String participantName;
  /**
   * The public key of the participant.
   */
  @JsonIgnore
  private String participantPublicKey;
  /**
   * Constructor for the VerificationResultParticipant
   *
   * @param participantName Name of participant
   * @param id id of credential
   * @param participantPublicKey public key of participant
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus status according to GAIA-X lifecycle
   * @param issuedDateTime issuing date of the credential
   * @param validators Validators, signing parts of the credential
   * @param claims List of claims in the credential
   */
  public CredentialVerificationResultParticipant(Instant verificationTimestamp, String lifecycleStatus, String id, Instant issuedDateTime,
          List<CredentialClaim> claims, List<Validator> validators, String participantName, String participantPublicKey) {
    super(verificationTimestamp, lifecycleStatus, id, issuedDateTime, id, claims, validators);
    this.participantName = participantName;
    this.participantPublicKey = participantPublicKey;
  }
  
  @Override
  public String toString() {
    List<CredentialClaim> claims = getClaims();
    String cls = claims == null ? "null" : "" + claims.size();
    List<Validator> validators = getValidators();
    String vls = validators == null ? "null" : "" + validators.size();
    return "CredentialVerificationResultParticipant [id=" + getId() + ", participantName=" + participantName
            + ", issuer=" + getIssuer()  + ", validatorDids=" + getValidatorDids() + ", issuedDateTime=" + getIssuedDateTime()
            + ", participantPublicKey=" + participantPublicKey + ", claims=" + cls + ", validators=" + vls
            + ", verificationTimestamp=" + getVerificationTimestamp() + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
  
}


