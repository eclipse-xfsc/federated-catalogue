package eu.xfsc.fc.core.service.pubsub;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;

public interface AssetPublisher {

	enum AssetEvent {ADD, DELETE, UPDATE};

	boolean isTransactional();
	boolean publish(AssetMetadata assetMetadata, CredentialVerificationResult verificationResult);
	boolean publish(String hash, AssetEvent event, AssetStatus status);
	void setTransactional(boolean transactional);

}
