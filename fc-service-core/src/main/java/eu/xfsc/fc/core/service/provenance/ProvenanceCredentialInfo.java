package eu.xfsc.fc.core.service.provenance;

import java.util.List;

/**
 * Metadata extracted from a raw provenance credential.
 *
 * <p>The {@code facts} list is non-empty and ordered as the predicates appeared on
 * {@code credentialSubject}. The first entry is treated as the primary fact for relational storage;
 * every fact is projected to the graph store.</p>
 */
public record ProvenanceCredentialInfo(
    String credentialId,
    List<ProvenanceInfo> facts,
    String formatLabel) {

  /** Convenience accessor for the primary fact used to populate the relational row. */
  public ProvenanceInfo primary() {
    return facts.get(0);
  }
}
