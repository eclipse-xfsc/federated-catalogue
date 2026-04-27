package eu.xfsc.fc.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
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
    	AssetPublisher pub = switch (pubImpl) {
            //case "basf":
            //	pub = new BasfAssetPublisherImpl();
            //	break;
            case "ces" -> new CesAssetPublisherImpl();
            case "nats" -> new NatsAssetPublisherImpl();
            default -> new AssetPublisher() {
                @Override
                public boolean isTransactional() {
                    return false;
                }

                @Override
                public void setTransactional(boolean t) {
                }

                @Override
                public boolean publish(AssetMetadata m, CredentialVerificationResult r) {
                    return true;
                }

                @Override
                public boolean publish(String hash, AssetEvent event, AssetStatus status) {
                    return true;
                }
            };
        };
    	log.debug("getAssetPublisher; returning {} for impl {}", pub, pubImpl);
    	return pub;
    }

    @Bean
    public AssetSubscriber getAssetSubscriber() {
    	AssetSubscriber sub = switch (subImpl) {
            //case "basf":
            //	sub = new BasfAssetSubscriberImpl();
            //	break;
            case "ces" -> new CesAssetSubscriberImpl();
            case "nats" -> new NatsAssetSubscriberImpl();
            default -> null;
        };
        log.debug("getAssetSubscriber; returning {} for impl {}", sub, subImpl);
    	return sub;
    }
        
}
