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
  MODIFICATION
}
