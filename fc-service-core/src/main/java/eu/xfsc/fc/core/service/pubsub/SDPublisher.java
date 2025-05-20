package eu.xfsc.fc.core.service.pubsub;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;

public interface SDPublisher {
	
	enum SDEvent {ADD, DELETE, UPDATE};
	
	boolean isTransactional();
	boolean publish(SelfDescriptionMetadata sdMetadata, VerificationResult verificationResult);
	boolean publish(String hash, SDEvent event, SelfDescriptionStatus status);
	void setTransactional(boolean transactional);

}
