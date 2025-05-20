package eu.xfsc.fc.core.service.resolve;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.Document;
import com.apicatalog.jsonld.document.JsonDocument;
import com.apicatalog.jsonld.loader.DocumentLoader;

import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.exception.VerificationException;
import foundation.identity.did.DIDDocument;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class HttpDocumentResolver {

    @Autowired
    private DocumentLoader documentLoader;
    @Autowired
    private DocumentLoaderConfig config;

	public String resolveDocumentContent(String uri) {
		log.debug("resolveDocumentContent.enter; got uri to resolve: {}", uri);
		URI docUri = URI.create(uri);
		Document doc;
		try {
			doc = documentLoader.loadDocument(docUri, config.getDocLoaderOptions());
		} catch (JsonLdError ex) {
			log.warn("resolveDocumentContent; error processing uri: {}", uri, ex);
			throw new VerificationException(ex);
		}
		String content = null;
		if (doc instanceof JsonDocument) {
			JsonDocument jsonDoc = JsonDocument.class.cast(doc);
			content = jsonDoc.getJsonContent().get().toString();
		}
		log.debug("resolveDocumentContent.exit; returning doc: {}", content);
		return content;
	}
	
	public DIDDocument resolveDidDocument(String did) {
		String docContent = resolveDocumentContent(did);
		return DIDDocument.fromJson(docContent);
	}
	
}
