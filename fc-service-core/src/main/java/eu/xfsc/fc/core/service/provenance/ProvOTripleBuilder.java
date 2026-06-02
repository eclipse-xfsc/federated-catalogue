package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.RdfClaim;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.ArrayList;
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
  private static final String PROV_GENERATED = "<" + ProvOConstants.NAMESPACE + "generated>";
  private static final String PROV_USED = "<" + ProvOConstants.NAMESPACE + "used>";
  private static final String PROV_WAS_ASSOCIATED_WITH = "<" + ProvOConstants.NAMESPACE + "wasAssociatedWith>";
  private static final String PROV_ACTED_ON_BEHALF_OF = "<" + ProvOConstants.NAMESPACE + "actedOnBehalfOf>";

  /**
   * Builds the PROV-O triples for a single recognised predicate.
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
    return List.of(new RdfClaim(
        "<" + assetId + ">",
        predicateFor(provenanceType),
        "<" + objectValue + ">"));
  }

  /**
   * Builds one triple per recognised predicate on the credential subject. The subject IRI is reused
   * across all triples so that the projected graph contains a star of relations rooted at the
   * versioned asset identifier.
   *
   * @param assetId versioned asset identifier used as the triple subject
   * @param facts   ordered list of recognised predicate/value pairs, as parsed from the VC
   * @return one {@link RdfClaim} per fact, in input order
   */
  public static List<RdfClaim> buildAll(String assetId, List<ProvenanceInfo> facts) {
    validateIri(assetId, "assetId");
    List<RdfClaim> triples = new ArrayList<>(facts.size());
    for (ProvenanceInfo fact : facts) {
      validateIri(fact.objectValue(), "objectValue");
      triples.add(new RdfClaim(
          "<" + assetId + ">",
          predicateFor(fact.type()),
          "<" + fact.objectValue() + ">"));
    }
    return List.copyOf(triples);
  }

  private static String predicateFor(ProvenanceType provenanceType) {
    return switch (provenanceType) {
      case CREATION -> PROV_WAS_GENERATED_BY;
      case DERIVATION -> PROV_WAS_DERIVED_FROM;
      case ATTRIBUTION -> PROV_WAS_ATTRIBUTED_TO;
      case MODIFICATION -> PROV_WAS_REVISION_OF;
      case GENERATION -> PROV_GENERATED;
      case USAGE -> PROV_USED;
      case ASSOCIATION -> PROV_WAS_ASSOCIATED_WITH;
      case DELEGATION -> PROV_ACTED_ON_BEHALF_OF;
    };
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
