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
  private static final String CONTEXT_KEY = "@context";
  private static final String ACCEPTED_PREDICATES =
      "prov:wasGeneratedBy, prov:wasDerivedFrom, prov:wasAttributedTo, prov:wasRevisionOf";

  /**
   * Recognizes both compact ({@code prov:X}) and expanded ({@code http://www.w3.org/ns/prov#X})
   * forms of the four supported PROV-O predicates.
   */
  private static final Map<String, ProvenanceType> PREDICATE_MAP = Map.of(
      "prov:wasGeneratedBy", ProvenanceType.CREATION,
      ProvOConstants.NAMESPACE + "wasGeneratedBy", ProvenanceType.CREATION,
      "prov:wasDerivedFrom", ProvenanceType.DERIVATION,
      ProvOConstants.NAMESPACE + "wasDerivedFrom", ProvenanceType.DERIVATION,
      "prov:wasAttributedTo", ProvenanceType.ATTRIBUTION,
      ProvOConstants.NAMESPACE + "wasAttributedTo", ProvenanceType.ATTRIBUTION,
      "prov:wasRevisionOf", ProvenanceType.MODIFICATION,
      ProvOConstants.NAMESPACE + "wasRevisionOf", ProvenanceType.MODIFICATION
  );

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
      return verificationService.verifyCredential(content);
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

  private String extractCredentialId(JsonNode root) {
    JsonNode idNode = root.path(VC_ID_KEY);
    return idNode.isTextual() ? idNode.asText() : null;
  }

  private ProvenanceInfo extractProvenance(JsonNode root) {
    JsonNode subject = root.path(CREDENTIAL_SUBJECT_KEY);
    if (subject.isMissingNode() || subject.isNull()) {
      throw new ClientException(
          "Provenance credential must contain a 'credentialSubject' with a supported PROV-O predicate. "
              + "Accepted predicates: " + ACCEPTED_PREDICATES);
    }
    for (Map.Entry<String, JsonNode> field : subject.properties()) {
      ProvenanceType type = PREDICATE_MAP.get(field.getKey());
      if (type != null) {
        String objectValue = field.getValue().asText(null);
        if (objectValue == null) {
          throw new ClientException(
              "PROV-O predicate '" + field.getKey() + "' must have a string value.");
        }
        return new ProvenanceInfo(type, objectValue);
      }
    }
    throw new ClientException(
        "credentialSubject must contain one of the supported PROV-O predicates: "
            + ACCEPTED_PREDICATES);
  }

  private JsonNode parseJson(String rawVc) {
    try {
      return objectMapper.readTree(rawVc);
    } catch (JsonProcessingException ex) {
      throw new ClientException("Invalid JSON in provenance credential: " + ex.getMessage(), ex);
    }
  }
}
