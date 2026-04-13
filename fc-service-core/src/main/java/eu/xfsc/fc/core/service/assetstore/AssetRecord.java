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
  /** 1-based version ordinal from Envers revision order. Null when not in a version-history context. */
  @Setter
  private Integer version;
  /** Whether this is the current (latest) version. Null when not in a version-history context. */
  @Setter
  private Boolean isCurrent;

  /**
   * Creates a record from explicit metadata fields.
   *
   * @param id              asset IRI / subjectId
   * @param issuer          credential issuer DID
   * @param validators      validator list
   * @param contentAccessor raw content
   * @param expirationTime  earliest validator expiry, or null
   */
  public AssetRecord(String id, String issuer, List<Validator> validators,
      ContentAccessor contentAccessor, Instant expirationTime) {
    super(id, issuer, validators, contentAccessor);
    this.expirationTime = expirationTime;
  }

  /**
   * Creates a record from a verification result, deriving expiration from the earliest validator.
   *
   * @param contentAccessor     raw credential content
   * @param verificationResult  result of credential verification
   */
  public AssetRecord(ContentAccessor contentAccessor, CredentialVerificationResult verificationResult) {
    super(contentAccessor, verificationResult);
    final List<Validator> validators = verificationResult.getValidators();
    if (validators != null) {
      Validator minVal = validators.stream().min(new Validator.ExpirationComparator()).orElse(null);
      expirationTime = minVal == null ? null : minVal.getExpirationDate();
    }
  }

  /**
   * Creates a record from explicit fields.
   *
   * @param assetHash       SHA-256 content hash
   * @param id              asset IRI / subjectId
   * @param status          lifecycle status
   * @param issuer          credential issuer DID
   * @param validatorDids   validator DID list
   * @param uploadTime      upload timestamp
   * @param statusTime      last status-change timestamp
   * @param content         raw content accessor
   * @param expirationTime  earliest validator expiry, or null
   * @param contentType     MIME type
   * @param fileSize        file size in bytes, or null
   * @param originalFilename original filename for binary uploads, or null
   * @param changeComment   optional change note for this version
   */
  @Builder
  public AssetRecord(String assetHash, String id, AssetStatus status, String issuer, List<String> validatorDids,
      Instant uploadTime, Instant statusTime, ContentAccessor content, Instant expirationTime,
      String contentType, Long fileSize, String originalFilename, String changeComment) {
    super(assetHash, id, status, issuer, validatorDids, uploadTime, statusTime, content);
    this.expirationTime = expirationTime;
    setContentType(contentType);
    setFileSize(fileSize);
    this.originalFilename = originalFilename;
    setChangeComment(changeComment);
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
