package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import eu.xfsc.fc.api.generated.model.GraphStatus;
import eu.xfsc.fc.api.generated.model.RebuildStatus;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildProgress;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.assetstore.AssetStore;

/**
 * Unit tests for {@link GraphAdminService} business logic.
 * Covers sync assessment, rebuild conflict, and disabled backend scenarios.
 */
@ExtendWith(MockitoExtension.class)
class GraphAdminServiceTest {

  @Mock
  private GraphRebuildService graphRebuildService;

  @Mock
  private GraphStore graphStore;

  @Mock
  private AssetStore assetStore;

  private GraphAdminService service;

  @BeforeEach
  void setUp() {
    service = new GraphAdminService(graphRebuildService, graphStore, assetStore, 4, 100);
  }

  @Test
  void triggerGraphRebuild_started_returns202Accepted() {
    when(graphRebuildService.triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(true);
    when(graphRebuildService.getStatus())
        .thenReturn(GraphRebuildProgress.idle());

    ResponseEntity<RebuildStatus> response = service.triggerGraphRebuild();

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
  }

  @Test
  void triggerGraphRebuild_alreadyRunning_returns409Conflict() {
    when(graphRebuildService.triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt())).thenReturn(false);
    when(graphRebuildService.getStatus())
        .thenReturn(GraphRebuildProgress.idle());

    ResponseEntity<RebuildStatus> response = service.triggerGraphRebuild();

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
  }

  @Test
  void getGraphRebuildStatus_idle_returnsCompletedStatus() {
    when(graphRebuildService.getStatus())
        .thenReturn(GraphRebuildProgress.idle());
    when(graphRebuildService.isRunning()).thenReturn(false);

    ResponseEntity<RebuildStatus> response = service.getGraphRebuildStatus();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(0L, response.getBody().getTotal());
    assertEquals(true, response.getBody().getComplete());
    assertEquals(false, response.getBody().getRunning());
  }

  @Test
  void getGraphStatus_disabledBackend_returnsDisabledAssessment() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NONE);

    ResponseEntity<GraphStatus> response = service.getGraphStatus();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    GraphStatus body = response.getBody();
    assertEquals("NONE", body.getBackend());
    assertEquals(false, body.getEnabled());
    assertEquals(false, body.getHealthy());
    assertEquals("disabled", body.getSyncAssessment());
  }

  @Test
  void getGraphStatus_assetCountUnknown_returnsUnknownAssessment() {
    stubEnabledBackend(GraphBackendType.NEO4J, true);
    stubActiveAssetCount(5);
    when(graphStore.getRDFAssetCountInGraph()).thenReturn(-1L);

    GraphStatus body = service.getGraphStatus().getBody();

    assertEquals("unknown", body.getSyncAssessment());
  }

  @Test
  void getGraphStatus_bothEmpty_returnsEmptyAssessment() {
    stubEnabledBackend(GraphBackendType.NEO4J, true);
    stubActiveAssetCount(0);
    when(graphStore.getRDFAssetCountInGraph()).thenReturn(0L);

    GraphStatus body = service.getGraphStatus().getBody();

    assertEquals("empty", body.getSyncAssessment());
  }

  @Test
  void getGraphStatus_emptyGraphWithActiveAssets_returnsOutOfSync() {
    stubEnabledBackend(GraphBackendType.FUSEKI, true);
    stubActiveAssetCount(10);
    when(graphStore.getRDFAssetCountInGraph()).thenReturn(0L);

    GraphStatus body = service.getGraphStatus().getBody();

    assertEquals("out-of-sync", body.getSyncAssessment());
  }

  @Test
  void getGraphStatus_graphAssetsButNoActiveAssets_returnsOutOfSync() {
    stubEnabledBackend(GraphBackendType.NEO4J, true);
    stubActiveAssetCount(0);
    when(graphStore.getRDFAssetCountInGraph()).thenReturn(5L);

    GraphStatus body = service.getGraphStatus().getBody();

    assertEquals("out-of-sync", body.getSyncAssessment());
  }

  @Test
  void getGraphStatus_assetCountMatchesActiveAssets_returnsInSync() {
    stubEnabledBackend(GraphBackendType.NEO4J, true);
    stubActiveAssetCount(10);
    when(graphStore.getRDFAssetCountInGraph()).thenReturn(10L);

    GraphStatus body = service.getGraphStatus().getBody();

    assertEquals("in-sync", body.getSyncAssessment());
    assertEquals(true, body.getEnabled());
    assertEquals(true, body.getHealthy());
  }

  @Test
  void getGraphStatus_fewerAssetsInGraphThanActive_returnsOutOfSync() {
    stubEnabledBackend(GraphBackendType.NEO4J, true);
    stubActiveAssetCount(500);
    when(graphStore.getRDFAssetCountInGraph()).thenReturn(2L);

    GraphStatus body = service.getGraphStatus().getBody();

    assertEquals("out-of-sync", body.getSyncAssessment());
  }

  @Test
  void getGraphStatus_moreAssetsInGraphThanActive_returnsOutOfSync() {
    stubEnabledBackend(GraphBackendType.NEO4J, true);
    stubActiveAssetCount(10);
    when(graphStore.getRDFAssetCountInGraph()).thenReturn(25L);

    GraphStatus body = service.getGraphStatus().getBody();

    assertEquals("out-of-sync", body.getSyncAssessment());
  }

  // --- Helpers ---

  private void stubEnabledBackend(GraphBackendType type, boolean healthy) {
    when(graphStore.getBackendType()).thenReturn(type);
    when(graphStore.isHealthy()).thenReturn(healthy);
  }

  @SuppressWarnings("unchecked")
  private void stubActiveAssetCount(long count) {
    PaginatedResults<?> result = org.mockito.Mockito.mock(PaginatedResults.class);
    when(result.getTotalCount()).thenReturn(count);
    when(assetStore.getByFilter(any(AssetFilter.class), eq(false), eq(false)))
        .thenReturn((PaginatedResults) result);
  }
}
