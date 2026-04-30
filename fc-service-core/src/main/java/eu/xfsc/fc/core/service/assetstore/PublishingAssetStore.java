package eu.xfsc.fc.core.service.assetstore;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.provenance.ProvenanceService;
import eu.xfsc.fc.core.service.pubsub.AssetPublisher;
import eu.xfsc.fc.core.service.pubsub.AssetPublisher.AssetEvent;

public class PublishingAssetStore extends AssetStoreImpl {

  private final AssetPublisher assetPublisher;

  public PublishingAssetStore(AssetDao dao, GraphStore graphDb, FileStore fileStore,
      IriGenerator iriGenerator, AssetRepository assetRepository,
      ProtectedNamespaceProperties namespaceProperties,
      ProvenanceService provenanceService,
      AssetPublisher assetPublisher) {
    super(dao, graphDb, fileStore, iriGenerator, assetRepository, namespaceProperties,
        provenanceService);
    this.assetPublisher = assetPublisher;
  }

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
