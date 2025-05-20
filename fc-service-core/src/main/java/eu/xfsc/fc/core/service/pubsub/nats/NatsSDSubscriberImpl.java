package eu.xfsc.fc.core.service.pubsub.nats;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import eu.xfsc.fc.core.config.NatsConfig;
import eu.xfsc.fc.core.service.pubsub.BaseSDSubscriber;
import io.nats.client.Connection;
import io.nats.client.PushSubscribeOptions;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import io.nats.client.impl.Headers;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NatsSDSubscriberImpl extends BaseSDSubscriber {
	
	@Value("${subscriber.subject}")	
    private String subject;
	@Value("${subscriber.stream}")
	private String stream;
	@Value("${subscriber.queue}")
    private String queue;
	@Value("${subscriber.group}")
    private String group;
	
    @Autowired 
	private Connection subConnection;
	
	@Override
	protected void subscribe() throws Exception {
		log.debug("subscribe; connect: {}, store: {}", subConnection, sdStore);
	    //Choosing delivery policy is analogous to setting the current offset
	    //in a partition for a consumer or consumer group in Kafka.
	    DeliverPolicy deliverPolicy = DeliverPolicy.New;
	    PushSubscribeOptions subscribeOptions = ConsumerConfiguration.builder()
	            .durable(queue)
	            .deliverGroup(group)
	            .deliverPolicy(deliverPolicy)
	            .buildPushSubscribeOptions();
	    /*Subscription subscription =*/
	    NatsConfig.createOrReplaceStream(subConnection.jetStreamManagement(), stream, subject);
	    subConnection.jetStream().subscribe(
	            subject,
	            group,
	            subConnection.createDispatcher(),
	            natsMsg -> {
	                //This callback will be called for incoming messages
	                //asynchronously. Every subscription configured this
	                //way will be backed by its own thread, that will be
	                //used to call this callback.
	            	log.debug("onMessage; got message: {}", natsMsg);
	            	try {
	            		Headers headers = natsMsg.getHeaders();
	            		String source = headers.getFirst("source");
	            		if (!instance.equals(source)) {
		            		Map<String, Object> params = new HashMap<>();
		            		headers.entrySet().forEach(e -> {
		            			params.put(e.getKey(), e.getValue().get(0));
		            		});
		            		String payload = new String(natsMsg.getData());
		            		if (payload.length() > 10) {
		            			params.put("data", payload);
		            		}
		            		params.put("hash", natsMsg.getSubject().substring(6));
		            		this.onMessage(params);
	            		}
	            	} catch (Exception ex) {
	            		log.error("onMessage.error", ex);
	            	}
	            },
	            true,  //true if you want received messages to be acknowledged
	                   //automatically, otherwise you will have to call
	                   //natsMessage.ack() manually in the above callback function
	            subscribeOptions);
	}
}
