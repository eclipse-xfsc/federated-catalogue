package eu.xfsc.fc.core.service.graphdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.dao.assets.ContentKind;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;
import eu.xfsc.fc.core.service.verification.CredentialFormatDetector;
import eu.xfsc.fc.core.service.verification.EnvelopedCredentialResolver;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;
import eu.xfsc.fc.core.util.GraphRebuilder;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

/**
 * Pins the invariant that the graph-rebuild progress counter cannot overshoot: every asset that
 * increments {@code processed} must also be counted in {@code total}.
 *
 * <p>Unlike a test that stubs {@code getByFilter} with a fixed number, the asset store here derives
 * the total from the {@link AssetFilter} it is handed, and lists active hashes without regard to
 * content kind — mirroring what the production SQL does. That is what makes a divergence between
 * the counting predicate and the walk predicate observable: a hard-coded total is only ever
 * asserted against itself.</p>
 *
 * <p>The asset that exposes the divergence is one enriched after upload. Enrichment writes RDF
 * content into a non-RDF asset without changing its content kind, so it is skipped by a
 * content-kind-based count, walked by a status-only walk, and ticked by a
 * content-presence-based progress callback.</p>
 */
public class GraphRebuildProgressOvershootTest {

  private static final String RDF_HASH_1 = "hash-rdf-1";
  private static final String RDF_HASH_2 = "hash-rdf-2";
  private static final String ENRICHED_NON_RDF_HASH = "hash-enriched-non-rdf";
  private static final int SINGLE_CHUNK = 1;
  private static final int FIRST_CHUNK_ID = 0;
  private static final int SINGLE_THREAD = 1;
  private static final int BATCH_SIZE = 100;
  private static final long COMPLETION_TIMEOUT_MS = 10_000L;
  private static final long POLL_INTERVAL_MS = 20L;
  private static final int EXPECTED_PROCESSED = 3;

  /**
   * One active asset as the fake store sees it: its content kind, and whether it currently holds
   * content to extract claims from.
   *
   * @param hash        the asset hash the rebuild walk pages over
   * @param contentKind the stored content kind, unchanged by enrichment
   * @param hasContent  whether a content accessor is available
   */
  private record StoredAsset(String hash, ContentKind contentKind, boolean hasContent) {}

  @Test
  public void triggerRebuild_enrichedNonRdfAsset_processedNeverExceedsTotal()
      throws InterruptedException {
    Map<String, StoredAsset> assets = new LinkedHashMap<>();
    assets.put(RDF_HASH_1, new StoredAsset(RDF_HASH_1, ContentKind.RDF, true));
    assets.put(RDF_HASH_2, new StoredAsset(RDF_HASH_2, ContentKind.RDF, true));
    // Uploaded as non-RDF, then enriched: saveEnrichedContent populates content and leaves
    // contentKind at NON_RDF.
    assets.put(ENRICHED_NON_RDF_HASH,
        new StoredAsset(ENRICHED_NON_RDF_HASH, ContentKind.NON_RDF, true));

    GraphRebuildService service = buildService(assets);

    assertTrue(service.triggerRebuild(SINGLE_CHUNK, FIRST_CHUNK_ID, SINGLE_THREAD, BATCH_SIZE),
        "precondition: no other rebuild may be in progress");
    GraphRebuildProgress status = awaitCompletion(service);

    assertEquals(EXPECTED_PROCESSED, status.getProcessedCount(),
        "the walk filters on status only, so every active asset with content is processed");
    assertTrue(status.getProcessedCount() <= status.getTotal(),
        "every asset that ticks the counter must also be counted in total — total="
            + status.getTotal() + ", processed=" + status.getProcessedCount());
    assertTrue(status.getPercentComplete() <= 100,
        "reported progress must never exceed 100% — actual: " + status.getPercentComplete() + "%");
  }

  @Test
  public void getPercentComplete_processedExceedsTotal_isCappedAtHundred() {
    GraphRebuildProgress progress = new GraphRebuildProgress(9);

    for (int i = 0; i < 10; i++) {
      progress.incrementProcessed();
    }

    assertTrue(progress.getPercentComplete() <= 100,
        "a counter that overshot its total must still report at most 100% — the \"10 of 9 assets\""
            + " symptom; actual: " + progress.getPercentComplete() + "%");
  }

  /**
   * Wires a rebuild service over an asset store that answers from the given asset set, honouring
   * the filter it is passed rather than returning a fixed count.
   *
   * @param assets the active assets the fake store holds, keyed by hash
   * @return a rebuild service ready to trigger
   */
  private GraphRebuildService buildService(Map<String, StoredAsset> assets) {
    AssetStore assetStore = mock(AssetStore.class);
    GraphStore graphStore = mock(GraphStore.class);
    ClaimExtractionServiceStubs stubs = new ClaimExtractionServiceStubs();

    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);

