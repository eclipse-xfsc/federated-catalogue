package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.xfsc.fc.api.generated.model.SwitchGraphDatabaseRequest;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigEntry;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.server.service.graphdb.RoutingGraphStore;

/**
 * Unit tests for the backend-switch path of {@link GraphAdminService}. Focused on the
 * order of persist-vs-swap — persisting a preference for a backend whose live swap then
 * fails would trap the next cold boot, so save must not happen unless the swap succeeded.
 */
@ExtendWith(MockitoExtension.class)
class GraphAdminSwitchTest {

  @Mock
  private GraphRebuildService graphRebuildService;
  @Mock
  private GraphStore graphStore;
  @Mock
  private AssetStore assetStore;
  @Mock
  private AdminConfigRepository adminConfigRepository;
  @Mock
  private GraphStoreProbe graphStoreProbe;
  @Mock
  private RoutingGraphStore routingGraphStore;

  private GraphAdminService service;

  @BeforeEach
  void setUp() {
    service = new GraphAdminService(graphRebuildService, graphStore, assetStore,
        adminConfigRepository, graphStoreProbe, routingGraphStore, 4, 100);
  }

  @Test
  void switchGraphDatabase_setActiveThrows_doesNotPersistPreference() {
    when(graphStoreProbe.probe(GraphBackendType.NEO4J))
        .thenReturn(GraphStoreProbe.Result.reachable("ok"));
    RuntimeException swapFailure = new IllegalArgumentException("No adapter for NEO4J");
    org.mockito.Mockito.doThrow(swapFailure).when(routingGraphStore).setActive(GraphBackendType.NEO4J);

    SwitchGraphDatabaseRequest request = new SwitchGraphDatabaseRequest();
    request.setBackend("NEO4J");

    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> service.switchGraphDatabase(request));
    assertEquals(swapFailure, thrown);

    verify(adminConfigRepository, never()).save(any(AdminConfigEntry.class));
  }
}
