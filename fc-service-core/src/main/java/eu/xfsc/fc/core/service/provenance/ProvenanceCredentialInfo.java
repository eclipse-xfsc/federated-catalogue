package eu.xfsc.fc.core.service.provenance;

/**
 * Metadata extracted from a raw provenance credential.
 */
public record ProvenanceCredentialInfo(
    String credentialId,
    ProvenanceInfo provenance,
    String formatLabel) {}
