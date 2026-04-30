package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;

/**
 * Result of PROV-O predicate detection: the internal type classification and the
 * IRI value of the predicate from {@code credentialSubject}, which becomes the triple object.
 */
public record ProvenanceInfo(ProvenanceType type, String objectValue) {}
