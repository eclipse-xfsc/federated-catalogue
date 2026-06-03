package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.core.dao.provenance.ProvenanceType;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

  /**
   * Returns the fact used to populate the relational provenance_type column. Picks the first
   * PROV-O relation in declaration order; auxiliary predicates ({@code rdf:type}, {@code dcs:action},
   * timestamps, {@code prov:wasInformedBy}) are projected to the graph but skipped here because they
   * do not classify the credential as a whole.
   */
  public ProvenanceInfo primary() {
    return facts.stream()
        .filter(fact -> !AUXILIARY_TYPES.contains(fact.type()))
        .findFirst()
        .orElse(facts.get(0));
  }

  private static final Set<ProvenanceType> AUXILIARY_TYPES = EnumSet.of(
      ProvenanceType.TYPE,
      ProvenanceType.INFORMATION,
      ProvenanceType.STARTED_AT_TIME,
      ProvenanceType.ENDED_AT_TIME,
      ProvenanceType.ACTION);
}
