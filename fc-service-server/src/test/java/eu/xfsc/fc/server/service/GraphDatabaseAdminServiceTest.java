package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.xfsc.fc.api.generated.model.SwitchGraphDatabaseRequest;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigEntry;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.server.service.graphdb.RoutingGraphStore;

/**
 * Unit tests for {@link GraphDatabaseAdminService}. Focused on the order of
 * persist-vs-swap on the switch path — persisting a preference for a backend whose
 * live swap then fails would trap the next cold boot, so save must not happen unless
 * the swap succeeded.
 */
@ExtendWith(MockitoExtension.class)
class GraphDatabaseAdminServiceTest {

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

  @InjectMocks
  private GraphDatabaseAdminService service;

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
