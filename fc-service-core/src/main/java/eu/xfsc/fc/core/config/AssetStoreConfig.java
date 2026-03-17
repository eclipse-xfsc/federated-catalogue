package eu.xfsc.fc.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.assetstore.PublishingAssetStore;
import eu.xfsc.fc.core.service.assetstore.AssetStoreImpl;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AssetStoreConfig {

    @Value("${publisher.impl}")
    private String pubImpl;

    @Bean
    public AssetStore assetStorePublisher() {
    	AssetStore assetStore = null;
    	if ("none".equals(pubImpl)) {
    		assetStore = new AssetStoreImpl();
    	} else {
    		assetStore = new PublishingAssetStore();
    	}
    	log.debug("getAssetStore; returning {} for impl {}", assetStore, pubImpl);
    	return assetStore;
    }
	
}
