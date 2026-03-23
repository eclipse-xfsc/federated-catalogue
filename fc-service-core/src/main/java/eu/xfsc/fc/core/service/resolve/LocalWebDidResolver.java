package eu.xfsc.fc.core.service.resolve;

import eu.xfsc.fc.core.util.DidUtils;
import foundation.identity.did.DIDDocument;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import uniresolver.ResolutionException;
import uniresolver.UniResolver;
import uniresolver.result.ResolveResult;

/**
 * Local did:web resolver that fetches DID documents directly over HTTPS without
 * delegating to an external Universal Resolver service.
 *
 * <p>Used when {@code federated-catalogue.verification.signature-verifier=local} so that
 * JWT signature verification can resolve internal hostnames (e.g. {@code did:web:did-server}
 * in a Docker Compose stack) that are not reachable from the public internet.
 */
@Slf4j
@RequiredArgsConstructor
public class LocalWebDidResolver implements UniResolver {

  private final HttpDocumentResolver httpDocumentResolver;

  @Override
  public ResolveResult resolve(String did, Map<String, Object> options) throws ResolutionException {
    log.debug("resolve.enter; did={}", did);
    URI docUri;
    try {
      docUri = DidUtils.resolveWebUri(URI.create(did));
    } catch (IOException ex) {
      throw new ResolutionException("resolver_error: Failed to convert did:web to URL: " + ex.getMessage(), ex);
    }
    if (docUri == null) {
      throw new ResolutionException("resolver_error: Only did:web is supported by LocalWebDidResolver, got: " + did);
    }

    DIDDocument didDocument;
    try {
      didDocument = httpDocumentResolver.resolveDidDocument(docUri.toString());
    } catch (Exception ex) {
      throw new ResolutionException("resolver_error: " + ex.getMessage(), ex);
    }

    log.debug("resolve.exit; did={}, doc={}", did, didDocument);
    return ResolveResult.build(Map.of(), didDocument, Map.of());
  }

  @Override
  public Map<String, Map<String, Object>> properties() throws ResolutionException {
    return Map.of();
  }

  @Override
  public Set<String> methods() throws ResolutionException {
    return Set.of("web");
  }

  @Override
  public Map<String, List<String>> testIdentifiers() throws ResolutionException {
    return Map.of();
  }

  @Override
  public Map<String, Map<String, Object>> traits() throws ResolutionException {
    return Map.of();
  }
}
