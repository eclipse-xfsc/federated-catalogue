package eu.xfsc.fc.server.health;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.Optional;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.graphdb.GraphStore;

/**
 * Spring Boot health indicator for the graph store backend.
 * Reports the backend type and its health status.
 */
@Component
@RequiredArgsConstructor
public class GraphStoreHealthIndicator implements HealthIndicator {

  private final GraphStore graphStore;

  /**
   * Returns the health of the graph store backend. Reports UP with "disabled" status
   * when the backend is NONE, DOWN when connectivity fails, or UP with query language details.
   *
   * @return the graph store health
   */
  @Override
  public Health health() {
    GraphBackendType backendType = graphStore.getBackendType();
    if (backendType == GraphBackendType.NONE) {
      return Health.up()
          .withDetail("backend", backendType.name())
          .withDetail("status", "disabled")
          .build();
    }
    if (!graphStore.isHealthy()) {
      return Health.down()
          .withDetail("backend", backendType.name())
          .withDetail("reason", "Backend connectivity check failed")
          .build();
    }
    Optional<QueryLanguage> queryLanguage = graphStore.getSupportedQueryLanguage();
    return Health.up()
        .withDetail("backend", backendType.name())
        .withDetail("queryLanguage", queryLanguage.map(QueryLanguage::name).orElse("unknown"))
        .build();
  }
}
