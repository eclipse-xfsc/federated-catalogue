package eu.xfsc.fc.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.xfsc.fc.core.service.pubsub.SDPublisher;
import eu.xfsc.fc.core.service.pubsub.SDSubscriber;
import eu.xfsc.fc.core.service.pubsub.ces.CesSDPublisherImpl;
import eu.xfsc.fc.core.service.pubsub.ces.CesSDSubscriberImpl;
import eu.xfsc.fc.core.service.pubsub.nats.NatsSDPublisherImpl;
import eu.xfsc.fc.core.service.pubsub.nats.NatsSDSubscriberImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class PubSubConfig {

    @Value("${publisher.impl}")
    private String pubImpl;
    @Value("${subscriber.impl}")
    private String subImpl;
    
    @Bean
    public SDPublisher getSDPublisher() {
    	SDPublisher pub = null;
    	switch (pubImpl) {
			//case "basf": 
			//	pub = new BasfSDPublisherImpl();
			//	break;
	    	case "ces":
	    		pub = new CesSDPublisherImpl();
	    		break;
			case "nats": 
				pub = new NatsSDPublisherImpl();
				break;
    	}
    	log.debug("getSDPublisher; returning {} for impl {}", pub, pubImpl);
    	return pub;
    }

    @Bean
    public SDSubscriber getSDSubscriber() {
    	SDSubscriber sub = null;
    	switch (subImpl) {
    		//case "basf": 
    		//	sub = new BasfSDSubscriberImpl();
    		//	break;
    	    case "ces":
    	    	sub = new CesSDSubscriberImpl();
    	    	break;
    		case "nats": 
    			sub = new NatsSDSubscriberImpl();
    			break;
    	}
    	log.debug("getSDSubscriber; returning {} for impl {}", sub, subImpl);
    	return sub;
    }
        
}
