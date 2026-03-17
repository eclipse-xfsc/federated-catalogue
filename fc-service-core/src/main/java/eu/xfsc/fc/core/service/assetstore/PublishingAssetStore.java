package eu.xfsc.fc.core.service.assetstore;

import org.springframework.beans.factory.annotation.Autowired;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.pubsub.AssetPublisher;
import eu.xfsc.fc.core.service.pubsub.AssetPublisher.AssetEvent;

public class PublishingAssetStore extends AssetStoreImpl {

	  @Autowired
	  private AssetPublisher assetPublisher;

	  @Override
	  public void storeCredential(final AssetMetadata assetMetadata, final CredentialVerificationResult verificationResult) {
		SubjectHashRecord subHash = super.storeCredentialInternal(assetMetadata, verificationResult);
		if (subHash != null && subHash.assetHash() != null ) {
	      assetPublisher.publish(subHash.assetHash(), AssetEvent.UPDATE, AssetStatus.DEPRECATED);
	    }
	    assetPublisher.publish(assetMetadata, verificationResult);
	  }
	  
	  @Override
	  public void changeLifeCycleStatus(final String hash, final AssetStatus targetStatus) {
		super.changeLifeCycleStatus(hash, targetStatus);
	    assetPublisher.publish(hash, AssetEvent.UPDATE, targetStatus);
	  }
		  
	  @Override
	  public void deleteAsset(final String hash) {
        super.deleteAsset(hash);
	    assetPublisher.publish(hash, AssetEvent.DELETE, null);
	  }
	  
}
