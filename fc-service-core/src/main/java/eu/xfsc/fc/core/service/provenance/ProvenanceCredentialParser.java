package eu.xfsc.fc.core.service.provenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.provenance.ProvenanceInfo.ObjectKind;
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
          + "prov:generated, prov:used, prov:wasAssociatedWith, prov:actedOnBehalfOf, "
          + "prov:wasInformedBy, prov:startedAtTime, prov:endedAtTime, dcs:action, rdf:type";

  private static final Map<String, String> KNOWN_PREFIXES = Map.of(
      "prov", ProvOConstants.NAMESPACE,
      "dcs", ProvOConstants.DCS_NAMESPACE,
      "rdf", ProvOConstants.RDF_NAMESPACE);

  /**
   * Recognises compact ({@code prov:X}) and expanded ({@code http://www.w3.org/ns/prov#X}) forms of
   * every supported predicate. Insertion order is preserved to make the parsed fact list
   * deterministic.
   */
  private static final Map<String, PredicateMeta> PREDICATE_MAP = buildPredicateMap();

  private static Map<String, PredicateMeta> buildPredicateMap() {
    Map<String, PredicateMeta> map = new LinkedHashMap<>();
    registerProv(map, "wasGeneratedBy", ProvenanceType.CREATION, ObjectKind.IRI, null);
    registerProv(map, "wasDerivedFrom", ProvenanceType.DERIVATION, ObjectKind.IRI, null);
    registerProv(map, "wasAttributedTo", ProvenanceType.ATTRIBUTION, ObjectKind.IRI, null);
    registerProv(map, "wasRevisionOf", ProvenanceType.MODIFICATION, ObjectKind.IRI, null);
    registerProv(map, "generated", ProvenanceType.GENERATION, ObjectKind.IRI, null);
    registerProv(map, "used", ProvenanceType.USAGE, ObjectKind.IRI, null);
    registerProv(map, "wasAssociatedWith", ProvenanceType.ASSOCIATION, ObjectKind.IRI, null);
    registerProv(map, "actedOnBehalfOf", ProvenanceType.DELEGATION, ObjectKind.IRI, null);
    registerProv(map, "wasInformedBy", ProvenanceType.INFORMATION, ObjectKind.IRI, null);
    registerProv(map, "startedAtTime", ProvenanceType.STARTED_AT_TIME,
        ObjectKind.LITERAL, ProvOConstants.XSD_DATETIME);
    registerProv(map, "endedAtTime", ProvenanceType.ENDED_AT_TIME,
        ObjectKind.LITERAL, ProvOConstants.XSD_DATETIME);
    register(map, "dcs:action", ProvOConstants.DCS_ACTION,
        ProvenanceType.ACTION, ObjectKind.LITERAL, null);
    // rdf:type is the activity's class declaration. JSON-LD aliases "type" and "@type" to rdf:type
    // when the @context provides the standard credentials-v2 mapping, so all three keys must be
    // recognised. The expanded form is also accepted for callers that produce already-canonical JSON.
    PredicateMeta typeMeta = new PredicateMeta(ProvenanceType.TYPE, ObjectKind.IRI, null);
    map.put("type", typeMeta);
    map.put("@type", typeMeta);
    map.put("rdf:type", typeMeta);
    map.put(ProvOConstants.RDF_TYPE, typeMeta);
    return Map.copyOf(map);
  }

  private static void registerProv(Map<String, PredicateMeta> map, String localName,
                                   ProvenanceType type, ObjectKind kind, String datatype) {
    register(map, "prov:" + localName, ProvOConstants.NAMESPACE + localName, type, kind, datatype);
  }

  private static void register(Map<String, PredicateMeta> map, String compactKey, String fullIri,
                               ProvenanceType type, ObjectKind kind, String datatype) {
    PredicateMeta meta = new PredicateMeta(type, kind, datatype);
    map.put(compactKey, meta);
    map.put(fullIri, meta);
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
      PredicateMeta meta = PREDICATE_MAP.get(field.getKey());
      if (meta == null) {
        continue;
      }
      facts.add(buildFact(field.getKey(), field.getValue(), meta));
    }
    if (facts.isEmpty()) {
      throw new ClientException(
          "credentialSubject must contain one of the supported predicates: " + ACCEPTED_PREDICATES);
    }
    return List.copyOf(facts);
  }

  private ProvenanceInfo buildFact(String fieldName, JsonNode value, PredicateMeta meta) {
    return switch (meta.kind()) {
      case IRI -> {
        String iri = extractIriReference(value);
        if (iri == null) {
          throw new ClientException(
              "Predicate '" + fieldName + "' must reference an IRI — either as a string or as "
                  + "an object with '@id' or 'id'.");
        }
        yield new ProvenanceInfo(meta.type(), expandCurie(iri), ObjectKind.IRI, null);
      }
      case LITERAL -> {
        String lexical = extractLiteralValue(value);
        if (lexical == null) {
          throw new ClientException(
              "Predicate '" + fieldName + "' must carry a literal value (string, number, "
                  + "or {\"@value\": ...}).");
        }
        yield new ProvenanceInfo(meta.type(), lexical, ObjectKind.LITERAL, meta.datatypeIri());
      }
    };
  }

  /**
   * Returns the IRI a predicate points at. PROV-O object properties relate resources to
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

  /**
   * Extracts the literal value of a JSON-LD scalar field. Accepts the compact string form (the
   * JSON-LD context declares the predicate as a literal datatype), numeric and boolean scalars,
   * and the expanded {@code {"@value": "..."}} object form.
   */
  private static String extractLiteralValue(JsonNode value) {
    if (value == null || value.isNull()) {
      return null;
    }
    if (value.isTextual()) {
      return value.asText();
    }
    if (value.isNumber() || value.isBoolean()) {
      return value.asText();
    }
    if (value.isObject()) {
      JsonNode v = value.get("@value");
      return (v != null && (v.isTextual() || v.isNumber() || v.isBoolean())) ? v.asText() : null;
    }
    return null;
  }

  /**
   * Expands a compact prefix-form IRI ({@code prov:Activity}) using the parser's known prefix
   * map. Inputs that are already absolute IRIs, contain no colon, or use an unknown prefix are
   * returned unchanged so the downstream IRI validator can flag them.
   */
  private static String expandCurie(String value) {
    int colon = value.indexOf(':');
    if (colon <= 0) {
      return value;
    }
    String prefix = value.substring(0, colon);
    String ns = KNOWN_PREFIXES.get(prefix);
    if (ns == null) {
      return value;
    }
    return ns + value.substring(colon + 1);
  }

  private JsonNode parseJson(String rawVc) {
    try {
      return objectMapper.readTree(rawVc);
    } catch (JsonProcessingException ex) {
      throw new ClientException("Invalid JSON in provenance credential: " + ex.getMessage(), ex);
    }
  }

  /**
   * Predicate metadata: the mapped enum, the expected object kind, and an optional datatype IRI.
   */
  private record PredicateMeta(ProvenanceType type, ObjectKind kind, String datatypeIri) {
  }
}
