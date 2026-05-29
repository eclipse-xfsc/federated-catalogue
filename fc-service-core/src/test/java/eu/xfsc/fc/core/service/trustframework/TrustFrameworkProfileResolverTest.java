package eu.xfsc.fc.core.service.trustframework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBundleConfig;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkBundleConfigRepository;
import eu.xfsc.fc.core.service.trustframework.compliance.TrustFrameworkProfileConfig;

class TrustFrameworkProfileResolverTest {

  private static final String BUNDLE_ID = "gaia-x-2511";
  private static final String FAMILY_ID = "gaia-x";

  private static final TrustFrameworkProfileConfig YAML_BASELINE = new TrustFrameworkProfileConfig(
      BUNDLE_ID, FAMILY_ID,
      "jwt-vc-compliance",
      "https://compliance.gaia-x.eu/v2",
      "/api/credential-offers/standard-compliance",
      "v2",
      30
  );

  private TrustFrameworkRegistry registry;
  private TrustFrameworkBundleConfigRepository overrides;
  private TrustFrameworkProfileResolver resolver;

  @BeforeEach
  void setUp() {
    registry = mock(TrustFrameworkRegistry.class);
    overrides = mock(TrustFrameworkBundleConfigRepository.class);
    resolver = new TrustFrameworkProfileResolver(registry, overrides);
    when(registry.getProfileConfig(BUNDLE_ID)).thenReturn(Optional.of(YAML_BASELINE));
    when(overrides.findById(any())).thenReturn(Optional.empty());
  }

  @Test
  void getProfileConfig_noOverrideRow_returnsYamlBaseline() {
    Optional<TrustFrameworkProfileConfig> result = resolver.getProfileConfig(BUNDLE_ID);

    assertThat(result).contains(YAML_BASELINE);
  }

  @Test
  void getProfileConfig_unknownBundleId_returnsEmpty() {
    when(registry.getProfileConfig("missing")).thenReturn(Optional.empty());

    Optional<TrustFrameworkProfileConfig> result = resolver.getProfileConfig("missing");

    assertThat(result).isEmpty();
  }

  @Test
  void getProfileConfig_serviceUrlOverride_appliesOverride() {
    TrustFrameworkBundleConfig override = TrustFrameworkBundleConfig.builder()
        .bundleId(BUNDLE_ID)
        .serviceUrl("https://mock.test/v2")
        .build();
    when(overrides.findById(BUNDLE_ID)).thenReturn(Optional.of(override));

    TrustFrameworkProfileConfig result = resolver.getProfileConfig(BUNDLE_ID).orElseThrow();

    assertThat(result.serviceUrl()).isEqualTo("https://mock.test/v2");
    assertThat(result.compliancePath()).isEqualTo(YAML_BASELINE.compliancePath());
    assertThat(result.apiVersion()).isEqualTo(YAML_BASELINE.apiVersion());
    assertThat(result.timeoutSeconds()).isEqualTo(YAML_BASELINE.timeoutSeconds());
    assertThat(result.clientType()).isEqualTo(YAML_BASELINE.clientType());
  }

  @Test
  void getProfileConfig_timeoutOverride_appliesOverride() {
    TrustFrameworkBundleConfig override = TrustFrameworkBundleConfig.builder()
        .bundleId(BUNDLE_ID)
        .timeoutSeconds(90)
        .build();
    when(overrides.findById(BUNDLE_ID)).thenReturn(Optional.of(override));

    TrustFrameworkProfileConfig result = resolver.getProfileConfig(BUNDLE_ID).orElseThrow();

    assertThat(result.timeoutSeconds()).isEqualTo(90);
    assertThat(result.serviceUrl()).isEqualTo(YAML_BASELINE.serviceUrl());
  }

  @Test
  void getProfileConfig_clientTypeOverride_appliesOverride() {
    TrustFrameworkBundleConfig override = TrustFrameworkBundleConfig.builder()
        .bundleId(BUNDLE_ID)
        .clientType("untp-credential-exchange")
        .build();
    when(overrides.findById(BUNDLE_ID)).thenReturn(Optional.of(override));

    TrustFrameworkProfileConfig result = resolver.getProfileConfig(BUNDLE_ID).orElseThrow();

    assertThat(result.clientType()).isEqualTo("untp-credential-exchange");
    assertThat(result.serviceUrl()).isEqualTo(YAML_BASELINE.serviceUrl());
  }

  @Test
  void getProfileConfig_blankOverrideFields_fallsBackToYaml() {
    TrustFrameworkBundleConfig override = TrustFrameworkBundleConfig.builder()
        .bundleId(BUNDLE_ID)
        .serviceUrl("   ")
        .apiVersion("")
        .build();
    when(overrides.findById(BUNDLE_ID)).thenReturn(Optional.of(override));

    TrustFrameworkProfileConfig result = resolver.getProfileConfig(BUNDLE_ID).orElseThrow();

    assertThat(result.serviceUrl()).isEqualTo(YAML_BASELINE.serviceUrl());
    assertThat(result.apiVersion()).isEqualTo(YAML_BASELINE.apiVersion());
  }

  @Test
  void getProfileConfig_allFieldsOverridden_appliesAll() {
    TrustFrameworkBundleConfig override = TrustFrameworkBundleConfig.builder()
        .bundleId(BUNDLE_ID)
        .clientType("untp-credential-exchange")
        .serviceUrl("https://mock.test/v3")
        .compliancePath("/different/path")
        .apiVersion("v3")
        .timeoutSeconds(15)
        .build();
    when(overrides.findById(BUNDLE_ID)).thenReturn(Optional.of(override));

    TrustFrameworkProfileConfig result = resolver.getProfileConfig(BUNDLE_ID).orElseThrow();

    assertThat(result.clientType()).isEqualTo("untp-credential-exchange");
    assertThat(result.serviceUrl()).isEqualTo("https://mock.test/v3");
    assertThat(result.compliancePath()).isEqualTo("/different/path");
    assertThat(result.apiVersion()).isEqualTo("v3");
    assertThat(result.timeoutSeconds()).isEqualTo(15);
    assertThat(result.frameworkProfileId()).isEqualTo(BUNDLE_ID);
    assertThat(result.familyId()).isEqualTo(FAMILY_ID);
  }
}
