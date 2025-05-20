package eu.xfsc.fc.core.service.resolve;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;

import eu.xfsc.fc.core.exception.VerificationException;
import foundation.identity.did.DIDDocument;
import lombok.extern.slf4j.Slf4j;
import uniresolver.ResolutionException;
import uniresolver.UniResolver;
import uniresolver.result.ResolveRepresentationResult;

@Slf4j
@Component
public class DidDocumentResolver {

	private static final Map<String, Object> RESOLVE_OPTIONS = Map.of("accept", "application/did+ld+json");
	
	@Autowired
	private UniResolver resolver;
	@Autowired
	private Cache<String, DIDDocument> didDocumentCache;

	public String resolveDocumentContent(String did) {
		DIDDocument diDoc = resolveDidDocument(did);
		return diDoc.toJson();
	}
	
	public DIDDocument resolveDidDocument(String did) {
		log.debug("resolveDidDocument.enter; got did to resolve: {}", did);
		DIDDocument diDoc = didDocumentCache.getIfPresent(did);
		boolean cached = true;
		if (diDoc == null) {
			cached = false;
			ResolveRepresentationResult didResult;
			try {
				didResult = resolver.resolveRepresentation(did, RESOLVE_OPTIONS);
				log.trace("resolveDid; resolved to: {}", didResult.toJson());
			} catch (ResolutionException ex) {
				log.warn("resolveDidDocument; error processing did {}", did, ex);
				throw new VerificationException(ex);
			}
			if (didResult.isErrorResult()) {
				throw new VerificationException(didResult.getErrorMessage());
			}

			String docStream = didResult.getDidDocumentStreamAsString();
			log.trace("resolveDidDocument; doc stream is: {}", docStream);
			diDoc = DIDDocument.fromJson(docStream);
			didDocumentCache.put(did, diDoc);
		}
		log.debug("resolveDidDocument.exit; returning doc: {}, from cache: {}", diDoc, cached);
		return diDoc;
	}
	
	
}