    // The production count query applies the filter's content-kind clause; the production walk
    // (findHashesFirstPage / findHashesAfter) filters on status and hash chunk only.
    when(assetStore.getByFilter(any(), eq(false), eq(false))).thenAnswer(invocation -> {
      AssetFilter filter = invocation.getArgument(0);
      long count = assets.values().stream().filter(asset -> matchesFilter(filter, asset)).count();
      return new PaginatedResults<>(count, List.of());
    });
    when(assetStore.getActiveAssetHashes(any(), eq(BATCH_SIZE), eq(SINGLE_CHUNK), eq(FIRST_CHUNK_ID)))
        .thenAnswer(invocation -> {
          String afterHash = invocation.getArgument(0);
          return afterHash == null ? List.copyOf(assets.keySet()) : List.of();
        });
    when(assetStore.getByHash(anyString())).thenAnswer(invocation ->
        metadataFor(assets.get(invocation.<String>getArgument(0)), stubs.content));

    GraphRebuilder rebuilder = new GraphRebuilder(assetStore, graphStore,
        stubs.claimExtractionService, stubs.protectedNamespaceFilter, stubs.assetRepository,
        stubs.validationResultStore, stubs.credentialFormatDetector,
        stubs.envelopedCredentialResolver);
    return new GraphRebuildService(rebuilder, assetStore, graphStore);
  }

  /**
   * Applies the filter clauses this test exercises, as the count query would.
   *
   * @param filter the filter handed to the store
   * @param asset  the candidate asset
   * @return whether the asset is counted
   */
  private static boolean matchesFilter(AssetFilter filter, StoredAsset asset) {
    if (filter.getContentKinds() != null && !filter.getContentKinds().contains(asset.contentKind())) {
      return false;
    }
    return filter.getStatuses() == null || filter.getStatuses().contains(AssetStatus.ACTIVE);
  }

  /**
   * Builds the metadata the rebuilder sees for one stored asset.
   *
   * @param asset   the stored asset, never {@code null} for a hash the walk returned
   * @param content the content accessor handed to assets that hold content
   * @return metadata whose content accessor reflects whether content is present
   */
  private static AssetMetadata metadataFor(StoredAsset asset, ContentAccessor content) {
    AssetMetadata metadata = mock(AssetMetadata.class);
    when(metadata.getContentAccessor()).thenReturn(asset.hasContent() ? content : null);
    when(metadata.getId()).thenReturn("subject-" + asset.hash());
    when(metadata.getContentType()).thenReturn(VerificationConstants.MEDIA_TYPE_TURTLE);
    return metadata;
  }

  /**
   * Awaits rebuild completion.
   *
   * @param service the service whose rebuild is running
   * @return the final progress
   * @throws InterruptedException if the wait is interrupted
   */
  private static GraphRebuildProgress awaitCompletion(GraphRebuildService service)
      throws InterruptedException {
    GraphRebuildProgress status = service.getStatus();
    long deadline = System.currentTimeMillis() + COMPLETION_TIMEOUT_MS;
    while (!status.isComplete() && System.currentTimeMillis() < deadline) {
      Thread.sleep(POLL_INTERVAL_MS);
    }
    assertTrue(status.isComplete(), "rebuild did not finish within " + COMPLETION_TIMEOUT_MS + "ms");
    return status;
  }

  /** Collaborators the rebuilder needs but whose behaviour this test does not vary. */
  private static final class ClaimExtractionServiceStubs {
    private final ContentAccessor content = mock(ContentAccessor.class);
    private final ClaimExtractionService claimExtractionService = mock(ClaimExtractionService.class);
    private final ProtectedNamespaceFilter protectedNamespaceFilter =
        mock(ProtectedNamespaceFilter.class);
    private final AssetRepository assetRepository = mock(AssetRepository.class);
    private final ValidationResultStore validationResultStore = mock(ValidationResultStore.class);
    private final CredentialFormatDetector credentialFormatDetector =
        mock(CredentialFormatDetector.class);
    private final EnvelopedCredentialResolver envelopedCredentialResolver =
        mock(EnvelopedCredentialResolver.class);

    private ClaimExtractionServiceStubs() {
      List<RdfClaim> claims = List.of(new RdfClaim("<s>", "<p>", "<o>"));
      when(claimExtractionService.extractAllTriples(content)).thenReturn(claims);
      when(protectedNamespaceFilter.filterClaims(eq(claims), anyString()))
          .thenReturn(new FilteredClaims(claims, null));
      when(assetRepository.findByAssetTypeWithLink(any())).thenReturn(List.of());
      when(validationResultStore.findAll(any())).thenReturn(new PageImpl<>(List.of()));
    }
  }
}
