package eu.xfsc.fc.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for protected RDF namespace filtering.
 *
 * <p>The {@code namespace} value is the single source of truth for which namespace URI
 * is filtered at ingestion boundaries. Configured via
 * {@code federated-catalogue.verification.protected-namespace.namespace} in
 * {@code application.yml}.</p>
 *
 * <p>In production the values are set by {@code application.yml}
 * ({@code fc-service-server/src/main/resources}) and by each module's test
 * {@code application.yml}. The defaults below act as a safety net so the
 * namespace filter never silently becomes a no-op if the configuration entry
 * is accidentally removed or misspelled.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "federated-catalogue.verification.protected-namespace")
public class ProtectedNamespaceProperties {

  /**
   * Full namespace URI that is filtered at ingestion boundaries.
   * Overridden by {@code federated-catalogue.verification.protected-namespace.namespace}
   * in {@code application.yml}; default ensures filtering is always active.
   */
  private String namespace =
      "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#";

  /**
   * Short prefix used in log and warning messages (e.g. "fcmeta").
   * Overridden by {@code federated-catalogue.verification.protected-namespace.prefix}
   * in {@code application.yml}; not used in the actual filtering logic.
   */
  private String prefix = "fcmeta";
}
