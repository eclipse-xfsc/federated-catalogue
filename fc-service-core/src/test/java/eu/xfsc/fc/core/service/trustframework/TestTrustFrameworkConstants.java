package eu.xfsc.fc.core.service.trustframework;

/**
 * Test-only constants for the trust-framework package.
 *
 * <p>These identifiers are test-scoped because they encode specific profile IDs, client-type
 * strings, and service URLs that are only relevant to integration tests. Production code resolves
 * these values from classpath bundle YAML; they must not be inlined in production source.
 *
 * <p>Visibility is public for all constants to avoid unnecessary adjustments when scope changes.
 */
public class TestTrustFrameworkConstants {

  /**
   * Profile ID of the built-in Gaia-X Loire 2511 bundle.
   */
  public static final String PROFILE_GAIA_X_2511 = "gaia-x-2511";

  /**
   * Trust framework family names.
   */
  public static final String TFW_FAMILY_GAIA_X = "gaia-x";

  /**
   * Client-type string identifying the generic REST + JWT-VC compliance client.
   */
  public static final String CLIENT_TYPE_JWT_VC = "jwt-vc-compliance";

  /**
   * Canonical base URL of the live GXDCH Loire compliance service.
   */
  public static final String GXDCH_LOIRE_SERVICE_URL = "https://compliance.gaia-x.eu/v2";

  /**
   * Compliance endpoint path used by the Gaia-X Loire / GXDCH deployment.
   */
  public static final String GXDCH_LOIRE_COMPLIANCE_PATH = "/api/credential-offers/standard-compliance";

  /**
   * API version string for the GXDCH Loire v2 compliance protocol.
   */
  public static final String API_VERSION_V2 = "v2";

  /**
   * Expected per-request timeout in seconds for the Loire compliance client.
   */
  public static final int TIMEOUT_SECONDS = 30;

  /**
   * Base classes known to be declared in the default gaia-x bundle (from framework.yaml).
   */
  public static final String TFW_BASE_CLASS_PARTICIPANT = "Participant";
  public static final String TFW_BASE_CLASS_DIGITAL_SERVICE_OFFERING = "DigitalServiceOffering";
  public static final String TFW_BASE_CLASS_SERVICE_OFFERING = "ServiceOffering";
  public static final String TFW_BASE_CLASS_RESOURCE = "Resource";

  private TestTrustFrameworkConstants() {
  }
}
