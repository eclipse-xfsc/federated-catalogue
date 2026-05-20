package eu.xfsc.fc.core.service.trustframework.compliance;

import java.time.Instant;

/**
 * Outcome indicating that the trust framework issued a verifiable attestation credential
 * for the asset under check.
 *
 * @param attestationCredential raw attestation credential string (JWT), or {@code null} if not
 *                              retained; the JWT's {@code iss} claim identifies the issuing service
 * @param credentialValidUntil  expiry timestamp of the issued credential; {@code null} if the
 *                              credential carries no {@code exp} claim
 */
public record IssuedAttestation(
    String attestationCredential, Instant credentialValidUntil
) implements ComplianceCheckOutcome {

  @Override
  public boolean compliant() {
    return true;
  }
}
