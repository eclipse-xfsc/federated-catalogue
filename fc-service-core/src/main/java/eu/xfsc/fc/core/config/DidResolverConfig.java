package eu.xfsc.fc.core.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import eu.xfsc.fc.core.exception.DidException;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.resolve.LocalWebDidResolver;
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

  /**
   * Creates the UniResolver used by JwtSignatureVerifier for DID document lookup.
   *
   * <p>When {@code signature-verifier=local}, uses {@link LocalWebDidResolver} which fetches
   * {@code did:web} documents directly over HTTPS — required in Docker Compose environments
   * where internal hostnames (e.g. {@code did:web:did-server}) are not reachable from the
   * public Universal Resolver at {@code did.base-url}.
   *
   * <p>{@code HttpDocumentResolver} is injected via {@link ObjectProvider} (not direct injection)
   * so that test contexts that configure {@code signature-verifier=uni-res} and omit
   * {@link HttpDocumentResolver} from their bean set can still load without error — the provider
   * is only resolved ({@code getObject()}) on the {@code local} branch.
   */
  @Bean
  public UniResolver uniResolver(
      @Value("${federated-catalogue.verification.did.base-url}") String baseUrl,
      ObjectProvider<HttpDocumentResolver> httpDocumentResolverProvider) {
    log.info("uniResolver.enter; signature-verifier={}, base-url={}", signatureVerifier, baseUrl);
    UniResolver resolver;
    if ("local".equals(signatureVerifier)) {
      resolver = new LocalWebDidResolver(httpDocumentResolverProvider.getObject());
    } else {
      URI uri;
      try {
        uri = new URI(baseUrl);
      } catch (URISyntaxException ex) {
        log.error("uniResolver.error", ex);
        throw new DidException(ex);
      }
      resolver = ClientUniResolver.create(uri);
    }
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
