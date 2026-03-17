package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

public class CredentialVerificationResult extends eu.xfsc.fc.api.generated.model.VerificationResult {

  private static final long serialVersionUID = 1L;
  
  /**
   * credentialSubject (id) of this credential.
   */
  @JsonIgnore
  private String id;
  /**
   * claims of the credential, to be inserted into the Graph-DB.
   */
  @JsonIgnore
  private List<CredentialClaim> claims;
  /**
   * validators, that signed parts of the credential.
   */
  @JsonIgnore
  private List<Validator> validators;

  public CredentialVerificationResult(Instant verificationTimestamp, String lifecycleStatus, String issuer, Instant issuedDateTime,
          String id, List<CredentialClaim> claims, List<Validator> validators) {
    super(verificationTimestamp, lifecycleStatus, issuer, issuedDateTime, null, null);
    this.id = id;
    this.claims = claims;
    this.setValidators(validators);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public List<CredentialClaim> getClaims() {
    return claims;
  }

  public void setClaims(List<CredentialClaim> claims) {
    this.claims = claims;
  }

  public List<Validator> getValidators() {
    return validators;
  }

  public void setValidators(List<Validator> validators) {
    //TODO: Check what parts of the validators should be added to the response
    if (validators == null) {
      super.setValidatorDids(null);  
    } else {
      super.setValidatorDids(validators.stream().map(Validator::getDidURI).collect(Collectors.toList()));
    }
    this.validators = validators;
  }

  @Override
  public String toString() {
    List<CredentialClaim> claims = getClaims();
    String cls = claims == null ? "null" : "" + claims.size();
    List<Validator> validators = getValidators();
    String vls = validators == null ? "null" : "" + validators.size();
    return "CredentialVerificationResult [id=" + getId() + ", issuer=" + getIssuer() + ", validatorDids=" + getValidatorDids()
            + ", issuedDateTime=" + getIssuedDateTime() + ", claims=" + cls + ", validators=" + vls
            + ", verificationTimestamp=" + getVerificationTimestamp() + ", lifecycleStatus=" + getLifecycleStatus() + "]";
  }

}
