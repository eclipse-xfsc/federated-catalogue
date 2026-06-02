package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;

/**
 * A single PROV-O predicate detected on the credential subject: the internal type classification
 * and the IRI value of the predicate, which becomes the triple object when projected to the
 * graph store.
 */
public record ProvenanceInfo(ProvenanceType type, String objectValue) {}
