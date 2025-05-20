package eu.xfsc.fc.core.service.pubsub.ces;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.pubsub.BaseSDPublisher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CesSDPublisherImpl extends BaseSDPublisher {
	
    @Value("${publisher.url}")
    private String pubUrl;
    @Value("${publisher.comp-url:#{null}}")
    private String compUrl;

    private CesRestClient cesClient;
    private CompRestClient compClient;
	
    @Override
    public void initialize() {
		cesClient = new CesRestClient(this.jsonMapper, pubUrl);
		compClient = new CompRestClient(this.jsonMapper, compUrl);
    }

	@Override
	protected boolean publishInternal(SelfDescriptionMetadata sdMetadata, VerificationResult verificationResult) {
		log.debug("publishInternal. sdMetadata: {}", sdMetadata);
		String content = sdMetadata.getSelfDescription().getContentAsString();
		Map<String, Object> compEvent = compClient.postCredentials(content);
		log.debug("publishInternal. got comp event: {}", compEvent);
		compEvent.put("source", instance);
		String location = cesClient.postCredentials(compEvent);
		log.debug("publishInternal. got location: {}", location);
		return location != null;
	}

	@Override
    protected boolean supportsStatusUpdate() {
    	return false;
    }

}
