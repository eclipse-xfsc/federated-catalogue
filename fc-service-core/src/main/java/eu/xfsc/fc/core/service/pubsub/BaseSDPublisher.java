package eu.xfsc.fc.core.service.pubsub;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import jakarta.annotation.PostConstruct;

public abstract class BaseSDPublisher implements SDPublisher {

    @Value("${publisher.instance}")
    protected String instance;
    @Value("${publisher.pool-size:4}")
    protected int poolSize;
    @Value("${publisher.transactional:false}")
    protected boolean transactional;
	
    @Autowired 
	protected ObjectMapper jsonMapper;
	
    private ExecutorService threadPool;
    
    @PostConstruct
    public void init() throws Exception {
   		threadPool = Executors.newFixedThreadPool(poolSize); // .newVirtualThreadPerTaskExecutor();
    	initialize();
    }

    @Override
	public boolean isTransactional() {
    	return transactional;
    }
    
	@Override
	public boolean publish(SelfDescriptionMetadata sd, VerificationResult verificationResult) {
		if (supportsMetadataUpdate()) {
			if (transactional) {
				return publishInternal(sd, verificationResult);
			} else {
				threadPool.execute(() -> {
					// set thread name?
					publishInternal(sd, verificationResult);
				});
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean publish(String hash, SDEvent event, SelfDescriptionStatus status) {
		if (supportsStatusUpdate()) {
			if (transactional) {
				return publishInternal(hash, event, status);
			} else {
				threadPool.execute(() -> {
					// set thread name?
					publishInternal(hash, event, status);
				});
				return true;
			}
		}
		return false;
	}

	@Override
	public void setTransactional(boolean transactional) {
		this.transactional = transactional;
	}

    protected void initialize() throws Exception {
    	// any initialization steps here..
    }

	protected boolean publishInternal(SelfDescriptionMetadata sd, VerificationResult verificationResult) {
		return false;
	}

	protected boolean publishInternal(String hash, SDEvent event, SelfDescriptionStatus status) {
		return false;
	}
	
    protected boolean supportsMetadataUpdate() {
    	return true;
    }

    protected boolean supportsStatusUpdate() {
    	return true;
    }
    
}
