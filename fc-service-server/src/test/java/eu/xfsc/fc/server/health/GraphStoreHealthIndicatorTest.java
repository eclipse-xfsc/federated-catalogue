package eu.xfsc.fc.server.health;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.mockito.Mockito.when;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.graphdb.GraphStore;

/**
 * Unit tests for {@link GraphStoreHealthIndicator}.
 * Covers UP, DOWN, and disabled backend scenarios.
 */
@ExtendWith(MockitoExtension.class)
class GraphStoreHealthIndicatorTest {

  @Mock
  private GraphStore graphStore;

  @InjectMocks
  private GraphStoreHealthIndicator healthIndicator;

  @Test
  void health_disabledBackend_reportsUpWithDisabledStatus() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NONE);

    Health health = healthIndicator.health();

    assertEquals(Status.UP, health.getStatus());
    assertEquals("NONE", health.getDetails().get("backend"));
    assertEquals("disabled", health.getDetails().get("status"));
  }

  @Test
  void health_healthyNeo4jBackend_reportsUpWithQueryLanguage() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.isHealthy()).thenReturn(true);
    when(graphStore.getSupportedQueryLanguage()).thenReturn(Optional.of(QueryLanguage.OPENCYPHER));

    Health health = healthIndicator.health();

    assertEquals(Status.UP, health.getStatus());
    assertEquals("NEO4J", health.getDetails().get("backend"));
    assertEquals("OPENCYPHER", health.getDetails().get("queryLanguage"));
  }

  @Test
  void health_healthyFusekiBackend_reportsUpWithSparql() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.FUSEKI);
    when(graphStore.isHealthy()).thenReturn(true);
    when(graphStore.getSupportedQueryLanguage()).thenReturn(Optional.of(QueryLanguage.SPARQL));

    Health health = healthIndicator.health();

    assertEquals(Status.UP, health.getStatus());
    assertEquals("FUSEKI", health.getDetails().get("backend"));
    assertEquals("SPARQL", health.getDetails().get("queryLanguage"));
  }

  @Test
  void health_unhealthyBackend_reportsDownWithReason() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.isHealthy()).thenReturn(false);

    Health health = healthIndicator.health();

    assertEquals(Status.DOWN, health.getStatus());
    assertEquals("NEO4J", health.getDetails().get("backend"));
    assertEquals("Backend connectivity check failed", health.getDetails().get("reason"));
  }

  @Test
  void health_emptyQueryLanguage_reportsUnknown() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.isHealthy()).thenReturn(true);
    when(graphStore.getSupportedQueryLanguage()).thenReturn(Optional.empty());

    Health health = healthIndicator.health();

    assertEquals(Status.UP, health.getStatus());
    assertEquals("unknown", health.getDetails().get("queryLanguage"));
  }
}
