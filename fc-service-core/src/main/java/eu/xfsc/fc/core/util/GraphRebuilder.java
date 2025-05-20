package eu.xfsc.fc.core.util;

import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.verification.VerificationService;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
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
  private SelfDescriptionStore sdStore;
  private final GraphStore graphStore;
  private final VerificationService verificationService;

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
    BlockingQueue<String> taskQueue = new ArrayBlockingQueue<>(batchSize);
    ExecutorService executorService = ProcessorUtils.createProcessors(threads, taskQueue, this::addSdToGraph, "GraphRebuilder");

    int lastCount;
    String lastHash = null;
    do {
      List<String> activeSdHashes = sdStore.getActiveSdHashes(lastHash, batchSize, chunkCount, chunkId);
      lastCount = activeSdHashes.size();
      log.info("Rebuilding GraphDB: Fetched {} Hashes", lastCount);
      if (lastCount > 0) {
        lastHash = activeSdHashes.get(activeSdHashes.size() - 1);
        for (String hash : activeSdHashes) {
          try {
            taskQueue.put(hash);
          } catch (InterruptedException ex) {
            log.warn("Interrupted while rebuilding the GraphDB, aborting.");
            lastCount = 0;
            taskQueue.clear();
          }
        }
      }
    } while (lastCount > 0);

    int openJobs = taskQueue.size();
    while (openJobs > 0) {
      log.debug("Waiting for {} jobs to be finished.", openJobs);
      sleepForQueue();
      openJobs = taskQueue.size();
    }

    // Sleep to give the last task a chance to complete.
    sleepForQueue();

    ProcessorUtils.shutdownProcessors(executorService, taskQueue, 10, TimeUnit.MINUTES);
  }

  private void sleepForQueue() {
    try {
      Thread.sleep(QUEUE_CLEAR_WAIT_INTERVAL);
    } catch (InterruptedException ex) {
      log.error("Interrupted while waiting for graph rebuild queue to empty.");
    }
  }

  private void addSdToGraph(String hash) {
    SelfDescriptionMetadata sdMetaData = sdStore.getByHash(hash);
    List<SdClaim> claims = verificationService.extractClaims(sdMetaData.getSelfDescription());
    graphStore.addClaims(claims, sdMetaData.getId());
  }

}
