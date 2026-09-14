package eu.xfsc.fc.server.listener;

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
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;

/**
 * Unit tests for {@link GraphStoreStartupChecker}.
 * Verifies startup behavior for each graph state scenario using mocks.
 */
@ExtendWith(MockitoExtension.class)
class GraphStoreStartupCheckerTest {

  private static final int AUTO_REBUILD_THREADS = 4;
  private static final int AUTO_REBUILD_BATCH_SIZE = 100;
  private static final boolean AUTO_REBUILD_ENABLED = true;
  private static final boolean AUTO_REBUILD_DISABLED = false;

  @Mock
  private GraphStore graphStore;

  @Mock
  private GraphRebuildService graphRebuildService;

  @Mock
  private ApplicationReadyEvent event;

  private GraphStoreStartupChecker startupChecker;

  @BeforeEach
  void setUp() {
    startupChecker = buildChecker(AUTO_REBUILD_DISABLED);
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

    verify(graphRebuildService, never()).countRebuildableAssets();
    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_emptyGraphWithRebuildableAssets_logsWarningWithoutRebuild() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.getClaimCount()).thenReturn(0L);
    when(graphRebuildService.countRebuildableAssets()).thenReturn(5L);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_emptyGraphWithRebuildableAssetsAndAutoRebuild_triggersRebuild() {
    startupChecker = buildChecker(AUTO_REBUILD_ENABLED);
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.getClaimCount()).thenReturn(0L);
    when(graphRebuildService.countRebuildableAssets()).thenReturn(5L);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService).triggerRebuild(eq(1), eq(0), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_emptyGraphWithOnlyEnrichedAssetsAndAutoRebuild_triggersRebuild() {
    // The catalogue holds nothing uploaded as a credential; its only indexable content arrived
    // through enrichment, which leaves content kind at NON_RDF. Counting by content kind reports
    // zero here, so the gate stayed shut and the graph was left empty until an operator noticed.
    startupChecker = buildChecker(AUTO_REBUILD_ENABLED);
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.FUSEKI);
    when(graphStore.getClaimCount()).thenReturn(0L);
    when(graphRebuildService.countRebuildableAssets()).thenReturn(1L);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService).triggerRebuild(eq(1), eq(0), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_populatedGraph_doesNotTriggerRebuild() {
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.FUSEKI);
    when(graphStore.getClaimCount()).thenReturn(10L);
    when(graphRebuildService.countRebuildableAssets()).thenReturn(5L);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  @Test
  void onApplicationEvent_emptyGraphAndNoRebuildableAssets_doesNotTriggerRebuild() {
    startupChecker = buildChecker(AUTO_REBUILD_ENABLED);
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(graphStore.getClaimCount()).thenReturn(0L);
    when(graphRebuildService.countRebuildableAssets()).thenReturn(0L);

    startupChecker.onApplicationEvent(event);

    verify(graphRebuildService, never()).triggerRebuild(anyInt(), anyInt(), anyInt(), anyInt());
  }

  private GraphStoreStartupChecker buildChecker(boolean autoRebuildOnEmpty) {
    return new GraphStoreStartupChecker(graphStore, graphRebuildService, autoRebuildOnEmpty,
        AUTO_REBUILD_THREADS, AUTO_REBUILD_BATCH_SIZE);
  }
}
