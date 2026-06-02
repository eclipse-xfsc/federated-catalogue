package eu.xfsc.fc.core.util;

import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.pojo.AssetType;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;
import eu.xfsc.fc.core.service.verification.CredentialFormatDetector;
import eu.xfsc.fc.core.service.verification.EnvelopedCredentialResolver;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;

import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

/**
 * A set of tools to rebuild the graph store.
 */
@Slf4j
@AllArgsConstructor
@Component
public class GraphRebuilder {

  /**
   * The period to sleep while waiting for the queue to empty.
   */
  private static final int QUEUE_CLEAR_WAIT_INTERVAL = 100;

  private final AssetStore assetStore;
  private final GraphStore graphStore;
    private final ClaimExtractionService claimExtractionService;
  private final ProtectedNamespaceFilter protectedNamespaceFilter;
  private final AssetRepository assetRepository;
  private final ValidationResultStore validationResultStore;
  private final CredentialFormatDetector credentialFormatDetector;
  private final EnvelopedCredentialResolver envelopedCredentialResolver;

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
      boolean processed = false;
      try {
        processed = addAssetToGraph(hash);
      } catch (Exception e) {
        log.error("Failed to add asset {} to graph", hash, e);
        caught = e;
      } finally {
        pendingTasks.decrementAndGet();
        // Skip the progress tick for non-RDF assets that early-returned without
        // doing any work — they are not counted in `total` either, so ticking
        // here would push processed > total.
        if (progressCallback != null && (processed || caught != null)) {
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

    // Separate pass: restore link triples from persisted asset metadata records.
    // This must NOT be merged into addAssetToGraph() because non-RDF (human-readable) assets
    // have contentAccessor = null; calling extractClaims(null) would throw a NullPointerException.
    rebuildLinkTriples();

    // After asset claims are restored, rebuild validation result triples. The
    // progress callback contract (see Javadoc on the public overload) ticks once
    // per asset; reusing it here would push processed past total because the
    // rebuild status `total` only counts assets. Run silently — the
    // rebuildValidationResults info log emits a per-pass summary for operators.
    log.info("Rebuilding validation result triples...");
    rebuildValidationResults(null);
    log.info("Graph rebuild complete (assets + validation results)");
  }

  /**
   * Restores {@code fcmeta:hasHumanReadable} and {@code fcmeta:hasMachineReadable} triples
   * from stored asset metadata records.
   *
   * <p>Only machine-readable assets with a linked asset are fetched. One row is sufficient to
   * reconstruct both directions because {@link #writeLinkTriples} writes both triples.</p>
   */
  private void rebuildLinkTriples() {
    final var mrAssets = assetRepository.findByAssetTypeWithLink(AssetType.MACHINE_READABLE);
    log.info("rebuildLinkTriples; restoring triples for {} MR→HR asset pairs", mrAssets.size());
    for (var mr : mrAssets) {
      try {
        assetStore.writeAssetLinkTriples(mr.getSubjectId(), mr.getLinkedAsset().getSubjectId());
      } catch (Exception ex) {
        log.error("rebuildLinkTriples; failed to write triple for link {}->{}: {}",
            mr.getSubjectId(), mr.getLinkedAsset().getSubjectId(), ex.getMessage(), ex);
      }
    }
  }

  private void sleepForQueue() {
    try {
      Thread.sleep(QUEUE_CLEAR_WAIT_INTERVAL);
    } catch (InterruptedException ex) {
      log.error("Interrupted while waiting for graph rebuild queue to empty.");
    }
  }

  /**
   * Processes one asset hash. Returns true when claims were extracted and pushed to
   * the graph store, false when the asset was skipped (non-RDF — null contentAccessor).
   * The caller uses the return value to decide whether to tick the progress counter.
   */
  private boolean addAssetToGraph(String hash) throws Exception {
    AssetMetadata assetMetaData = assetStore.getByHash(hash);
    if (assetMetaData.getContentAccessor() == null) {
      return false;
    }
    List<RdfClaim> claims = extractClaims(assetMetaData);
    claims = protectedNamespaceFilter.filterClaims(claims, "graph rebuild").claims();
    // Remove any prior claims for this credential subject before re-adding so the
    // rebuild is idempotent — repeated rebuilds against the same asset set must
    // converge on the same graph state, not accumulate. Without this clear, RDF
    // backends silently grow on every rebuild because the per-claim RDF-star
    // annotations regenerate slightly different blank-node identifiers each pass,
    // and Neo4j hits its n10s.unique-uri constraint mid-import.
    graphStore.deleteClaims(assetMetaData.getId());
    graphStore.addClaims(claims, assetMetaData.getId());
    return true;
  }

  private List<RdfClaim> extractClaims(AssetMetadata assetMetaData) {
        String contentType = assetMetaData.getContentType();
        if (VerificationConstants.MEDIA_TYPE_NTRIPLES.equals(contentType)
                || VerificationConstants.MEDIA_TYPE_TURTLE.equals(contentType)
                || VerificationConstants.MEDIA_TYPE_RDF_XML.equals(contentType)) {
            return claimExtractionService.extractAllTriples(assetMetaData.getContentAccessor());
        }
    // Parity with the upload path: JWT-secured credentials must be decoded to JSON-LD
    // before claim extraction. Pure content decode — no signature or policy enforcement.
    ContentAccessor content = credentialFormatDetector.unwrapToJsonLd(assetMetaData.getContentAccessor());
    // VP payloads may embed inner credentials as EnvelopedVerifiableCredential entries
    // (data:application/vc+jwt,... URIs). Resolve them in place so the claim extractors
    // see the inner credentialSubject; the upload path does this in extractAndValidateClaims.
    content = envelopedCredentialResolver.resolveInnerEnvelopedCredentials(content);
    List<RdfClaim> claims = claimExtractionService.extractCredentialClaims(content);
        if (claims == null || claims.isEmpty()) {
            log.debug("extractClaims; credential extraction returned empty for {}, falling back to all-triples", contentType);
          return claimExtractionService.extractAllTriples(content);
        }
        return claims;
    }

  /**
   * Rebuilds validation result triples in the graph store from stored validation result records.
   *
   * <p>Iterates through all {@link ValidationResult} entities and re-projects their
   * {@code fcmeta:} triples to the graph store. Updates {@code graph_sync_status} to
   * {@code SYNCED} on success; leaves as {@code FAILED} if graph write fails.</p>
   *
   * <p>This pass runs after asset claim restoration to ensure validation result IRIs
   * can reference existing asset subjects.</p>
   *
   * @param progressCallback Optional callback for progress reporting (count, exception). May be null.
   */
  public void rebuildValidationResults(BiConsumer<Integer, Exception> progressCallback) {
    final int batchSize = 100;
    int pageNumber = 0;
    long totalProcessed = 0;
    long totalSucceeded = 0;
    long totalFailed = 0;

    Page<ValidationResult> page;
    do {
      page = validationResultStore.findAll(PageRequest.of(pageNumber++, batchSize));
      log.debug("rebuildValidationResults; processing page {} with {} results",
          pageNumber - 1, page.getNumberOfElements());

      for (ValidationResult result : page.getContent()) {
        Exception caught = null;
        try {
          validationResultStore.syncToGraph(result, graphStore);
          totalSucceeded++;
          log.debug("rebuildValidationResults; restored triples for validation result id={}", result.getId());
        } catch (Exception e) {
          log.error("rebuildValidationResults; failed to restore validation result id={}", result.getId(), e);
          totalFailed++;
          caught = e;
        } finally {
          totalProcessed++;
          if (progressCallback != null) {
            progressCallback.accept(1, caught);
          }
        }
      }
    } while (page.hasNext());

    log.info("rebuildValidationResults; complete. Processed: {}, Succeeded: {}, Failed: {}",
        totalProcessed, totalSucceeded, totalFailed);
  }

}
