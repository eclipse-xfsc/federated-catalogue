package eu.xfsc.fc.server.listener;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.context.event.ApplicationReadyEvent;

import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.assetstore.AssetStore;

/**
 * Unit tests for {@link GraphStoreStartupChecker}.
 * Verifies startup behavior for each graph state scenario using mocks.
 */
@ExtendWith(MockitoExtension.class)
class GraphStoreStartupCheckerTest {

  @Mock
  private GraphStore graphStore;

  @Mock
  private AssetStore assetStore;

  @Mock
  private GraphRebuildService graphRebuildService;

  @Mock
  private ApplicationReadyEvent event;

  private GraphStoreStartupChecker startupChecker;

  @BeforeEach
  void setUp() {
    startupChecker = new GraphStoreStartupChecker(
        graphStore, assetStore, graphRebuildService, false, 4, 100);
  }

  @Test
  void onApplicationEvent_disabledBackend_skipsCheckEntirely() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NONE);

    startupChecker.onApplicationEvent(event);

    verify(graphStore, never()).getClaimCount();
    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_connectivityFailure_skipsRebuild() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.getClaimCount()).thenReturn(-1L);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_emptyGraphWithActiveAssets_logsWarningWithoutRebuild() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.getClaimCount()).thenReturn(0L);
    stubActiveAssetCount(5);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_emptyGraphWithActiveAssetsAndAutoRebuild_triggersRebuild() {
    startupChecker = new GraphStoreStartupChecker(
        graphStore, assetStore, graphRebuildService, true, 4, 100);
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.getClaimCount()).thenReturn(0L);
    stubActiveAssetCount(5);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService).triggerRebuild(eq(1), eq(0), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_populatedGraph_doesNotTriggerRebuild() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.FUSEKI);
    when(graphStore.getClaimCount()).thenReturn(10L);
    stubActiveAssetCount(5);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_emptyGraphAndNoActiveAssets_doesNotTriggerRebuild() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.getClaimCount()).thenReturn(0L);
    stubActiveAssetCount(0);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @SuppressWarnings("unchecked")
  private void stubActiveAssetCount(long count) {
    var result = org.mockito.Mockito.mock(
        eu.xfsc.fc.core.pojo.PaginatedResults.class);
    when(result.getTotalCount()).thenReturn(count);
    when(assetStore.getByFilter(any(AssetFilter.class), eq(false), eq(false))).thenReturn(result);
  }
}
