package eu.xfsc.fc.core.service.trustframework.compliance;

/**
 * Sealed result type for a trust-framework compliance check.
 *
 * <p>Permitted subtypes cover every possible outcome: an issued attestation credential,
 * a trust-list membership entry, or a failure that could not be verified.
 * Callers should use an exhaustive switch expression to dispatch on the concrete type.
 */
public sealed interface ComplianceCheckOutcome
    permits IssuedAttestation, UnverifiableAttestation {

  /**
   * Returns {@code true} when the check produced a positive compliance result.
   */
  boolean compliant();
}
