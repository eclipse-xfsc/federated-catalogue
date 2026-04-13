package eu.xfsc.fc.server.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration holder for Admin Dashboard service properties.
 * Groups Keycloak, file store, and WebClient config to reduce constructor parameters.
 */
@Getter
@Component
@RequiredArgsConstructor
public class AdminDashboardConfig {

  private final WebClient webClient;
  private final AdminDashboardProperties props;

  private String keycloakIssuerUrl;
  private String keycloakAdminConsoleUrl;
  private String fileStorePath;

  @PostConstruct
  private void init() {
    keycloakIssuerUrl = props.keycloakAuthServerUrl() + "/realms/" + props.keycloakRealm();
    String override = props.keycloakAdminConsoleUrl();
    keycloakAdminConsoleUrl = (override == null || override.isBlank())
        ? props.keycloakAuthServerUrl() + "/admin/master/console/#/" + props.keycloakRealm()
        : override;
    fileStorePath = props.fileStorePath();
  }
}
