package eu.xfsc.fc.core.service.assetstore;

import java.time.Instant;
import java.util.List;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Database record for asset metadata table.
 */
@Getter
public class AssetRecord extends AssetMetadata {

  private Instant expirationTime;
  @Setter
  private String originalFilename;

  public AssetRecord(String id, String issuer, List<Validator> validators, ContentAccessor contentAccessor, Instant expirationTime) {
    super(id, issuer, validators, contentAccessor);
    this.expirationTime = expirationTime;
  }

  public AssetRecord(ContentAccessor contentAccessor, CredentialVerificationResult verificationResult) {
    super(contentAccessor, verificationResult);
    final List<Validator> validators = verificationResult.getValidators();
    if (validators != null) {
      Validator minVal = validators.stream().min(new Validator.ExpirationComparator()).orElse(null);
      expirationTime = minVal == null ? null : minVal.getExpirationDate();
    }
  }

  @Builder
  public AssetRecord(String assetHash, String id, AssetStatus status, String issuer, List<String> validatorDids,
      Instant uploadTime, Instant statusTime, ContentAccessor content, Instant expirationTime,
      String contentType, Long fileSize, String originalFilename) {
	super(assetHash, id, status, issuer, validatorDids, uploadTime, statusTime, content);
	this.expirationTime = expirationTime;
	setContentType(contentType);
	setFileSize(fileSize);
	this.originalFilename = originalFilename;
  }

  public String getContent() {
    final ContentAccessor contentAccessor = super.getContentAccessor();
    if (contentAccessor == null) {
      return null;
    }
    return contentAccessor.getContentAsString();
  }

    public String[] getValidators() {
    final List<String> validatorDids = getValidatorDids();
    if (validatorDids == null) {
      return null;
    }
    return validatorDids.toArray(String[]::new);
  }
}
