package eu.xfsc.fc.core.service.trustframework;

/**
 * Test-only constants for the trust-framework package.
 *
 * <p>These identifiers are test-scoped because they encode specific profile IDs, client-type
 * strings, and service URLs that are only relevant to integration tests. Production code resolves
 * these values from classpath bundle YAML; they must not be inlined in production source.
 */
class TestTrustFrameworkConstants {

  /**
   * Profile ID of the built-in Gaia-X Loire 2511 bundle.
   */
  static final String PROFILE_GAIA_X_2511 = "gaia-x-2511";

  /**
   * Client-type string identifying the GXDCH Loire v2 compliance client.
   */
  static final String CLIENT_TYPE_GXDCH_LOIRE = "gxdch-loire";

  /**
   * Canonical base URL of the live GXDCH Loire compliance service.
   */
  static final String GXDCH_LOIRE_SERVICE_URL = "https://compliance.gaia-x.eu/v2";

  /**
   * API version string for the GXDCH Loire v2 compliance protocol.
   */
  static final String API_VERSION_V2 = "v2";

  /**
   * Expected per-request timeout in seconds for the Loire compliance client.
   */
  static final int TIMEOUT_SECONDS = 30;

  /**
   * Property key for the trust-anchor URL in the gaia-x-2511 bundle properties map.
   */
  static final String TRUST_ANCHOR_URL_PROP = "trust_anchor_url";

  private TestTrustFrameworkConstants() {
  }
}
