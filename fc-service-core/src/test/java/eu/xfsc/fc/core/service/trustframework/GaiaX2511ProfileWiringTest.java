package eu.xfsc.fc.core.service.trustframework;

import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.API_VERSION_V2;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.CLIENT_TYPE_JWT_VC;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.GXDCH_LOIRE_COMPLIANCE_PATH;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.GXDCH_LOIRE_SERVICE_URL;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.PROFILE_GAIA_X_2511;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TIMEOUT_SECONDS;
import static eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry.TRUST_ANCHOR_URL;
import static org.assertj.core.api.Assertions.assertThat;

import eu.xfsc.fc.core.service.trustframework.compliance.TrustFrameworkProfileConfig;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pure registry-wiring test for the {@code gaia-x-2511} profile.
 *
 * <p>Asserts that the compliance properties ({@code client_type}, {@code api_version},
 * {@code service_url}, {@code compliance_path}, {@code timeout_seconds}) are correctly propagated
 * from {@code gaia-x-2511/framework.yaml} through {@link TrustFrameworkBundleLoader} into
 * {@link TrustFrameworkRegistry}. No HTTP traffic, no live service, no Spring context.
 */
class GaiaX2511ProfileWiringTest {

  private TrustFrameworkRegistry registry;

  @BeforeEach
  void setUp() throws IOException {
    List<TrustFrameworkBundle> bundles = new TrustFrameworkBundleLoader().load();
    registry = new TrustFrameworkRegistry(bundles);
  }

  @Test
  void getProfileConfig_forGaiaX2511_clientTypeIsJwtVcCompliance() {
    TrustFrameworkProfileConfig config = loadConfig();

    assertThat(config.clientType())
        .as("client_type must be 'jwt-vc-compliance' in gaia-x-2511/framework.yaml")
        .isEqualTo(CLIENT_TYPE_JWT_VC);
  }

  @Test
  void getProfileConfig_forGaiaX2511_compliancePathIsLoireEndpoint() {
    TrustFrameworkProfileConfig config = loadConfig();

    assertThat(config.compliancePath())
        .as("compliance_path must be the Loire standard-compliance endpoint in gaia-x-2511/framework.yaml")
        .isEqualTo(GXDCH_LOIRE_COMPLIANCE_PATH);
  }

  @Test
  void getProfileConfig_forGaiaX2511_apiVersionIsV2() {
    TrustFrameworkProfileConfig config = loadConfig();

    assertThat(config.apiVersion())
        .as("api_version must be 'v2' in gaia-x-2511/framework.yaml")
        .isEqualTo(API_VERSION_V2);
  }

  @Test
  void getProfileConfig_forGaiaX2511_serviceUrlIsLoireEndpoint() {
    TrustFrameworkProfileConfig config = loadConfig();

    assertThat(config.serviceUrl())
        .as("service_url must be the Loire compliance endpoint in gaia-x-2511/framework.yaml")
        .isEqualTo(GXDCH_LOIRE_SERVICE_URL);
  }

  @Test
  void getProfileConfig_forGaiaX2511_timeoutIs30Seconds() {
    TrustFrameworkProfileConfig config = loadConfig();

    assertThat(config.timeoutSeconds())
        .as("timeout_seconds must be 30 in gaia-x-2511/framework.yaml")
        .isEqualTo(TIMEOUT_SECONDS);
  }

  @Test
  void getBundle_forGaiaX2511_trustAnchorUrlIsNonBlank() {
    String trustAnchorUrl = registry.getBundle(PROFILE_GAIA_X_2511)
        .map(b -> b.config().properties().get(TRUST_ANCHOR_URL))
        .orElseThrow(() -> new AssertionError("No bundle registered for " + PROFILE_GAIA_X_2511));

    assertThat(trustAnchorUrl)
        .as("trust_anchor_url must be non-blank in gaia-x-2511/framework.yaml")
        .isNotBlank();
  }

  /**
   * Retrieves the profile config for the {@code gaia-x-2511} profile, failing the test
   * if no bundle is registered.
   */
  private TrustFrameworkProfileConfig loadConfig() {
    return registry.getProfileConfig(PROFILE_GAIA_X_2511)
        .orElseThrow(() -> new AssertionError(
            "No profile config registered for " + PROFILE_GAIA_X_2511));
  }
}
