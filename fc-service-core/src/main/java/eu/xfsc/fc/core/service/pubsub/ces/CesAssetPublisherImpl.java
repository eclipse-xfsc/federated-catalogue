package eu.xfsc.fc.core.service.pubsub.ces;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;

import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.pubsub.BaseAssetPublisher;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CesAssetPublisherImpl extends BaseAssetPublisher {
	
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
	protected boolean publishInternal(AssetMetadata assetMetadata, CredentialVerificationResult verificationResult) {
		log.debug("publishInternal. assetMetadata: {}", assetMetadata);
		String content = assetMetadata.getContentAccessor().getContentAsString();
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
