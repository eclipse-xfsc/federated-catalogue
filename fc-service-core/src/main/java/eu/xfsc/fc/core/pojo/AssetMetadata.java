package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static eu.xfsc.fc.core.util.HashUtils.calculateSha256AsHex;


/**
 * Class for handling the metadata of an Asset, and optionally a
 * reference to a content accessor.
 */
@AllArgsConstructor
@NoArgsConstructor
public class AssetMetadata extends Asset {

  /**
   * A reference to the asset content.
   */
  @Getter
  @Setter
  @JsonIgnore
  private ContentAccessor contentAccessor;

  /** Optional change note for this version, set by the caller before storing. */
  @Getter
  @Setter
  @JsonIgnore
  private String changeComment;

  /** Raw JSON-LD content string, populated only for RDF assets returned by GET /assets/{id}. */
  @Getter
  @Setter
  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String rawContent;

  /**
   * Creates asset metadata from explicit fields; computes the SHA-256 hash from content.
   *
   * @param id              asset IRI
   * @param issuer          credential issuer DID
   * @param validators      validator list
   * @param contentAccessor raw credential content
   */
  public AssetMetadata(String id, String issuer, List<Validator> validators, ContentAccessor contentAccessor) {
    super(calculateSha256AsHex(contentAccessor.getContentAsString()), id, AssetStatus.ACTIVE, issuer,
        validators != null ? validators.stream().map(Validator::getDidURI).collect(Collectors.toList()) : null, Instant.now(), Instant.now(),
        null, null, null, null, null); // null: contentType, fileSize, warnings, humanReadableId, machineReadableId
    this.contentAccessor = contentAccessor;
  }

  /**
   * Creates asset metadata from a verification result.
   *
   * @param contentAccessor    raw credential content
   * @param verificationResult verification result supplying id, issuer, validators, and timestamps
   */
  public AssetMetadata(ContentAccessor contentAccessor, CredentialVerificationResult verificationResult) {
    super(calculateSha256AsHex(contentAccessor.getContentAsString()), verificationResult.getId(), AssetStatus.ACTIVE,
            verificationResult.getIssuer(), verificationResult.getValidatorDids(), verificationResult.getIssuedDateTime(),
            verificationResult.getVerificationTimestamp(), null, null, null, null, null); // null: contentType, fileSize, warnings, humanReadableId, machineReadableId
    this.contentAccessor = contentAccessor;
  }

  /**
   * Creates asset metadata from all explicit fields, bypassing hash computation.
   *
   * @param assetHash      pre-computed SHA-256 content hash
   * @param id             asset IRI
   * @param status         lifecycle status
   * @param issuer         credential issuer DID
   * @param validatorDids  validator DID list
   * @param uploadTime     upload timestamp
   * @param statusTime     last status-change timestamp
   * @param contentAccessor raw content, or null for binary assets
   */
  public AssetMetadata(String assetHash, String id, AssetStatus status, String issuer,
      List<String> validatorDids, Instant uploadTime, Instant statusTime,
      ContentAccessor contentAccessor) {
    super(assetHash, id, status, issuer, validatorDids, uploadTime, statusTime, null, null, null, null, null); // null: contentType, fileSize, warnings, humanReadableId, machineReadableId
    this.contentAccessor = contentAccessor;
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + Objects.hash(this.getAssetHash());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
        return true;
    }
    if (getClass() != obj.getClass()) {
        return false;
    }
    AssetMetadata other = (AssetMetadata) obj;
    return Objects.equals(this.getAssetHash(), other.getAssetHash());
  }
  
}
