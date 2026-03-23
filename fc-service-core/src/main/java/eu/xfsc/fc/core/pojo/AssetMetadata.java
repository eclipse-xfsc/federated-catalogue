package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.annotation.JsonIgnore;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetStatus;

import static eu.xfsc.fc.core.util.HashUtils.calculateSha256AsHex;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Class for handling the metadata of an Asset, and optionally a
 * reference to a content accessor.
 */
@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
public class AssetMetadata extends Asset {

  /**
   * A reference to the asset content.
   */
  @lombok.Getter
  @lombok.Setter
  @JsonIgnore
  private ContentAccessor contentAccessor;

  public AssetMetadata(String id, String issuer, List<Validator> validators, ContentAccessor contentAccessor) {
    super(calculateSha256AsHex(contentAccessor.getContentAsString()), id, AssetStatus.ACTIVE, issuer,
        validators != null ? validators.stream().map(Validator::getDidURI).collect(Collectors.toList()) : null, Instant.now(), Instant.now(),
        null, null, null);
    this.contentAccessor = contentAccessor;
  }

  public AssetMetadata(ContentAccessor contentAccessor, CredentialVerificationResult verificationResult) {
    super(calculateSha256AsHex(contentAccessor.getContentAsString()), verificationResult.getId(), AssetStatus.ACTIVE,
            verificationResult.getIssuer(), verificationResult.getValidatorDids(), verificationResult.getIssuedDateTime(),
            verificationResult.getVerificationTimestamp(), null, null, null); // null: contentType, fileSize, warnings
    this.contentAccessor = contentAccessor;
  }

  public AssetMetadata(String assetHash, String id, AssetStatus status, String issuer, List<String> validatorDids, Instant uploadTime, Instant statusTime, ContentAccessor contentAccessor) {
	super(assetHash, id, status, issuer, validatorDids, uploadTime, statusTime, null, null, null);
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
