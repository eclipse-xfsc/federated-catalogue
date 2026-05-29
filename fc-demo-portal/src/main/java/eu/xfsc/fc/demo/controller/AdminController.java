package eu.xfsc.fc.demo.controller;

import java.util.List;
import java.util.Map;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.xfsc.fc.api.generated.model.AdminHealthStatus;
import eu.xfsc.fc.api.generated.model.AdminStats;
import eu.xfsc.fc.api.generated.model.GraphDatabaseStatus;
import eu.xfsc.fc.api.generated.model.GraphDatabaseSwitchResult;
import eu.xfsc.fc.api.generated.model.KeycloakAdminUrl;
import eu.xfsc.fc.api.generated.model.OntologyImpactList;
import eu.xfsc.fc.api.generated.model.SchemaModulePatch;
import eu.xfsc.fc.api.generated.model.SchemaValidationStatus;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEntry;
import eu.xfsc.fc.api.generated.model.TrustFrameworkPatch;
import eu.xfsc.fc.api.generated.model.TrustFrameworkRolePatch;
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

  /**
   * Partially update a trust framework.
   */
  @PatchMapping("/trust-frameworks/{id}")
  public void patchTrustFramework(
      @PathVariable("id") String id,
      @RequestBody TrustFrameworkPatch patch,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.patchTrustFramework(id, patch, authorizedClient);
  }

  /**
   * Partially update the external client configuration of a trust framework bundle using
   * RFC 7396 merge-patch semantics.
   */
  @PatchMapping("/trust-frameworks/bundles/{bundleId}")
  public void patchTrustFrameworkBundleConfig(
      @PathVariable("bundleId") String bundleId,
      @RequestBody Map<String, Object> patch,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.patchTrustFrameworkBundleConfig(bundleId, patch, authorizedClient);
  }

  /**
   * Remove all persisted overrides for a trust framework bundle, restoring the bundle YAML
   * as the sole source of compliance configuration.
   */
  @DeleteMapping("/trust-frameworks/bundles/{bundleId}")
  public void deleteTrustFrameworkBundleConfig(
      @PathVariable("bundleId") String bundleId,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.deleteTrustFrameworkBundleConfig(bundleId, authorizedClient);
  }

  /**
   * Partially update a role within a trust framework bundle.
   */
  @PatchMapping("/trust-frameworks/{bundleId}/roles/{roleName}")
  public void patchTrustFrameworkRole(
      @PathVariable("bundleId") String bundleId,
      @PathVariable("roleName") String roleName,
      @RequestBody TrustFrameworkRolePatch patch,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.patchTrustFrameworkRole(bundleId, roleName, patch, authorizedClient);
  }

  // --- Schema Validation ---

  /** Get schema validation module status. */
  @GetMapping("/schema-validation")
  public SchemaValidationStatus getSchemaValidation(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.getSchemaValidation(authorizedClient);
  }

  /**
   * Partially update a schema validation module.
   */
  @PatchMapping("/schema-validation/modules/{type}")
  public void patchSchemaModule(
      @PathVariable("type") String type,
      @RequestBody SchemaModulePatch patch,
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    adminClient.patchSchemaModule(type, patch, authorizedClient);
  }

  /** List uploaded ontologies and their per-role subclass contributions. */
  @GetMapping("/schema-validation/ontologies")
  public OntologyImpactList getOntologyImpact(
      @RegisteredOAuth2AuthorizedClient("fc-client-oidc") OAuth2AuthorizedClient authorizedClient) {
    return adminClient.getOntologyImpact(authorizedClient);
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
