package eu.xfsc.fc.client;

import java.util.List;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.reactive.function.client.WebClient;

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

/**
 * Client for Admin API endpoints.
 */
public class AdminClient extends ServiceClient {

    public AdminClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public AdminClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public void checkAdminAccess(OAuth2AuthorizedClient authorizedClient) {
        doGet("/admin/me", Map.of(), null, Void.class, authorizedClient);
    }

    public AdminStats getAdminStats(OAuth2AuthorizedClient authorizedClient) {
        return doGet("/admin/stats", Map.of(), null, AdminStats.class, authorizedClient);
    }

    public AdminHealthStatus getAdminHealth(OAuth2AuthorizedClient authorizedClient) {
        return doGet("/admin/health", Map.of(), null, AdminHealthStatus.class, authorizedClient);
    }

    /** Gets the Keycloak Admin Console URL for iframe embedding. */
    public KeycloakAdminUrl getKeycloakAdminUrl(OAuth2AuthorizedClient authorizedClient) {
        return doGet("/admin/keycloak-url", Map.of(), null, KeycloakAdminUrl.class, authorizedClient);
    }

    /** Lists all registered trust frameworks. */
    public List<TrustFrameworkEntry> getTrustFrameworks(OAuth2AuthorizedClient authorizedClient) {
        return doGet("/admin/trust-frameworks", Map.of(), null,
            new ParameterizedTypeReference<List<TrustFrameworkEntry>>(){},
            authorizedClient);
    }

  /**
   * Applies a merge-patch to the identified trust framework. Only fields present in the patch
   * are modified; absent fields are left unchanged.
   *
   * @param id               trust framework family identifier
   * @param patch            fields to update
   * @param authorizedClient OAuth2 client for bearer token
   */
  public void patchTrustFramework(String id, TrustFrameworkPatch patch,
        OAuth2AuthorizedClient authorizedClient) {
        doPatch("/admin/trust-frameworks/{id}", patch, Map.of("id", id), Void.class, authorizedClient);
  }

  /** Gets schema validation module status. */
    public SchemaValidationStatus getSchemaValidation(OAuth2AuthorizedClient authorizedClient) {
        return doGet("/admin/schema-validation", Map.of(), null,
            SchemaValidationStatus.class, authorizedClient);
    }

    /**
     * Applies a merge-patch to the identified schema validation module. Only fields present in
     * the patch are modified; absent fields are left unchanged.
     *
     * @param type             module type (SHACL, JSON_SCHEMA, XML_SCHEMA, OWL)
     * @param patch            fields to update
     * @param authorizedClient OAuth2 client for bearer token
     */
    public void patchSchemaModule(String type, SchemaModulePatch patch,
                                  OAuth2AuthorizedClient authorizedClient) {
        doPatch("/admin/schema-validation/modules/{type}", patch,
            Map.of("type", type), Void.class, authorizedClient);
    }

  /** Lists uploaded ontologies and their per-role subclass contributions. */
    public OntologyImpactList getOntologyImpact(OAuth2AuthorizedClient authorizedClient) {
        return doGet("/admin/schema-validation/ontologies", Map.of(), null,
            OntologyImpactList.class, authorizedClient);
    }

    // --- Graph Database ---

    /** Gets graph database status. */
    public GraphDatabaseStatus getGraphDatabaseStatus(OAuth2AuthorizedClient authorizedClient) {
        return doGet("/admin/graph-database", Map.of(), null,
            GraphDatabaseStatus.class, authorizedClient);
    }

    /** Requests a graph database backend switch. */
    public GraphDatabaseSwitchResult switchGraphDatabase(String backend,
        OAuth2AuthorizedClient authorizedClient) {
        return doPost("/admin/graph-database/switch",
            Map.of("backend", backend), Map.of(), null,
            GraphDatabaseSwitchResult.class, authorizedClient);
    }
}
