package eu.xfsc.fc.core.service.trustframework.compliance;

import java.time.Instant;

/**
 * Outcome indicating that the trust framework issued a verifiable attestation credential
 * for the asset under check.
 *
 * @param attestationCredential raw attestation credential string (JWT), or {@code null} if not
 *                              retained; the JWT's {@code iss} claim identifies the issuing service
 * @param credentialValidUntil  expiry timestamp of the issued credential, taken from the JWT
 *                              {@code exp} claim or the VC 2.0 {@code validUntil} claim;
 *                              {@code null} if the credential carries neither
 */
public record IssuedAttestation(
    String attestationCredential, Instant credentialValidUntil
) implements ComplianceCheckOutcome {

  @Override
  public boolean compliant() {
    return true;
  }
}
