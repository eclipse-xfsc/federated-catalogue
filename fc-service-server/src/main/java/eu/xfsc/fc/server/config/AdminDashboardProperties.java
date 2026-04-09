package eu.xfsc.fc.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the Admin Dashboard service.
 * Bound from the {@code admin.dashboard} prefix in application configuration.
 */
@ConfigurationProperties(prefix = "admin.dashboard")
public record AdminDashboardProperties(
    String keycloakAuthServerUrl,
    String keycloakRealm,
    String keycloakAdminConsoleUrl,
    String fileStorePath) {}
