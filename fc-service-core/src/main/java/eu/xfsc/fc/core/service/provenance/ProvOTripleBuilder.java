package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.provenance.ProvenanceInfo.ObjectKind;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Builds RDF {@link RdfClaim} triples for a provenance credential.
 *
 * <p>Maps each {@link ProvenanceType} to its corresponding predicate IRI (W3C PROV-O for the
 * provenance relations, RDF and the catalogue's DCS namespace for the activity-centric extensions
 * {@code rdf:type} and {@code dcs:action}). Each fact yields one N-Triples line whose object is
 * rendered as an IRI or as a literal depending on the fact's {@link ObjectKind}.</p>
 *
 * <p>For an activity-centric credential the subject is the activity IRI; for an entity-centric
 * credential it is the versioned asset identifier ({@code assetId:vN}).</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ProvOTripleBuilder {

  // Prevents N-Triples triple injection: rejects chars forbidden in IRIREF per RDF 1.2 N-Triples spec
  private static final Pattern INVALID_IRI_CHARS = Pattern.compile("[\\x00-\\x20<>\"{}|^`\\\\]");

  // Rejects literal payloads that would break out of the N-Triples quoting (raw quotes, backslashes,
  // CR/LF). Lexical forms that legitimately need these characters must escape them upstream.
  private static final Pattern INVALID_LITERAL_CHARS = Pattern.compile("[\"\\\\\\r\\n]");

  private static final String PROV_WAS_GENERATED_BY = "<" + ProvOConstants.NAMESPACE + "wasGeneratedBy>";
  private static final String PROV_WAS_DERIVED_FROM = "<" + ProvOConstants.NAMESPACE + "wasDerivedFrom>";
  private static final String PROV_WAS_ATTRIBUTED_TO = "<" + ProvOConstants.NAMESPACE + "wasAttributedTo>";
  private static final String PROV_WAS_REVISION_OF = "<" + ProvOConstants.NAMESPACE + "wasRevisionOf>";
  private static final String PROV_GENERATED = "<" + ProvOConstants.NAMESPACE + "generated>";
  private static final String PROV_USED = "<" + ProvOConstants.NAMESPACE + "used>";
  private static final String PROV_WAS_ASSOCIATED_WITH = "<" + ProvOConstants.NAMESPACE + "wasAssociatedWith>";
  private static final String PROV_ACTED_ON_BEHALF_OF = "<" + ProvOConstants.NAMESPACE + "actedOnBehalfOf>";
  private static final String PROV_WAS_INFORMED_BY = "<" + ProvOConstants.NAMESPACE + "wasInformedBy>";
  private static final String PROV_STARTED_AT_TIME = "<" + ProvOConstants.NAMESPACE + "startedAtTime>";
  private static final String PROV_ENDED_AT_TIME = "<" + ProvOConstants.NAMESPACE + "endedAtTime>";
  private static final String RDF_TYPE = "<" + ProvOConstants.RDF_TYPE + ">";
  private static final String DCS_ACTION = "<" + ProvOConstants.DCS_ACTION + ">";

  /**
   * Builds a single triple for a recognised predicate. Retained for IRI-valued facts; literal
   * facts must go through {@link #buildAll(String, List)}.
   *
   * @param subjectIri  IRI used as the triple subject (versioned asset IRI or activity IRI)
   * @param provenanceType the provenance relation type to map to a predicate
   * @param objectValue    IRI value of the predicate from {@code credentialSubject}
   * @return a single-element list with the corresponding {@link RdfClaim}
   */
  public static List<RdfClaim> build(
      String subjectIri, ProvenanceType provenanceType, String objectValue) {
    validateIri(subjectIri, "assetId");
    validateIri(objectValue, "objectValue");
    return List.of(new RdfClaim(
        "<" + subjectIri + ">",
        predicateFor(provenanceType),
        "<" + objectValue + ">"));
  }

  /**
   * Builds one triple per recognised fact, reusing the same subject IRI so that the projected
   * graph contains a star of relations rooted at it.
   *
   * @param subjectIri triple subject (versioned asset IRI or activity IRI)
   * @param facts      ordered list of recognised facts, as parsed from the VC
   * @return one {@link RdfClaim} per fact, in input order
   */
  public static List<RdfClaim> buildAll(String subjectIri, List<ProvenanceInfo> facts) {
    validateIri(subjectIri, "assetId");
    List<RdfClaim> triples = new ArrayList<>(facts.size());
    for (ProvenanceInfo fact : facts) {
      triples.add(new RdfClaim(
          "<" + subjectIri + ">",
          predicateFor(fact.type()),
          renderObject(fact)));
    }
    return List.copyOf(triples);
  }

  private static String renderObject(ProvenanceInfo fact) {
    return switch (fact.objectKind()) {
      case IRI -> {
        validateIri(fact.objectValue(), "objectValue");
        yield "<" + fact.objectValue() + ">";
      }
      case LITERAL -> {
        validateLiteral(fact.objectValue());
        String quoted = "\"" + fact.objectValue() + "\"";
        yield fact.datatypeIri() == null ? quoted : quoted + "^^<" + fact.datatypeIri() + ">";
      }
    };
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
      case INFORMATION -> PROV_WAS_INFORMED_BY;
      case STARTED_AT_TIME -> PROV_STARTED_AT_TIME;
      case ENDED_AT_TIME -> PROV_ENDED_AT_TIME;
      case TYPE -> RDF_TYPE;
      case ACTION -> DCS_ACTION;
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

  private static void validateLiteral(String value) {
    if (value == null || INVALID_LITERAL_CHARS.matcher(value).find()) {
      throw new ClientException(
          "Invalid literal value: must not contain unescaped quotes, backslashes, or newlines");
    }
  }
}
