package eu.xfsc.fc.core.service.graphdb;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.dao.assets.ContentKind;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.util.GraphRebuilder;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

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
          status.setTotal(countRebuildableAssets());
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
   * Counts the assets a rebuild would process.
   *
   * <p>This is the {@code total} a rebuild reports, and it must select exactly the assets that tick
   * the progress callback: {@code addAssetToGraph} ticks for every walked asset that holds content
   * to extract claims from. Content kind is the wrong predicate for that — it records how an asset
   * was uploaded and is left unchanged by enrichment, so an asset uploaded as NON_RDF and later
   * enriched holds content and is processed while a content-kind filter excludes it, which is how
   * processed came to exceed total. The asset walk itself filters on status only.</p>
   *
   * <p>The predicate keeps a single definition here; {@link #countRebuildAssets()} reuses it when a
   * caller needs the same total broken down.</p>
   *
   * @return the number of active assets holding content
   */
  public long countRebuildableAssets() {
    return countActive(null, true);
  }

  /**
   * Counts the rebuildable set and its two parts in a single repeatable-read snapshot.
   *
   * <p>The parts are a partition of the whole: an asset holding content was either uploaded as a
   * credential or enriched afterwards. Callers therefore get numbers that add up. Deriving one part
   * by subtracting the other from the total, across separately timed counts, does not: an upload
   * landing between two reads makes the remainder wrong, and in the wrong order makes it negative.
   * The graph-backend claim count is deliberately left out — it is not a database read and cannot
   * join this snapshot.</p>
   *
   * @return the rebuildable total together with its credential and enriched parts
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public RebuildAssetCounts countRebuildAssets() {
    return new RebuildAssetCounts(
        countActive(List.of(ContentKind.RDF), null),
        countActive(null, true),
        countActive(List.of(ContentKind.NON_RDF), true));
  }

  /**
   * Counts active assets matching the given content predicate.
   *
   * @param contentKinds the content kinds to include, or {@code null} for any
   * @param hasContent whether the asset must hold content, or {@code null} for either
   * @return the number of matching active assets
   */
  private long countActive(List<ContentKind> contentKinds, Boolean hasContent) {
    AssetFilter filter = new AssetFilter();
    filter.setStatuses(List.of(AssetStatus.ACTIVE));
    filter.setContentKinds(contentKinds);
    filter.setHasContent(hasContent);
    // setLimit(0) means "no limit" and would run the data query unbounded alongside the COUNT.
    filter.setLimit(1);
    filter.setOffset(0);
    return assetStore.getByFilter(filter, false, false).getTotalCount();
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

  /**
   * The asset counts describing a rebuild, all read from one snapshot.
   *
   * @param rdfAssetCount active assets uploaded as credentials
   * @param rebuildableAssetCount active assets holding content, which a rebuild processes
   * @param enrichedAssetCount active assets uploaded as non-RDF that hold content from enrichment
   */
  public record RebuildAssetCounts(long rdfAssetCount, long rebuildableAssetCount,
                                   long enrichedAssetCount) {}
}
