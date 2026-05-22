package eu.xfsc.fc.core.config;

import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundleLoader;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link TrustFrameworkRegistry} bean populated from classpath bundles at boot,
 * with optional filesystem overrides applied on top.
 */
@Configuration
public class TrustFrameworkRegistryConfig {

  private final String overridePath;

  /**
   * Constructs the configuration with an optional filesystem override path for trust-framework bundles.
   *
   * @param overridePath path injected from {@code federated-catalogue.trust-frameworks.override-path};
   *                     blank means no filesystem override
   */
  public TrustFrameworkRegistryConfig(
      @Value("${federated-catalogue.trust-frameworks.override-path:}") String overridePath) {
    this.overridePath = overridePath;
  }

  @Bean
  public TrustFrameworkRegistry trustFrameworkRegistry() throws IOException {
    return new TrustFrameworkRegistry(new TrustFrameworkBundleLoader(overridePath).load());
  }
}
