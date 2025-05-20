package eu.xfsc.fc.core.service.sdstore;

import org.springframework.beans.factory.annotation.Autowired;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.pubsub.SDPublisher;
import eu.xfsc.fc.core.service.pubsub.SDPublisher.SDEvent;

public class PublishingSelfDescriptionStoreImpl extends SelfDescriptionStoreImpl {

	  @Autowired
	  private SDPublisher sdPublisher;

	  @Override
	  public void storeSelfDescription(final SelfDescriptionMetadata sdMetadata, final VerificationResult verificationResult) {
		SubjectHashRecord subHash = super.storeSDInternal(sdMetadata, verificationResult);
		if (subHash != null && subHash.sdHash() != null ) {
	      sdPublisher.publish(subHash.sdHash(), SDEvent.UPDATE, SelfDescriptionStatus.DEPRECATED);
	    }
	    sdPublisher.publish(sdMetadata, verificationResult);
	  }
	  
	  @Override
	  public void changeLifeCycleStatus(final String hash, final SelfDescriptionStatus targetStatus) {
		super.changeLifeCycleStatus(hash, targetStatus);
	    sdPublisher.publish(hash, SDEvent.UPDATE, targetStatus);
	  }
		  
	  @Override
	  public void deleteSelfDescription(final String hash) {
        super.deleteSelfDescription(hash);
	    sdPublisher.publish(hash, SDEvent.DELETE, null);
	  }
	  
}
