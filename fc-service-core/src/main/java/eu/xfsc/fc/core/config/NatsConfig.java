package eu.xfsc.fc.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.nats.client.Connection;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamManagement;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.api.RetentionPolicy;
import io.nats.client.api.StorageType;
import io.nats.client.api.StreamConfiguration;
import io.nats.client.api.StreamInfo;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class NatsConfig {

    @Value("${publisher.url}")
    private String pubUrl;
    @Value("${subscriber.url}")
    private String subUrl;
    //@Value("${pub-sub.user}")
    //private String user;
    //@Value("${pub-sub.password}")
    //private String password;
    
	@Bean("pubConnection")
	@ConditionalOnProperty(value = "publisher.impl", havingValue = "nats") 
	public Connection pubConnection() throws Exception {
		Connection nats = Nats.connect(
	        new Options.Builder()
                .server(pubUrl)
                .connectionListener((connection, eventType) -> {
                	log.debug("onConnectionEvent; event: {}", eventType);
                })
                .errorListener(new ErrorListener(){})
                .build());
		log.info("pubConnection; connecton established at {}; {}", pubUrl, nats);
		return nats;
	}

	@Bean("subConnection")
	@ConditionalOnProperty(value = "subscriber.impl", havingValue = "nats") 
	public Connection subConnection() throws Exception {
		Connection nats = Nats.connect(
	        new Options.Builder()
                .server(subUrl)
                .connectionListener((connection, eventType) -> {})
                .errorListener(new ErrorListener(){})
                .build());
		log.info("subConnection; connecton established at {}; {}", subUrl, nats);
		return nats;
	}
	
    public static void createOrReplaceStream(JetStreamManagement jsm, String stream, String subject) {
        // in case the stream was here before, we want a completely new one
        try { jsm.deleteStream(stream); } catch (Exception ignore) {}

        try {
        	StreamConfiguration sc = StreamConfiguration.builder()
        	    .name(stream)
        	    .subjects(subject)
        	    //.retentionPolicy(RetentionPolicy.WorkQueue)
        	    .build();
            StreamInfo si = jsm.addStream(sc);
            log.info("stream; created: {}", si.getConfiguration().getName());
        } catch (Exception ex) {
        	log.error("error creating stream", ex);
            System.exit(-1);
        }
    }	
}
