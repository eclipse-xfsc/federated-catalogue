package eu.xfsc.fc.server.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configures a shared {@link WebClient} bean for outbound HTTP calls.
 */
@Configuration
@EnableConfigurationProperties(AdminDashboardProperties.class)
public class WebClientConfig {

  @Bean
  public WebClient webClient(WebClient.Builder builder) {
    return builder.build();
  }
}
