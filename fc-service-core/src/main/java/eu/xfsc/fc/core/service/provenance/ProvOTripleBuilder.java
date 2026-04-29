package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.RdfClaim;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds PROV-O {@link RdfClaim} triples for a provenance credential.
 *
 * <p>Maps each {@link ProvenanceType} to its corresponding W3C PROV-O predicate URI, producing a
 * single triple in N-Triples form: {@code <assetId> <prov:predicate> <objectValue> .}</p>
 *
 * <p>The triple subject is the versioned asset identifier ({@code assetId:vN}), the predicate is
 * the PROV-O URI, and the object is the value of that predicate as declared in the VC's
 * {@code credentialSubject} (e.g. an agent DID, source entity IRI, or activity IRI).</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProvOTripleBuilder {

  // Prevents N-Triples triple injection: rejects chars forbidden in IRIREF per RDF 1.2 N-Triples spec
  private static final Pattern INVALID_IRI_CHARS = Pattern.compile("[\\x00-\\x20<>\"{}|^`\\\\]");

  private static final String PROV_WAS_GENERATED_BY = "<" + ProvOConstants.NAMESPACE + "wasGeneratedBy>";
  private static final String PROV_WAS_DERIVED_FROM = "<" + ProvOConstants.NAMESPACE + "wasDerivedFrom>";
  private static final String PROV_WAS_ATTRIBUTED_TO = "<" + ProvOConstants.NAMESPACE + "wasAttributedTo>";
  private static final String PROV_WAS_REVISION_OF = "<" + ProvOConstants.NAMESPACE + "wasRevisionOf>";

  /**
   * Builds a list containing the single PROV-O triple for the given provenance credential.
   *
   * @param assetId        versioned asset identifier used as the triple subject (e.g. {@code did:example:abc:v1})
   * @param provenanceType the provenance relation type to map to a PROV-O predicate
   * @param objectValue    IRI value of the PROV-O predicate from {@code credentialSubject}
   * @return a single-element list with the corresponding {@link RdfClaim}
   */
  public static List<RdfClaim> build(
      String assetId, ProvenanceType provenanceType, String objectValue) {
    validateIri(assetId, "assetId");
    validateIri(objectValue, "objectValue");
    String predicate = switch (provenanceType) {
      case CREATION -> PROV_WAS_GENERATED_BY;
      case DERIVATION -> PROV_WAS_DERIVED_FROM;
      case ATTRIBUTION -> PROV_WAS_ATTRIBUTED_TO;
      case MODIFICATION -> PROV_WAS_REVISION_OF;
    };
    RdfClaim triple = new RdfClaim(
        "<" + assetId + ">",
        predicate,
        "<" + objectValue + ">");
    return List.of(triple);
  }

  private static void validateIri(String value, String field) {
    if (INVALID_IRI_CHARS.matcher(value).find()) {
      throw new ClientException(
          "Invalid IRI for " + field + ": contains characters forbidden in N-Triples IRIREF");
    }
    try {
      if (!URI.create(value).isAbsolute()) {
        throw new ClientException("Invalid IRI for " + field + ": must be an absolute IRI");
      }
    } catch (IllegalArgumentException ex) {
      throw new ClientException("Invalid IRI for " + field + ": " + ex.getMessage(), ex);
    }
  }
}
