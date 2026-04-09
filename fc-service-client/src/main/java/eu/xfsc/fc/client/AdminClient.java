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
import eu.xfsc.fc.api.generated.model.SchemaValidationStatus;
import eu.xfsc.fc.api.generated.model.TrustFrameworkConfigUpdate;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEntry;

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

    /** Toggles a trust framework's enabled state. */
    public void setTrustFrameworkEnabled(String id, boolean enabled,
        OAuth2AuthorizedClient authorizedClient) {
        doPut("/admin/trust-frameworks/{id}/enabled", "",
            Map.of("id", id), Map.of("enabled", enabled), Void.class, authorizedClient);
    }

    /** Updates a trust framework's configuration. */
    public void updateTrustFrameworkConfig(String id, TrustFrameworkConfigUpdate config,
        OAuth2AuthorizedClient authorizedClient) {
        doPut("/admin/trust-frameworks/{id}", config,
            Map.of("id", id), null, Void.class, authorizedClient);
    }

    /** Gets schema validation module status. */
    public SchemaValidationStatus getSchemaValidation(OAuth2AuthorizedClient authorizedClient) {
        return doGet("/admin/schema-validation", Map.of(), null,
            SchemaValidationStatus.class, authorizedClient);
    }

    /** Toggles a schema validation module. */
    public void setSchemaModuleEnabled(String type, boolean enabled,
        OAuth2AuthorizedClient authorizedClient) {
        doPut("/admin/schema-validation/modules/{type}", "",
            Map.of("type", type), Map.of("enabled", enabled), Void.class, authorizedClient);
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
