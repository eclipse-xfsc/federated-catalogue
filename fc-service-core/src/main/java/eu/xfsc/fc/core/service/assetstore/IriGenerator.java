package eu.xfsc.fc.core.service.assetstore;

import java.io.IOException;
import java.io.StringReader;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.apicatalog.jsonld.JsonLd;
import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates or extracts IRIs for assets.
 *
 * <p>For non-RDF assets, a UUID URN is generated per RFC 4122.
 * For RDF assets, the IRI is extracted from the content
 * ({@code credentialSubject.id}, {@code @id}, OWL ontology IRI,
 * SHACL shape IRI, or SKOS concept scheme IRI).
 * If no IRI can be extracted, a UUID URN is generated as fallback.</p>
 *
 * <p><b>Note:</b> DIDs are <b>extracted</b> from RDF content, not generated for assets.
 * The {@link #generateDid} method is a utility for demonstration purposes,
 * verifying that DID construction and validation work correctly.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IriGenerator {

  private static final String URN_UUID_PREFIX = "urn:uuid:";

  private final IriValidator iriValidator;
  private final ObjectMapper objectMapper;

  /**
   * Resolve the IRI for an asset. For RDF content, attempts extraction first.
   * Falls back to UUID URN generation.
   *
   * @param content the asset content (may be null for non-RDF)
   * @param isRdf   whether the content was detected as RDF
   * @return a valid IRI for the asset
   */
  public String resolveIri(ContentAccessor content, boolean isRdf) {
    if (isRdf && content != null) {
      String extracted = extractIdFromRdf(content);
      if (extracted != null && iriValidator.isValid(extracted)) {
        return extracted;
      }
    }
    return generateUuidUrn();
  }

  /**
   * Generate a UUID URN per RFC 4122.
   *
   * @return a URN in the format {@code urn:uuid:{uuid-v4}}
   */
  public String generateUuidUrn() {
    return URN_UUID_PREFIX + UUID.randomUUID().toString();
  }

  /**
   * Generate a DID for a given method and identifier.
   * Utility method for demonstration and testing — verifies DID construction
   * and validation.
   *
   * <p><b>Note:</b> Not used in the asset upload flow; DIDs are extracted from RDF content.</p>
   *
   * @param method     the DID method (e.g. "web", "key")
   * @param identifier the method-specific identifier
   * @return a DID string
   * @throws ClientException if the resulting DID is not a valid format
   */
  public String generateDid(String method, String identifier) {
    String did = "did:" + method + ":" + identifier;
    if (!iriValidator.isValid(did)) {
      throw new ClientException("Generated DID is not a valid format: " + did);
    }
    return did;
  }

  /**
   * Attempt to extract an IRI from RDF content.
   *
   * <p>Extraction priority:
   * <ol>
   *   <li>{@code credentialSubject.id} — Verifiable Credentials</li>
   *   <li>{@code @id} — generic JSON-LD</li>
   * </ol>
   *
   * <p><b>Note:</b> For OWL ontologies, SHACL shapes, and SKOS concept schemes,
   * the {@code @id} extraction covers them as they use standard JSON-LD.</p>
   *
   * @param content the RDF content accessor
   * @return the extracted IRI, or null if none found
   */
  String extractIdFromRdf(ContentAccessor content) {
    try {
      String text = content.getContentAsString();
      if (text == null || text.isBlank()) {
        return null;
      }

      JsonNode json = objectMapper.readTree(text);

      // Verifiable Credential / Presentation: credentialSubject.id
      if (json.has("credentialSubject")) {
        JsonNode subject = json.get("credentialSubject");
        if (subject.isArray() && subject.size() > 0) {
          subject = subject.get(0);
        }
        if (subject != null && subject.has("id")) {
          String id = subject.get("id").asText();
          if (!id.isBlank()) {
            return id;
          }
        }
      }

      // Generic JSON-LD: expand the document so @context prefixes are resolved before
      // reading @id — this is standard JSON-LD processing and handles both compact IRIs
      // (e.g. "ex:item1") and already-absolute IRIs uniformly.
      // Falls back to the raw @id value when expansion fails (e.g. remote context unreachable).
      if (json.has("@id")) {
        String rawId = json.get("@id").asText();
        if (!rawId.isBlank()) {
          return expandTopLevelId(text, rawId);
        }
      }
      return null;
    } catch (IOException e) {
      log.warn("extractIdFromRdf; could not parse RDF content as JSON: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Expands the JSON-LD document and returns the top-level {@code @id} value.
   * Falls back to {@code rawId} when expansion fails (e.g. remote context unreachable).
   */
  private String expandTopLevelId(String jsonLdText, String rawId) {
    try {
      JsonDocument document = JsonDocument.of(new StringReader(jsonLdText));
      JsonArray expanded = JsonLd.expand(document).get();
      if (!expanded.isEmpty()) {
        JsonObject first = expanded.getJsonObject(0);
        if (first.containsKey("@id")) {
          String id = first.getString("@id");
          if (!id.isBlank()) {
            return id;
          }
        }
      }
    } catch (JsonLdError e) {
      log.warn("expandTopLevelId; JSON-LD expansion failed, using raw @id '{}': {}", rawId, e.getMessage());
    }
    return rawId;
  }
}
