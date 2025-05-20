package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.Instant;
import java.util.List;

/**
 * POJO Class for holding validation results.
 */
@lombok.Getter
@lombok.Setter
public class VerificationResultParticipant extends VerificationResult {

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
   * @param id id of SD
   * @param participantPublicKey public key of participant
   * @param verificationTimestamp time stamp of verification
   * @param lifecycleStatus status according to GAIA-X lifecycle
   * @param issuedDateTime issuing date of the SD
   * @param validators Validators, signing parts of the SD
   * @param claims List of claims in the SD
   */
  public VerificationResultParticipant(Instant verificationTimestamp, String lifecycleStatus, String id, Instant issuedDateTime,
          List<SdClaim> claims, List<Validator> validators, String participantName, String participantPublicKey) {
    super(verificationTimestamp, lifecycleStatus, id, issuedDateTime, id, claims, validators);
    this.participantName = participantName;
    this.participantPublicKey = participantPublicKey;
  }
  
  @Override
  public String toString() {
    List<SdClaim> claims = getClaims();
    String cls = claims == null ? "null" : "" + claims.size();
    List<Validator> validators = getValidators();
    String vls = validators == null ? "null" : "" + validators.size();
    return "VerificationResultParticipant [id=" + getId() + ", participantName=" + participantName
            + ", issuer=" + getIssuer()  + ", validatorDids=" + getValidatorDids() + ", issuedDateTime=" + getIssuedDateTime()
            + ", participantPublicKey=" + participantPublicKey + ", claims=" + cls + ", validators=" + vls
            + ", verificationTimestamp=" + getVerificationTimestamp() + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }
  
}


