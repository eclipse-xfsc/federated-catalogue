package eu.xfsc.fc.core.config;

import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundleLoader;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;

import java.io.IOException;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link TrustFrameworkRegistry} bean populated from classpath bundles at boot.
 */
@Configuration
public class TrustFrameworkRegistryConfig {

  @Bean
  public TrustFrameworkRegistry trustFrameworkRegistry() throws IOException {
    return new TrustFrameworkRegistry(new TrustFrameworkBundleLoader().loadFromClasspath());
  }
}
