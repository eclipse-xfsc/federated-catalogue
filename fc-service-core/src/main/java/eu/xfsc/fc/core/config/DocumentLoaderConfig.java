package eu.xfsc.fc.core.config;

import java.io.StringReader;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.http.ProfileConstants;
import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.DocumentLoaderOptions;
import com.danubetech.verifiablecredentials.jsonld.VerifiableCredentialContexts;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import eu.xfsc.fc.core.exception.VerificationException;
import foundation.identity.jsonld.ConfigurableDocumentLoader;
import lombok.extern.slf4j.Slf4j;

/**
 * Spring configuration class to create and inject a {@link DocumentLoader} into
 * {@link Autowired} fields.
 * 
 * <p>
 * Note: Please use <code> @Qualifier("additionalContextDocumentLoader")</code> to 
 * inject a document loader with additional context(s) by the application config item
 * <code>federated-catalogue.verification.additional-contexts</code>.
 * <p>
 * 
 */
@Slf4j
@Configuration
public class DocumentLoaderConfig {

	@Autowired
	private DocumentLoaderProperties properties;
	private DocumentLoaderOptions options;
	
	public DocumentLoaderConfig() {
		// not sure, what it is for..
		options = new DocumentLoaderOptions();
		options.setProfile(ProfileConstants.CONTEXT);
		options.setRequestProfile(Arrays.asList(options.getProfile()));
	}
	
	/**
	 * Returns a default document loader with additional context and settings
	 * 
	 * @see VerifiableCredentialContexts#DOCUMENT_LOADER
	 * <p>
	 * <b>TODO:</b> all w3c context files are part of the libraries in use, may be we should add the 
	 *      contexts we configure in the application into the catalog and load them by a classloader
	 *      instead of streaming them all the time. Right now we should stay with this solution, since
	 *      GAIA-X is enforcing fast changes during the last months...
	 * </p>     
	 * @return a document loader instance
	 */
	@Bean
	public DocumentLoader documentLoader(Cache<URI, Document> docLoaderCache) {
		log.info("documentLoader.enter; cache: {}", docLoaderCache);
		ConfigurableDocumentLoader loader = (ConfigurableDocumentLoader) VerifiableCredentialContexts.DOCUMENT_LOADER;
		loader.setEnableFile(properties.isEnableFile()); 
		loader.setEnableHttp(properties.isEnableHttp()); 
		loader.setEnableHttps(properties.isEnableHttp());
		loader.setEnableLocalCache(properties.isEnableLocalCache()); 
		if (!properties.isEnableLocalCache()) { 
		    loader.setRemoteCache(docLoaderCache);
		}

		if (!properties.getAdditionalContext().isEmpty()) {
			for (Map.Entry<String, String> ctxUrl: properties.getAdditionalContext().entrySet()) { 
				loadContext(loader, ctxUrl.getKey(), ctxUrl.getValue());
			}
		}
		log.info("documentLoader.exit; returning: {}", loader);
		return loader;
	}
	
	public DocumentLoaderOptions getDocLoaderOptions() {
		return options;
	}
	
	private void loadContext(ConfigurableDocumentLoader loader, String url, String contentRef) {
		try {
			Document document;
			final URI uri = new URI(url);
			final URI ref = getUri(contentRef);
			if (ref == null) {
				document = JsonDocument.of(new StringReader(contentRef));
			} else {
				document = loader.loadDocument(ref, options); 
			}
			if (document instanceof JsonDocument) {
				final JsonDocument jsonDocument = JsonDocument.class.cast(document);
				if (properties.isEnableLocalCache()) {
				    loader.getLocalCache().put(uri, jsonDocument);
				} else {
				    loader.getRemoteCache().asMap().putIfAbsent(uri, jsonDocument);
				}
				log.debug("Add additional context {} of size {}", url, jsonDocument.getJsonContent().toString().length());
			} else {
				log.error("Load an unknown document type '{}' for context '{}', expected was '{}'",	document.getClass(), url, JsonDocument.class.getSimpleName());
				throw new VerificationException("Unknown document format while load additional (GAIA-X) context '"	+ url + "'");
			}
		} catch (Exception ex) {
			log.error("Unexpected error while try to load additional context for {}: {}", url, contentRef);
			throw new ExceptionInInitializerError("Unexpected error while load additional context '" + url + "': " + ex.getMessage());
		}
	}
	
	private URI getUri(String ref) {
		try {
			return URI.create(ref);
		} catch (Exception ex) {
			return null;
		}
	}
	
	@Bean
	public Cache<URI, Document> docLoaderCache() {
        Caffeine<?, ?> cache = Caffeine.newBuilder().expireAfterAccess(properties.getCacheTimeout());
        if (properties.getCacheSize() > 0) {
            cache = cache.maximumSize(properties.getCacheSize());
        }
		log.info("docLoaderCache.exit; returning: {}", cache);
        return (Cache<URI, Document>) cache.build();
	}
	
}
