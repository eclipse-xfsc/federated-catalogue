package eu.xfsc.fc.server.config;

import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines Keycloak client configurations.
 */
@Configuration
public class KeycloakConfig {
  @Value("${keycloak.auth-server-url}")
  private String serverUrl;
  @Value("${keycloak.realm}")
  private String realm;
  @Value("${keycloak.resource}")
  private String clientId;
  @Value("${keycloak.credentials.secret}")
  private String clientSecret;

  /**
   * The Keycloak Client bean config.
   */
  @Bean
  public Keycloak getKeycloakClient() {
    return KeycloakBuilder.builder()
      .serverUrl(serverUrl).realm(realm).grantType(OAuth2Constants.CLIENT_CREDENTIALS)
      .clientId(clientId).clientSecret(clientSecret)
      .resteasyClient(new ResteasyClientBuilderImpl().connectionPoolSize(10).build())
      .build();
  }
}