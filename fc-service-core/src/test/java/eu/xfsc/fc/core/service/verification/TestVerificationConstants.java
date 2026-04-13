package eu.xfsc.fc.core.service.verification;

/**
 * Test-only constants for the verification package.
 */
class TestVerificationConstants {

  // Gaia-X 2511 vocabulary namespace — used in test fixtures to build realistic Loire credentials.
  // Not needed in production: Loire format detection is based on JWT structure, not vocabulary URLs.
  static final String GAIAX_2511_CONTEXT = "https://w3id.org/gaia-x/2511#";

  private TestVerificationConstants() {
  }
}
