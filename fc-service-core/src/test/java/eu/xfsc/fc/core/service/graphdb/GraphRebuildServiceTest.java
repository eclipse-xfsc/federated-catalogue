package eu.xfsc.fc.core.service.graphdb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;

import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
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

/**
 * Tests for {@link GraphRebuildService} and {@link GraphRebuildProgress}.
 * Tests that do not require a full Spring context.
 */
public class GraphRebuildServiceTest {

  @Test
  public void triggerRebuild_disabledStore_throwsException() {
    DummyGraphStore dummyStore = new DummyGraphStore();
    GraphRebuildService service = new GraphRebuildService(null, null, dummyStore);

    assertThrows(GraphStoreDisabledException.class,
        () -> service.triggerRebuild(1, 0, 4, 100));
  }

  @Test
  public void idle_newInstance_isComplete() {
    GraphRebuildProgress idle = GraphRebuildProgress.idle();

    assertTrue(idle.isComplete());
    assertEquals(0, idle.getTotal());
    assertEquals(100, idle.getPercentComplete());
  }

  @Test
  public void incrementProcessed_multipleCalls_tracksProgress() {
    GraphRebuildProgress status = new GraphRebuildProgress(10);
    assertEquals(10, status.getTotal());
    assertEquals(0, status.getProcessedCount());
    assertEquals(0, status.getPercentComplete());
    assertFalse(status.isComplete());

    status.incrementProcessed();
    status.incrementProcessed();
    status.incrementProcessed();
    assertEquals(3, status.getProcessedCount());
    assertEquals(30, status.getPercentComplete());

    status.markComplete();
    assertTrue(status.isComplete());
    assertFalse(status.isFailed());
  }

  @Test
  public void markFailed_withMessage_setsErrorAndComplete() {
    GraphRebuildProgress status = new GraphRebuildProgress(5);
    status.markFailed("test error");

    assertTrue(status.isFailed());
    assertTrue(status.isComplete());
    assertEquals("test error", status.getErrorMessage());
  }

  @Test
  public void incrementErrors_multipleCalls_tracksErrorCount() {
    GraphRebuildProgress status = new GraphRebuildProgress(5);
    assertEquals(0, status.getErrorCount());

    status.incrementErrors();
    status.incrementErrors();
    assertEquals(2, status.getErrorCount());
  }

  @Test
  public void getDurationMs_newInstance_returnsNonNegative() {
    GraphRebuildProgress status = new GraphRebuildProgress(1);

    assertTrue(status.getDurationMs() >= 0);
  }

  @Test
  public void triggerRebuild_mixOfRdfAndNonRdfAssets_processedNeverExceedsTotal() throws InterruptedException {
    // `total` is sized from an RDF-only asset count, but GraphRebuilder walks every active
    // asset and silently skips non-RDF ones inside addAssetToGraph. If that skip did not also
    // suppress the progress tick, processed would overshoot total — the "24 / 10 assets · 240%"
    // regression this test pins end-to-end through the real GraphRebuildService/GraphRebuilder
    // wiring, not just the isolated counter class.
    AssetStore assetStore = mock(AssetStore.class);
    GraphStore graphStore = mock(GraphStore.class);
    ClaimExtractionService claimExtractionService = mock(ClaimExtractionService.class);
    ProtectedNamespaceFilter protectedNamespaceFilter = mock(ProtectedNamespaceFilter.class);
    AssetRepository assetRepository = mock(AssetRepository.class);
    ValidationResultStore validationResultStore = mock(ValidationResultStore.class);
    CredentialFormatDetector credentialFormatDetector = mock(CredentialFormatDetector.class);
    EnvelopedCredentialResolver envelopedCredentialResolver = mock(EnvelopedCredentialResolver.class);

    String rdfHash1 = "rdf-hash-1";
    String rdfHash2 = "rdf-hash-2";
    String nonRdfHash = "non-rdf-hash-1";

    AssetMetadata rdfAsset1 = mock(AssetMetadata.class);
    AssetMetadata rdfAsset2 = mock(AssetMetadata.class);
    AssetMetadata nonRdfAsset = mock(AssetMetadata.class);
    ContentAccessor content = mock(ContentAccessor.class);
    List<RdfClaim> claims = List.of(new RdfClaim("<s>", "<p>", "<o>"));

    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);
    when(rdfAsset1.getContentAccessor()).thenReturn(content);
    when(rdfAsset1.getId()).thenReturn("subject-1");
    when(rdfAsset1.getContentType()).thenReturn(VerificationConstants.MEDIA_TYPE_TURTLE);
    when(rdfAsset2.getContentAccessor()).thenReturn(content);
    when(rdfAsset2.getId()).thenReturn("subject-2");
    when(rdfAsset2.getContentType()).thenReturn(VerificationConstants.MEDIA_TYPE_TURTLE);
    when(nonRdfAsset.getContentAccessor()).thenReturn(null);

    when(assetStore.getByHash(rdfHash1)).thenReturn(rdfAsset1);
    when(assetStore.getByHash(rdfHash2)).thenReturn(rdfAsset2);
    when(assetStore.getByHash(nonRdfHash)).thenReturn(nonRdfAsset);
    when(assetStore.getActiveAssetHashes(any(), anyInt(), eq(1), eq(0)))
        .thenReturn(List.of(rdfHash1, rdfHash2, nonRdfHash), List.of());
    when(claimExtractionService.extractAllTriples(content)).thenReturn(claims);
    when(protectedNamespaceFilter.filterClaims(eq(claims), anyString()))
        .thenReturn(new FilteredClaims(claims, null));
    when(assetRepository.findByAssetTypeWithLink(any())).thenReturn(List.of());
    when(validationResultStore.findAll(any())).thenReturn(new PageImpl<>(List.of()));
    // `total` counts RDF-content assets only, i.e. 2 — matching the two RDF hashes above and
    // deliberately excluding the non-RDF one, even though all three get walked.
    when(assetStore.getByFilter(any(), eq(false), eq(false))).thenReturn(new PaginatedResults<>(2, List.of()));

    GraphRebuilder graphRebuilder = new GraphRebuilder(assetStore, graphStore, claimExtractionService,
        protectedNamespaceFilter, assetRepository, validationResultStore, credentialFormatDetector,
        envelopedCredentialResolver);
    GraphRebuildService service = new GraphRebuildService(graphRebuilder, assetStore, graphStore);

    assertTrue(service.triggerRebuild(1, 0, 1, 100));

    GraphRebuildProgress status = service.getStatus();
    long deadline = System.currentTimeMillis() + 5_000;
    while (!status.isComplete() && System.currentTimeMillis() < deadline) {
      Thread.sleep(20);
    }

    assertTrue(status.isComplete());
    assertFalse(status.isFailed(), "rebuild failed: " + status.getErrorMessage());
    assertEquals(2, status.getTotal());
    assertEquals(2, status.getProcessedCount());
    assertTrue(status.getProcessedCount() <= status.getTotal());
    assertEquals(100, status.getPercentComplete());
  }
}
