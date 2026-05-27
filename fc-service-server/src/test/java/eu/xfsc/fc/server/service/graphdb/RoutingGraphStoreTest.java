package eu.xfsc.fc.server.service.graphdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.graphdb.GraphStore;

/**
 * Unit tests for {@link RoutingGraphStore#setActive(GraphBackendType)}.
 *
 * <p>The runtime build always loads both Neo4j and Fuseki adapter modules
 * unconditionally so a backend switch can happen without a JVM restart. That makes
 * the "no adapter registered" branch of {@code setActive} unreachable via Spring
 * wiring alone, so it is verified here against an in-process instance that omits
 * the relevant adapter.
 */
class RoutingGraphStoreTest {

  private AdminConfigRepository adminConfigRepository;

  @BeforeEach
  void setUp() {
    adminConfigRepository = mock(AdminConfigRepository.class);
    when(adminConfigRepository.getValue(RoutingGraphStore.KEY_PREFERRED_BACKEND))
        .thenReturn(Optional.empty());
  }

  @Test
  void setActive_targetWithoutRegisteredAdapter_throwsClientException() {
    GraphStore fuseki = adapterReturning(GraphBackendType.FUSEKI);
    GraphStore none = adapterReturning(GraphBackendType.NONE);
    RoutingGraphStore routing = newRoutingStore(List.of(fuseki, none), "fuseki");

    assertThatThrownBy(() -> routing.setActive(GraphBackendType.NEO4J))
        .isInstanceOf(ClientException.class)
        .hasMessageContaining("NEO4J")
        .hasMessageContaining("no adapter registered");
  }

  @Test
  void setActive_targetWithoutRegisteredAdapter_leavesActiveBackendUnchanged() {
    GraphStore fuseki = adapterReturning(GraphBackendType.FUSEKI);
    GraphStore none = adapterReturning(GraphBackendType.NONE);
    RoutingGraphStore routing = newRoutingStore(List.of(fuseki, none), "fuseki");

    assertThat(routing.getBackendType()).isEqualTo(GraphBackendType.FUSEKI);

    assertThatThrownBy(() -> routing.setActive(GraphBackendType.NEO4J))
        .isInstanceOf(ClientException.class);

    assertThat(routing.getBackendType()).isEqualTo(GraphBackendType.FUSEKI);
  }

  @Test
  void setActive_targetWithRegisteredAdapter_swapsActiveBackend() {
    GraphStore fuseki = adapterReturning(GraphBackendType.FUSEKI);
    GraphStore none = adapterReturning(GraphBackendType.NONE);
    RoutingGraphStore routing = newRoutingStore(List.of(fuseki, none), "none");

    assertThat(routing.getBackendType()).isEqualTo(GraphBackendType.NONE);

    routing.setActive(GraphBackendType.FUSEKI);

    assertThat(routing.getBackendType()).isEqualTo(GraphBackendType.FUSEKI);
  }

  private RoutingGraphStore newRoutingStore(List<GraphStore> adapters, String envBackend) {
    RoutingGraphStore routing = new RoutingGraphStore(adapters, adminConfigRepository);
    ReflectionTestUtils.setField(routing, "envBackendName", envBackend);
    routing.initialize();
    return routing;
  }

  private static GraphStore adapterReturning(GraphBackendType type) {
    GraphStore stub = mock(GraphStore.class);
    when(stub.getBackendType()).thenReturn(type);
    return stub;
  }
}
