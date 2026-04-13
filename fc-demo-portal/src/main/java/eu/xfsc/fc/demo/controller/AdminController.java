package eu.xfsc.fc.demo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import eu.xfsc.fc.api.generated.model.AdminHealthStatus;
import eu.xfsc.fc.api.generated.model.GraphDatabaseStatus;
import eu.xfsc.fc.api.generated.model.GraphDatabaseSwitchResult;
import eu.xfsc.fc.api.generated.model.AdminStats;
import eu.xfsc.fc.api.generated.model.KeycloakAdminUrl;
import eu.xfsc.fc.api.generated.model.SchemaValidationStatus;
import eu.xfsc.fc.api.generated.model.TrustFrameworkConfigUpdate;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEntry;
import eu.xfsc.fc.client.AdminClient;
import lombok.RequiredArgsConstructor;

/**
 * Proxy controller for Admin API.
 * Forwards requests from the demo portal to fc-service-server admin endpoints.
 */
@RestController
@RequestMapping("admin")
@RequiredArgsConstructor
public class AdminController {

  private final AdminClient adminClient;

  // --- Dashboard ---

  /** Check if the current user has admin access. */
  @GetMapping("/me")
  public void checkAdminAccess(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.checkAdminAccess(authorizedClient);
  }

  /** Get dashboard statistics. */
  @GetMapping("/stats")
  public AdminStats getAdminStats(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.getAdminStats(authorizedClient);
  }

  /** Get component health overview. */
  @GetMapping("/health")
  public AdminHealthStatus getAdminHealth(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.getAdminHealth(authorizedClient);
  }

  /** Get Keycloak Admin Console URL. */
  @GetMapping("/keycloak-url")
  public KeycloakAdminUrl getKeycloakAdminUrl(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.getKeycloakAdminUrl(authorizedClient);
  }

  // --- Trust Frameworks ---

  /** List all registered trust frameworks. */
  @GetMapping("/trust-frameworks")
  public List<TrustFrameworkEntry> getTrustFrameworks(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.getTrustFrameworks(authorizedClient);
  }

  /** Toggle a trust framework's enabled state. */
  @PutMapping("/trust-frameworks/{id}/enabled")
  public void setTrustFrameworkEnabled(
      @PathVariable("id") String id,
      @RequestParam("enabled") boolean enabled,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.setTrustFrameworkEnabled(id, enabled, authorizedClient);
  }

  /** Update a trust framework's configuration. */
  @PutMapping("/trust-frameworks/{id}")
  public void updateTrustFrameworkConfig(
      @PathVariable("id") String id,
      @RequestBody TrustFrameworkConfigUpdate config,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.updateTrustFrameworkConfig(id, config, authorizedClient);
  }

  // --- Schema Validation ---

  /** Get schema validation module status. */
  @GetMapping("/schema-validation")
  public SchemaValidationStatus getSchemaValidation(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.getSchemaValidation(authorizedClient);
  }

  /** Toggle a schema validation module. */
  @PutMapping("/schema-validation/modules/{type}")
  public void setSchemaModuleEnabled(
      @PathVariable("type") String type,
      @RequestParam("enabled") boolean enabled,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.setSchemaModuleEnabled(type, enabled, authorizedClient);
  }

  // --- Graph Database ---

  /** Get graph database status. */
  @GetMapping("/graph-database")
  public GraphDatabaseStatus getGraphDatabaseStatus(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.getGraphDatabaseStatus(authorizedClient);
  }

  /** Switch graph database backend. */
  @PostMapping("/graph-database/switch")
  public GraphDatabaseSwitchResult switchGraphDatabase(
      @RequestBody Map<String, String> body,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.switchGraphDatabase(body.get("backend"), authorizedClient);
  }
}
