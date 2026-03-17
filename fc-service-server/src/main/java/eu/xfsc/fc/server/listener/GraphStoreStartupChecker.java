package eu.xfsc.fc.server.listener;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Checks the graph store state at application startup.
 * Detects empty graphs when active assets exist and optionally triggers auto-rebuild.
 */
@Slf4j
@Component
public class GraphStoreStartupChecker implements ApplicationListener<ApplicationReadyEvent> {

  private final GraphStore graphStore;
  private final AssetStore assetStore;
  private final GraphRebuildService graphRebuildService;
  private final boolean autoRebuildOnEmpty;
  private final int rebuildThreads;
  private final int rebuildBatchSize;

  public GraphStoreStartupChecker(GraphStore graphStore, AssetStore assetStore,
                                  GraphRebuildService graphRebuildService,
                                  @Value("${graphstore.auto-rebuild-on-empty:false}") boolean autoRebuildOnEmpty,
                                  @Value("${graphstore.rebuild-threads:4}") int rebuildThreads,
                                  @Value("${graphstore.rebuild-batch-size:100}") int rebuildBatchSize) {
    this.graphStore = graphStore;
    this.assetStore = assetStore;
    this.graphRebuildService = graphRebuildService;
    this.autoRebuildOnEmpty = autoRebuildOnEmpty;
    this.rebuildThreads = rebuildThreads;
    this.rebuildBatchSize = rebuildBatchSize;
  }

  /**
   * Checks graph store state once the application is ready. Logs a warning if the graph
   * is empty while active assets exist, and optionally triggers an auto-rebuild.
   *
   * @param event the application ready event
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    GraphBackendType backendType = graphStore.getBackendType();
    if (backendType == GraphBackendType.NONE) {
      log.info("Graph store backend is disabled (NONE), skipping startup check");
      return;
    }

    log.info("Checking graph store state for backend: {}", backendType);

    long claimCount = graphStore.getClaimCount();
    if (claimCount == -1) {
      log.warn("Graph store connectivity check failed, cannot determine graph state");
      return;
    }

    AssetFilter filter = new AssetFilter();
    filter.setStatuses(List.of(AssetStatus.ACTIVE));
    filter.setLimit(0);
    filter.setOffset(0);
    long activeAssetCount = assetStore.getByFilter(filter, false, false).getTotalCount();

    if (claimCount == 0 && activeAssetCount > 0) {
      log.warn("Graph store is empty but {} active assets exist in storage", activeAssetCount);
      if (autoRebuildOnEmpty) {
        log.info("Auto-rebuild is enabled, triggering graph rebuild");
        graphRebuildService.triggerRebuild(1, 0, rebuildThreads, rebuildBatchSize);
      }
    } else {
      log.info("Graph store state: {} claims, {} active assets", claimCount, activeAssetCount);
    }
  }
}