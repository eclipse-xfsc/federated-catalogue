package eu.xfsc.fc.core.service.pubsub.nats;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.pubsub.BaseAssetPublisher;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NatsAssetPublisherImpl extends BaseAssetPublisher {
	
    @Value("${publisher.subject}")
    private String subject;
    @Value("${publisher.send-content}")
    private boolean sendContent;
	
	@Autowired
	private Connection pubConnection;

	@Override
	protected boolean publishInternal(AssetMetadata assetMetadata, CredentialVerificationResult verificationResult) {
		log.debug("publishInternal. asset: {}", assetMetadata);
		try {
			Headers headers = new Headers();
			headers.put("source", instance);
			headers.put("event", AssetEvent.ADD.name());
			headers.put("status", assetMetadata.getStatus().name());
			byte[] body = null;
			if (sendContent) {
			    Map<String, Object> data = Map.of("content", assetMetadata.getContentAccessor().getContentAsString(), 
				    "verificationResult", jsonMapper.writeValueAsString(verificationResult));
			    body = jsonMapper.writeValueAsString(data).getBytes();
			}
			pubConnection.jetStream().publish(subject + "." + assetMetadata.getAssetHash(), headers, body); 
			return true;
		} catch (IOException | JetStreamApiException ex) {
			log.error("publishInternal.error", ex);
		}
		return false;
	}

	@Override
	protected boolean publishInternal(String hash, AssetEvent event, AssetStatus status) {
		log.debug("publishInternal. hash: {}, event: {}, status: {}", hash, event, status);
		try {
			Headers headers = new Headers();
			headers.put("source", instance);
			headers.put("event", event.name());
			if (status != null) {
				headers.put("status", status.name());
			}
			pubConnection.jetStream().publish(subject + "." + hash, headers, null);
			return true;
		} catch (IOException | JetStreamApiException ex) {
			log.error("publishInternal.error", ex);
		}
		return false;
	}

}
