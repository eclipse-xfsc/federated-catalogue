package eu.xfsc.fc.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.sdstore.PublishingSelfDescriptionStoreImpl;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStoreImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class SelfDescriptionStoreConfig {

    @Value("${publisher.impl}")
    private String pubImpl;
    
    @Bean
    public SelfDescriptionStore sdStorePublisher() {
    	SelfDescriptionStore sdStore = null;
    	if ("none".equals(pubImpl)) {
    		sdStore = new SelfDescriptionStoreImpl();
    	} else {
    		sdStore = new PublishingSelfDescriptionStoreImpl();
    	}
    	log.debug("getSelfDescriptionStore; returning {} for impl {}", sdStore, pubImpl);
    	return sdStore;
    }
	
}
