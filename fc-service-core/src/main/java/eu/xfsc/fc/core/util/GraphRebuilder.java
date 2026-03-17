package eu.xfsc.fc.core.util;

import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationService;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A set of tools to rebuild the graph db.
 */
@Slf4j
@AllArgsConstructor
@Component
public class GraphRebuilder {

  /**
   * The period to sleep while waiting for the queue to empty.
   */
  private static final int QUEUE_CLEAR_WAIT_INTERVAL = 100;

  @Autowired
  private AssetStore assetStore;
  private final GraphStore graphStore;
  private final VerificationService verificationService;
  private final ProtectedNamespaceFilter protectedNamespaceFilter;

  /**
   * Starts rebuilding the graphDb, blocking until finished or interrupted.
   *
   * @param chunkCount The total number of parallel GraphRebuilders. If the re-build is done from a single instance,
   * this should be 1.
   * @param chunkId The (0-based) index of this GraphRebuilders. If the re-build is done from a single instance, this
   * should be 0.
   * @param threads The number of threads to use to rebuild the graph.
   * @param batchSize The number of Hashes to fetch from the database at the same time.
   */
  public void rebuildGraphDb(int chunkCount, int chunkId, int threads, int batchSize) {
    rebuildGraphDb(chunkCount, chunkId, threads, batchSize, null);
  }

  /**
   * Starts rebuilding the graphDb, blocking until finished or interrupted.
   * Reports progress via the provided callback.
   *
   * @param chunkCount The total number of parallel GraphRebuilders.
   * @param chunkId The (0-based) index of this GraphRebuilder.
   * @param threads The number of threads to use to rebuild the graph.
   * @param batchSize The number of Hashes to fetch from the database at the same time.
   * @param progressCallback Called for each asset processed: (1, null) on success, (1, exception) on failure. May be null.
   */
  public void rebuildGraphDb(int chunkCount, int chunkId, int threads, int batchSize,
                             BiConsumer<Integer, Exception> progressCallback) {
    BlockingQueue<String> taskQueue = new ArrayBlockingQueue<>(batchSize);
    AtomicInteger pendingTasks = new AtomicInteger(0);
    ExecutorService executorService = ProcessorUtils.createProcessors(threads, taskQueue, hash -> {
      Exception caught = null;
      try {
        addAssetToGraph(hash);
      } catch (Exception e) {
        log.error("Failed to add asset {} to graph", hash, e);
        caught = e;
      } finally {
        pendingTasks.decrementAndGet();
        if (progressCallback != null) {
          progressCallback.accept(1, caught);
        }
      }
    }, "GraphRebuilder");

    int lastCount;
    String lastHash = null;
    do {
      List<String> activeAssetHashes = assetStore.getActiveAssetHashes(lastHash, batchSize, chunkCount, chunkId);
      lastCount = activeAssetHashes.size();
      log.info("Rebuilding GraphDB: Fetched {} Hashes", lastCount);
      if (lastCount > 0) {
        lastHash = activeAssetHashes.getLast();
        for (String hash : activeAssetHashes) {
          try {
            pendingTasks.incrementAndGet();
            taskQueue.put(hash);
          } catch (InterruptedException ex) {
            log.warn("Interrupted while rebuilding the GraphDB, aborting.");
            lastCount = 0;
            taskQueue.clear();
            pendingTasks.decrementAndGet();
          }
        }
      }
    } while (lastCount > 0);

    while (pendingTasks.get() > 0) {
      log.debug("Waiting for {} pending jobs to be finished.", pendingTasks.get());
      sleepForQueue();
    }

    ProcessorUtils.shutdownProcessors(executorService, taskQueue, 10, TimeUnit.MINUTES);
  }

  private void sleepForQueue() {
    try {
      Thread.sleep(QUEUE_CLEAR_WAIT_INTERVAL);
    } catch (InterruptedException ex) {
      log.error("Interrupted while waiting for graph rebuild queue to empty.");
    }
  }

  private void addAssetToGraph(String hash) {
    AssetMetadata assetMetaData = assetStore.getByHash(hash);
    List<CredentialClaim> claims = verificationService.extractClaims(assetMetaData.getContentAccessor());
    claims = protectedNamespaceFilter.filterClaims(claims, "graph rebuild").claims();
    graphStore.addClaims(claims, assetMetaData.getId());
  }

}
