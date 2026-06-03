package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;

/**
 * A single recognised predicate detected on the credential subject. Carries both the internal type
 * classification and the value that becomes the triple object when projected to the graph store.
 *
 * <p>PROV-O object properties relate resources to resources; PROV-O literal properties (e.g.
 * {@code prov:startedAtTime}, {@code prov:endedAtTime}) and the trust-framework's lifecycle label
 * {@code dcs:action} carry literal values. The {@link ObjectKind} tag tells the triple builder
 * whether to render the object as an IRI ({@code <value>}) or as an N-Triples literal
 * ({@code "value"} or {@code "value"^^<datatypeIri>}).</p>
 */
public record ProvenanceInfo(
    ProvenanceType type,
    String objectValue,
    ObjectKind objectKind,
    String datatypeIri) {

  /**
   * Convenience constructor for IRI-valued predicates — preserves prior call-sites.
   */
  public ProvenanceInfo(ProvenanceType type, String objectValue) {
    this(type, objectValue, ObjectKind.IRI, null);
  }

  /**
   * Factory for an IRI-valued fact.
   */
  public static ProvenanceInfo iri(ProvenanceType type, String iri) {
    return new ProvenanceInfo(type, iri, ObjectKind.IRI, null);
  }

  /**
   * Factory for a plain string-literal fact (no datatype).
   */
  public static ProvenanceInfo string(ProvenanceType type, String lexicalForm) {
    return new ProvenanceInfo(type, lexicalForm, ObjectKind.LITERAL, null);
  }

  /**
   * Factory for an {@code xsd:dateTime}-typed literal fact.
   */
  public static ProvenanceInfo dateTime(ProvenanceType type, String lexicalForm) {
    return new ProvenanceInfo(
        type, lexicalForm, ObjectKind.LITERAL, ProvOConstants.XSD_DATETIME);
  }

  /**
   * How the object value should be serialised in the projected N-Triple.
   */
  public enum ObjectKind {
    IRI,
    LITERAL
  }
}
