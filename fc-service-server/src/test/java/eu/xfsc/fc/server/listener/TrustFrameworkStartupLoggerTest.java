package eu.xfsc.fc.server.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.core.service.trustframework.ValidationType;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

/**
 * Unit tests for {@link TrustFrameworkStartupLogger}.
 */
@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class TrustFrameworkStartupLoggerTest {

  @Mock
  private TrustFrameworkRegistry registry;

  @Mock
  private TrustFrameworkService trustFrameworkService;

  @Mock
  private ApplicationReadyEvent event;

  @Test
  void onApplicationEvent_gaiaXFamilyDisabled_logsDiscoverabilityHintWithProfileAndPaths(
      CapturedOutput output) {

    FrameworkBundleConfig config = new FrameworkBundleConfig(
        "gaia-x-2511", "gaia-x", "https://w3id.org/gaia-x/2511#",
        ValidationType.SHACL, Map.of(), Map.of());
    TrustFrameworkBundle bundle = new TrustFrameworkBundle(config, null, null);
    when(registry.getAllBundles()).thenReturn(List.of(bundle));
    when(trustFrameworkService.isEnabled("gaia-x")).thenReturn(false);
    TrustFrameworkStartupLogger logger =
        new TrustFrameworkStartupLogger(registry, trustFrameworkService);

    logger.onApplicationEvent(event);

    assertThat(output.getAll())
        .contains("trust-framework 'gaia-x' registered (profiles: gaia-x-2511) — disabled.")
        .contains("PATCH /admin/trust-frameworks/gaia-x {\"enabled\":true}")
        .contains("FEDERATED_CATALOGUE_ENABLED_TRUST_FRAMEWORKS=gaia-x");
  }

  @Test
  void onApplicationEvent_gaiaXFamilyEnabled_doesNotLogDiscoverabilityHint(
      CapturedOutput output) {

    FrameworkBundleConfig config = new FrameworkBundleConfig(
        "gaia-x-2511", "gaia-x", "https://w3id.org/gaia-x/2511#",
        ValidationType.SHACL, Map.of(), Map.of());
    TrustFrameworkBundle bundle = new TrustFrameworkBundle(config, null, null);
    when(registry.getAllBundles()).thenReturn(List.of(bundle));
    when(trustFrameworkService.isEnabled("gaia-x")).thenReturn(true);
    TrustFrameworkStartupLogger logger =
        new TrustFrameworkStartupLogger(registry, trustFrameworkService);

    logger.onApplicationEvent(event);

    assertThat(output.getAll()).doesNotContain("trust-framework 'gaia-x'");
  }

  @Test
  void onApplicationEvent_noBundlesRegistered_emitsNoLogs(CapturedOutput output) {

    when(registry.getAllBundles()).thenReturn(List.of());

    TrustFrameworkStartupLogger logger =
        new TrustFrameworkStartupLogger(registry, trustFrameworkService);

    logger.onApplicationEvent(event);

    assertThat(output.getAll()).doesNotContain("trust-framework");
  }
}
