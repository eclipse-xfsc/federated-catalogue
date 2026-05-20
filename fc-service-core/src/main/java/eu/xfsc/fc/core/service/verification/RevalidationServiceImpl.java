package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.dao.revalidator.RevalidatorChunksDao;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.util.ProcessorUtils;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

/**
 * Revalidates all active assets against the composite schema.
 *
 * <p>The background sweep is gated by the SHACL module toggle: when SHACL is
 * administratively disabled, {@link #handleTask} skips per-asset validation rather than
 * revoke previously-conforming assets. When the admin flips SHACL back on, the toggle
 * write path calls {@link #startValidating} to reset every chunk's {@code lastcheck} time
 * so the next manager cycle re-sweeps assets that were skipped while SHACL was off.
 */
@Slf4j
@RequiredArgsConstructor
public class RevalidationServiceImpl implements RevalidationService {

  private static final String REVALIDATOR_THREAD_NAME = "revalidator";
  private static final String MANAGER_THREAD_NAME = "revalidationManager";

  /**
   * The number of worker threads to use for revalidating assets.
   */
  @Value("${federated-catalogue.revalidation-service.worker-count:5}")
  private int workerCount;

  /**
   * The number of hashes to fetch at a time.
   */
  @Value("${federated-catalogue.revalidation-service.batch-size:100}")
  private int batchSize;

  /**
   * The time (in ms) to sleep between checks for changes.
   */
  @Value("${federated-catalogue.revalidation-service.sleeptime:1000}")
  private int managerSleepTime;

  /**
   * The total number of parallel instances of the catalogue that are running.
   */
  @Value("${federated-catalogue.instance-count:3}")
  private int instanceCount;

  private final RevalidatorChunksDao dao;
  private final AssetStore assetStorePublisher;
  private final SchemaValidationService schemaValidationService;
  private final SchemaModuleConfigService schemaModuleConfigService;

  private BlockingQueue<String> taskQueue;
  private ExecutorService executorService;
  private Thread managementThread;

  /**
   * Set to true by requesters, set to false by the manager when done processing.
   */
  private int workingOnChunk = -1;
  /**
   * Set to true by requesters, set to false by the manager when the precessing is restarted.
   */
  private AtomicBoolean restart = new AtomicBoolean(false);
  /**
   * Set to true by requesters.
   */
  private AtomicBoolean shutdown = new AtomicBoolean(false);

  public int getInstanceCount() {
    return instanceCount;
  }

  public void setInstanceCount(int instanceCount) {
    this.instanceCount = instanceCount;
  }

  public void setWorkerCount(int workerCount) {
    this.workerCount = workerCount;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  // Package-private for unit testing the SHACL-toggle gate without spinning up the
  // executor / manager thread pair. Worker threads call this via method-reference.
  void handleTask(final String assetHash) {
    if (!schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)) {
      // SHACL is administratively disabled. Skip this task — do not revoke. The chunk's
      // lastcheck advance at pickup leaves the asset recorded as "checked"; the admin
      // write path that re-enables SHACL calls startValidating() to reset chunk times
      // and re-sweep assets that were skipped while SHACL was off.
      log.debug("handleTask; SHACL module disabled — skipping asset {}", assetHash);
      return;
    }
    ContentAccessor content = assetStorePublisher.getFileByHash(assetHash);
    try {
      schemaValidationService.validateCredentialAgainstCompositeSchema(content);
    } catch (VerificationException ex) {
      log.info("Asset {} is no longer valid", assetHash);
      assetStorePublisher.changeLifeCycleStatus(assetHash, AssetStatus.REVOKED);
    }
    final var finalTaskQueue = taskQueue;
    if (finalTaskQueue != null && finalTaskQueue.size() < 0.5 * batchSize) {
      notifyManager();
    }
  }

  private void manage() {
    log.info("Revalidation manager starting.");
    String lastHash = null;
    boolean sleepAfter = true;
    while (!shutdown.get()) {
      if (restart.get()) {
        log.info("Processing revalidation restart.");
        restart.set(false);
        workingOnChunk = -1;
        taskQueue.clear();
      }
      if (workingOnChunk < 0) {
        workingOnChunk = dao.findChunkForWork(SchemaStore.SchemaType.SHAPE.name());
      }
      if (workingOnChunk >= 0) {
        if (taskQueue.size() < 0.5 * batchSize) {
          // Fetch more hashes.
          List<String> activeAssetHashes = assetStorePublisher.getActiveAssetHashes(lastHash, batchSize, instanceCount, workingOnChunk);
          if (activeAssetHashes.isEmpty()) {
            log.info("Finished revalidating.");
            workingOnChunk = -1;
            lastHash = null;
          } else {
            taskQueue.addAll(activeAssetHashes);
            lastHash = activeAssetHashes.get(activeAssetHashes.size() - 1);
            log.debug("Added {} hashes for chunk {} of {}. Queue now: {}", activeAssetHashes.size(), workingOnChunk, instanceCount, taskQueue.size());
          }
        }
      }
      if (sleepAfter && managerSleepTime > 0) {
        synchronized (managementThread) {
          try {
            managementThread.wait(managerSleepTime);
          } catch (InterruptedException ex) {
            log.warn("Revalidation manager was interrupted.");
          }
        }
      }
    }
    log.info("Revalidation manager exiting.");
  }

  /**
   * Starts the revalidation process when it is not started yet, restarts the process when it is already running. It
   * does this by resetting the times on the chunk table to 2000-01-01T00:00:00Z
   */
  @Override
  public void startValidating() {
    if (taskQueue == null) {
      setup();
    }
    log.debug("Sending Start signal to revalidation manager.");
    dao.resetChunkTableTimes();
    restart.set(true);
    notifyManager();
  }

  @Override
  public boolean isWorking() {
    return workingOnChunk >= 0;
  }

  private void notifyManager() {
    final Thread localManagementThread = managementThread;
    if (localManagementThread == null) {
      return;
    }
    synchronized (localManagementThread) {
      managementThread.notify();
    }
  }

  /**
   * Sets up the RevalidationService so it is ready for work. This does not actually start the revalidation process yet.
   */
  @Override
  public synchronized void setup() {
    shutdown.set(false);
    if (taskQueue != null) {
      return;
    }
    dao.checkChunkTable(instanceCount);
    taskQueue = new ArrayBlockingQueue<>(batchSize * 2);
    executorService = ProcessorUtils.createProcessors(workerCount, taskQueue, this::handleTask, REVALIDATOR_THREAD_NAME);
    managementThread = new Thread(this::manage, MANAGER_THREAD_NAME);
    managementThread.start();
  }

  /**
   * Clean up the revalidationService. If there are running tasks they will complete, but any queued tasks will not.
   */
  @Override
  public synchronized void cleanup() {
    shutdown.set(true);
    if (managementThread == null) {
      return;
    }
    notifyManager();
    ProcessorUtils.shutdownProcessors(executorService, taskQueue, 10, TimeUnit.SECONDS);
    taskQueue = null;
    executorService = null;
    managementThread = null;
  }

}
