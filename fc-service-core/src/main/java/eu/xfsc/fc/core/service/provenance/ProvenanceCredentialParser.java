package eu.xfsc.fc.core.service.provenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.verification.VerificationService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Parses and validates raw W3C VC payloads (JSON-LD and JWT forms) for provenance processing.
 */
@Component
@RequiredArgsConstructor
public class ProvenanceCredentialParser {

  private static final String CREDENTIAL_SUBJECT_KEY = "credentialSubject";
  private static final String VC_ID_KEY = "id";
  private static final String JSONLD_ID_KEY = "@id";
  private static final String CONTEXT_KEY = "@context";
  private static final String ACCEPTED_PREDICATES =
      "prov:wasGeneratedBy, prov:wasDerivedFrom, prov:wasAttributedTo, prov:wasRevisionOf, "
          + "prov:generated, prov:used, prov:wasAssociatedWith, prov:actedOnBehalfOf";

  /**
   * Recognizes both compact ({@code prov:X}) and expanded ({@code http://www.w3.org/ns/prov#X})
   * forms of every supported PROV-O predicate. Insertion order is preserved to make the parsed
   * fact list deterministic.
   */
  private static final Map<String, ProvenanceType> PREDICATE_MAP = buildPredicateMap();

  private static Map<String, ProvenanceType> buildPredicateMap() {
    Map<String, ProvenanceType> map = new LinkedHashMap<>();
    register(map, "wasGeneratedBy", ProvenanceType.CREATION);
    register(map, "wasDerivedFrom", ProvenanceType.DERIVATION);
    register(map, "wasAttributedTo", ProvenanceType.ATTRIBUTION);
    register(map, "wasRevisionOf", ProvenanceType.MODIFICATION);
    register(map, "generated", ProvenanceType.GENERATION);
    register(map, "used", ProvenanceType.USAGE);
    register(map, "wasAssociatedWith", ProvenanceType.ASSOCIATION);
    register(map, "actedOnBehalfOf", ProvenanceType.DELEGATION);
    return Map.copyOf(map);
  }

  private static void register(Map<String, ProvenanceType> map, String localName, ProvenanceType type) {
    map.put("prov:" + localName, type);
    map.put(ProvOConstants.NAMESPACE + localName, type);
  }

  private final VerificationService verificationService;
  private final ObjectMapper objectMapper;

  /**
   * Verifies the raw VC via the verification service.
   *
   * @throws ClientException if the credential fails verification
   */
  public CredentialVerificationResult parseAndValidateVc(String rawVc, String format) {
    try {
      ContentAccessorDirect content = new ContentAccessorDirect(rawVc, format);
      // Provenance credentials carry PROV-O predicates instead of trust-framework roles
      // (Participant/ServiceOffering/Resource), so role resolution must be bypassed.
      return verificationService.verifyCredential(content, false);
    } catch (VerificationException ex) {
      throw new ClientException("Invalid provenance credential: " + ex.getMessage(), ex);
    }
  }

  /**
   * Parses the raw VC once and extracts all metadata needed for provenance storage.
   *
   * @throws ClientException if the JSON is invalid, credentialSubject is absent, or no supported
   *     PROV-O predicate is found
   */
  public ProvenanceCredentialInfo extractCredentialInfo(String rawVc) {
    String stripped = rawVc.strip();
    JsonNode root = parseJson(stripped);
    String formatLabel = root.path(CONTEXT_KEY).isMissingNode() ? "JSONLD_JWT" : "JSONLD";
    return new ProvenanceCredentialInfo(
        extractCredentialId(root),
        extractProvenance(root),
        formatLabel);
  }

  /**
   * Extracts {@code credentialSubject.id} from a raw VC payload without re-running the predicate
   * validation. Returns {@code null} when the payload is unparseable or the field is absent;
   * callers use this to recover the graph subject IRI of a previously-stored credential so the
   * full set of projected triples can be cleaned up regardless of whether the credential was
   * entity- or activity-centric.
   */
  public String extractCredentialSubjectId(String rawVc) {
    try {
      JsonNode subject = objectMapper.readTree(rawVc.strip()).path(CREDENTIAL_SUBJECT_KEY);
      return subject.path(VC_ID_KEY).asText(null);
    } catch (JsonProcessingException ex) {
      return null;
    }
  }

  private String extractCredentialId(JsonNode root) {
    JsonNode idNode = root.path(VC_ID_KEY);
    return idNode.isTextual() ? idNode.asText() : null;
  }

  private List<ProvenanceInfo> extractProvenance(JsonNode root) {
    JsonNode subject = root.path(CREDENTIAL_SUBJECT_KEY);
    if (subject.isMissingNode() || subject.isNull()) {
      throw new ClientException(
          "Provenance credential must contain a 'credentialSubject' with a supported PROV-O predicate. "
              + "Accepted predicates: " + ACCEPTED_PREDICATES);
    }
    List<ProvenanceInfo> facts = new ArrayList<>();
    for (Map.Entry<String, JsonNode> field : subject.properties()) {
      ProvenanceType type = PREDICATE_MAP.get(field.getKey());
      if (type == null) {
        continue;
      }
      String objectValue = extractIriReference(field.getValue());
      if (objectValue == null) {
        throw new ClientException(
            "PROV-O predicate '" + field.getKey() + "' must reference an IRI — either as a "
                + "string or as an object with '@id' or 'id'.");
      }
      facts.add(new ProvenanceInfo(type, objectValue));
    }
    if (facts.isEmpty()) {
      throw new ClientException(
          "credentialSubject must contain one of the supported PROV-O predicates: "
              + ACCEPTED_PREDICATES);
    }
    return List.copyOf(facts);
  }

  /**
   * Returns the IRI a PROV-O predicate points at. PROV-O object properties relate resources to
   * resources (W3C PROV-DM), so the JSON-LD payload may carry either the compacted string IRI
   * (when the context declares the predicate as {@code "@type": "@id"}) or an object node with
   * {@code @id}/{@code id}. Datatyped/literal values return {@code null}.
   */
  private static String extractIriReference(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isTextual()) {
      String text = value.asText();
      return text.isEmpty() ? null : text;
    }
    if (value.isObject()) {
      JsonNode id = value.has(JSONLD_ID_KEY) ? value.get(JSONLD_ID_KEY) : value.get(VC_ID_KEY);
      return (id != null && id.isTextual()) ? id.asText() : null;
    }
    return null;
  }

  private JsonNode parseJson(String rawVc) {
    try {
      return objectMapper.readTree(rawVc);
    } catch (JsonProcessingException ex) {
      throw new ClientException("Invalid JSON in provenance credential: " + ex.getMessage(), ex);
    }
  }
}
