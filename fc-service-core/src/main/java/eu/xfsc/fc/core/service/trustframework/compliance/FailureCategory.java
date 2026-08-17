package eu.xfsc.fc.core.service.trustframework.compliance;

/**
 * Classifies the reason a compliance check could not produce a positive outcome.
 *
 * <p>{@link #UNVERIFIABLE_ATTESTATION} is the only constant ever carried by an
 * {@link UnverifiableAttestation} outcome; it always means the compliance service was reached and
 * responded, but the attestation it returned (or the input presented to it) could not be verified.
 *
 * <p>{@link #SERVICE_UNREACHABLE} and {@link #SERVICE_TIMEOUT} classify the opposite situation: the
 * compliance service was never reached, so no verdict about the asset exists at all. A prior review
 * of the external-trust-framework work deliberately removed transport/timeout categories from this
 * enum in favour of letting {@link eu.xfsc.fc.core.exception.ServiceUnavailableException} and
 * {@link eu.xfsc.fc.core.exception.TimeoutException} propagate as HTTP 503/504 — collapsing every
 * infrastructure failure into "no stored result" was an accepted trade-off at the time. They are
 * reintroduced here for a narrower purpose: recording a failed *attempt* in the audit trail. They
 * are never assigned to {@link ComplianceCheckOutcome#compliant()} results returned to a
 * caller — the type system does not enforce this pairing, so this comment is the only guard — they
 * are written only via {@link ComplianceResultStore#storeFailedAttempt}, which persists directly and
 * never produces a {@link ComplianceCheckOutcome} instance.
 */
public enum FailureCategory {
  UNVERIFIABLE_ATTESTATION,
  SERVICE_UNREACHABLE,
  SERVICE_TIMEOUT
}
