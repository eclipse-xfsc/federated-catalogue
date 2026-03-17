package eu.xfsc.fc.core.service.graphdb;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.util.GraphRebuilder;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Wraps {@link GraphRebuilder} with async execution and rebuild status tracking.
 * Guards against concurrent rebuilds via an {@link AtomicBoolean} flag.
 */
@Slf4j
@Component
public class GraphRebuildService {

  private final GraphRebuilder graphRebuilder;
  private final AssetStore assetStore;
  private final GraphStore graphStore;
  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private final AtomicBoolean running = new AtomicBoolean(false);

  @Getter
  private volatile GraphRebuildProgress status = GraphRebuildProgress.idle();

  public GraphRebuildService(GraphRebuilder graphRebuilder, AssetStore assetStore,
                             GraphStore graphStore) {
    this.graphRebuilder = graphRebuilder;
    this.assetStore = assetStore;
    this.graphStore = graphStore;
  }

  /**
   * Triggers an async graph rebuild. Returns {@code true} if the rebuild was started,
   * {@code false} if a rebuild is already in progress.
   *
   * @param chunkCount total number of parallel rebuilders
   * @param chunkId 0-based index of this rebuilder
   * @param threads number of threads for the rebuild
   * @param batchSize number of hashes to fetch per batch
   * @return true if rebuild was started, false if already running
   * @throws GraphStoreDisabledException if the graph store backend is disabled
   */
  public boolean triggerRebuild(int chunkCount, int chunkId, int threads, int batchSize) {
    if (graphStore.getBackendType() == GraphBackendType.NONE) {
      throw new GraphStoreDisabledException("Graph store is disabled");
    }
    if (!running.compareAndSet(false, true)) {
      return false;
    }
    status = new GraphRebuildProgress(0);
    try {
      executor.submit(() -> {
        try {
          AssetFilter filter = new AssetFilter();
          filter.setStatuses(List.of(AssetStatus.ACTIVE));
          filter.setLimit(0);
          filter.setOffset(0);
          long total = assetStore.getByFilter(filter, false, false).getTotalCount();
          status.setTotal(total);
          graphRebuilder.rebuildGraphDb(chunkCount, chunkId, threads, batchSize,
              (count, error) -> {
                status.incrementProcessed();
                if (error != null) {
                  status.incrementErrors();
                }
              });
          status.markComplete();
        } catch (Exception e) {
          log.error("Graph rebuild failed", e);
          status.markFailed(e.getMessage());
        } finally {
          running.set(false);
        }
      });
    } catch (Exception e) {
      running.set(false);
      status.markFailed(e.getMessage());
      throw e;
    }
    return true;
  }

  /**
   * Returns whether a rebuild is currently running.
   *
   * @return true if a rebuild is in progress
   */
  public boolean isRunning() {
    return running.get();
  }

  /**
   * Shuts down the executor service on application shutdown.
   */
  @PreDestroy
  public void destroy() {
    executor.shutdownNow();
    try {
      if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
        log.warn("Rebuild executor did not terminate within timeout");
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }
}
