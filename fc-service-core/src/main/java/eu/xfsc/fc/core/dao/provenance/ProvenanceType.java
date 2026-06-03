package eu.xfsc.fc.core.dao.provenance;

/**
 * Provenance relation types, each mapped to a W3C PROV-O predicate.
 *
 * <p>Used to select the PROV-O triple written to the graph store when a provenance
 * credential is stored with {@code graphstore.impl != none}.</p>
 */
public enum ProvenanceType {

  /** {@code prov:wasGeneratedBy} — asset was created by an agent. */
  CREATION,

  /** {@code prov:wasDerivedFrom} — asset was derived from another entity. */
  DERIVATION,

  /** {@code prov:wasAttributedTo} — asset is attributed to an agent. */
  ATTRIBUTION,

  /** {@code prov:wasRevisionOf} — asset is a revision of another entity. */
  MODIFICATION,

  /** {@code prov:wasGeneratedBy} inverse view — activity generated this entity. */
  GENERATION,

  /** {@code prov:used} — activity used this entity (or this entity was used by an activity). */
  USAGE,

  /** {@code prov:wasAssociatedWith} — activity was associated with this agent. */
  ASSOCIATION,

  /** {@code prov:actedOnBehalfOf} — agent acted on behalf of another agent. */
  DELEGATION,

  /**
   * {@code rdf:type} — declares the RDF class of the subject (e.g. {@code prov:Activity}).
   */
  TYPE,

  /**
   * {@code prov:wasInformedBy} — chains an activity to a prior activity.
   */
  INFORMATION,

  /**
   * {@code prov:startedAtTime} — xsd:dateTime literal for activity start.
   */
  STARTED_AT_TIME,

  /**
   * {@code prov:endedAtTime} — xsd:dateTime literal for activity end.
   */
  ENDED_AT_TIME,

  /**
   * {@code dcs:action} — string literal labelling the lifecycle action (e.g. "approved").
   */
  ACTION
}
