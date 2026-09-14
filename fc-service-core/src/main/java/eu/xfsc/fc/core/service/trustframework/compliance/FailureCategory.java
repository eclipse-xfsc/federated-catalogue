package eu.xfsc.fc.core.service.trustframework.compliance;

/**
 * Classifies the reason a compliance check could not produce a positive outcome.
 *
 * <p>{@link #UNVERIFIABLE_ATTESTATION}, {@link #MALFORMED_CREDENTIAL}, and
 * {@link #MALFORMED_ATTESTATION} are carried by an {@link UnverifiableAttestation} outcome; all
 * three mean the compliance service was reached (except {@link #MALFORMED_CREDENTIAL}, where no
 * request was even sent), but no positive verdict could be established — never confuse this with
 * an actual attestation that the asset is compliant.
 *
 * <p>{@link #SERVICE_UNREACHABLE}, {@link #SERVICE_ERROR}, and {@link #SERVICE_TIMEOUT} classify the
 * opposite situation: no verdict about the asset exists at all. A prior review of the
 * external-trust-framework work deliberately removed transport/timeout categories from this
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

  /**  The compliance service evaluated the asset and returned a genuine non-compliant verdict.  */
  UNVERIFIABLE_ATTESTATION,

  /**  The input presented to the check was malformed, so no request was sent to the service at all.  */
  MALFORMED_CREDENTIAL,

  /**  The service issued an attestation (a positive verdict), but the response could not be parsed
   *  on our side — this is our own defect, not evidence of non-compliance.  */
  MALFORMED_ATTESTATION,

  SERVICE_UNREACHABLE,

  /**
   * The service was reached but responded with an error (e.g. HTTP 5xx), as opposed to
   * {@link #SERVICE_UNREACHABLE}'s connection-level failure. See
   * {@link eu.xfsc.fc.core.exception.ServiceErrorException}.
   */
  SERVICE_ERROR,
  SERVICE_TIMEOUT
}
