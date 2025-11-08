package eu.xfsc.fc.core.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import eu.xfsc.fc.core.exception.DidException;
import eu.xfsc.fc.core.service.verification.signature.LocalSignatureVerifier;
import eu.xfsc.fc.core.service.verification.signature.SignatureVerifier;
import eu.xfsc.fc.core.service.verification.signature.UniSignatureVerifier;
import foundation.identity.did.DIDDocument;
import lombok.extern.slf4j.Slf4j;
import uniresolver.UniResolver;
import uniresolver.client.ClientUniResolver;

@Slf4j
@Configuration
public class DidResolverConfig {

    @Value("${federated-catalogue.verification.signature-verifier}")
    private String signatureVerifier;

	@Bean
	public UniResolver uniResolver(@Value("${federated-catalogue.verification.did.base-url}") String baseUrl) {
		log.info("uniResolver.enter; configured base URI: {}", baseUrl);
		UniResolver resolver;
		URI uri;
		try {
			uri = new URI(baseUrl);
		} catch (URISyntaxException ex) {
			log.error("uniResolver.error", ex);
			throw new DidException(ex);
		}
		resolver = ClientUniResolver.create(uri);
		log.info("uniResolver.exit; returning resolver: {}", resolver);
		return resolver;
	}
	
    @Bean
    public SignatureVerifier getSignatureVerifier() {
    	SignatureVerifier sv = null;
    	switch (signatureVerifier) {
    		case "local": 
    			sv = new LocalSignatureVerifier();
    			break;
    		case "uni-res": 
    	    	sv = new UniSignatureVerifier();
    			break;
    	}
    	log.debug("getSignatureVerifier; returning {} for impl {}", sv, signatureVerifier);
    	return sv;
    }
	
	@Bean
	public Cache<String, DIDDocument> didDocumentCache(@Value("${federated-catalogue.verification.did.cache.size}") int cacheSize,
			@Value("${federated-catalogue.verification.did.cache.timeout}") Duration timeout) {
		log.info("didDocumentCache.enter; cache size: {}, ttl: {}", cacheSize, timeout);
        Caffeine<?, ?> cache = Caffeine.newBuilder().expireAfterAccess(timeout); 
        if (cacheSize > 0) {
            cache = cache.maximumSize(cacheSize);
        } 
		log.info("didDocumentCache.exit; returning: {}", cache);
        return (Cache<String, DIDDocument>) cache.build();
	}
	    
}
