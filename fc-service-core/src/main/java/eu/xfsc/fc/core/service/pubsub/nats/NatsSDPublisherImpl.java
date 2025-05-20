package eu.xfsc.fc.core.service.pubsub.nats;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.pubsub.BaseSDPublisher;
import io.nats.client.Connection;
import io.nats.client.JetStreamApiException;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NatsSDPublisherImpl extends BaseSDPublisher {
	
    @Value("${publisher.subject}")
    private String subject;
    @Value("${publisher.send-content}")
    private boolean sendContent;
	
	@Autowired
	private Connection pubConnection;

	@Override
	protected boolean publishInternal(SelfDescriptionMetadata sdMetadata, VerificationResult verificationResult) {
		log.debug("publishInternal. sd: {}", sdMetadata);
		try {
			Headers headers = new Headers();
			headers.put("source", instance);
			headers.put("event", SDEvent.ADD.name());
			headers.put("status", sdMetadata.getStatus().name());
			byte[] body = null;
			if (sendContent) {
			    Map<String, Object> data = Map.of("content", sdMetadata.getSelfDescription().getContentAsString(), 
				    "verificationResult", jsonMapper.writeValueAsString(verificationResult));
			    body = jsonMapper.writeValueAsString(data).getBytes();
			}
			pubConnection.jetStream().publish(subject + "." + sdMetadata.getSdHash(), headers, body); 
			return true;
		} catch (IOException | JetStreamApiException ex) {
			log.error("publishInternal.error", ex);
		}
		return false;
	}

	@Override
	protected boolean publishInternal(String hash, SDEvent event, SelfDescriptionStatus status) {
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
