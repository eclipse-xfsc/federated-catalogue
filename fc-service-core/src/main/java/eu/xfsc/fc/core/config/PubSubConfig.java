package eu.xfsc.fc.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.xfsc.fc.core.service.pubsub.AssetPublisher;
import eu.xfsc.fc.core.service.pubsub.AssetSubscriber;
import eu.xfsc.fc.core.service.pubsub.ces.CesAssetPublisherImpl;
import eu.xfsc.fc.core.service.pubsub.ces.CesAssetSubscriberImpl;
import eu.xfsc.fc.core.service.pubsub.nats.NatsAssetPublisherImpl;
import eu.xfsc.fc.core.service.pubsub.nats.NatsAssetSubscriberImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class PubSubConfig {

    @Value("${publisher.impl}")
    private String pubImpl;
    @Value("${subscriber.impl}")
    private String subImpl;
    
    @Bean
    public AssetPublisher getAssetPublisher() {
    	AssetPublisher pub = null;
    	switch (pubImpl) {
			//case "basf": 
			//	pub = new BasfAssetPublisherImpl();
			//	break;
	    	case "ces":
	    		pub = new CesAssetPublisherImpl();
	    		break;
			case "nats": 
				pub = new NatsAssetPublisherImpl();
				break;
    	}
    	log.debug("getAssetPublisher; returning {} for impl {}", pub, pubImpl);
    	return pub;
    }

    @Bean
    public AssetSubscriber getAssetSubscriber() {
    	AssetSubscriber sub = null;
    	switch (subImpl) {
    		//case "basf": 
    		//	sub = new BasfAssetSubscriberImpl();
    		//	break;
    	    case "ces":
    	    	sub = new CesAssetSubscriberImpl();
    	    	break;
    		case "nats": 
    			sub = new NatsAssetSubscriberImpl();
    			break;
    	}
    	log.debug("getAssetSubscriber; returning {} for impl {}", sub, subImpl);
    	return sub;
    }
        
}
