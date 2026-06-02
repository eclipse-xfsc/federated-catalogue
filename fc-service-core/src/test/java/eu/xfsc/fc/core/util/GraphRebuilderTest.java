package eu.xfsc.fc.core.util;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;
import eu.xfsc.fc.core.service.verification.CredentialFormatDetector;
import eu.xfsc.fc.core.service.verification.EnvelopedCredentialResolver;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;

/**
 * Unit tests for {@link GraphRebuilder} that pin the rebuild contract for a single asset:
 * idempotent per-subject overwrite (deleteClaims before addClaims) and validation-result
 * pass invoked with a null progress callback so the asset-progress counter cannot overshoot
 * {@code total}.
 */
@ExtendWith(MockitoExtension.class)
class GraphRebuilderTest {

  private static final String ASSET_HASH = "test-hash-1";
  private static final String SUBJECT_ID = "did:web:test-subject.example.org";

  @Mock private AssetStore assetStore;
  @Mock private GraphStore graphStore;
  @Mock private ClaimExtractionService claimExtractionService;
  @Mock private ProtectedNamespaceFilter protectedNamespaceFilter;
  @Mock private AssetRepository assetRepository;
  @Mock private ValidationResultStore validationResultStore;
  @Mock private CredentialFormatDetector credentialFormatDetector;
  @Mock private EnvelopedCredentialResolver envelopedCredentialResolver;
  @Mock private AssetMetadata assetMetadata;
  @Mock private ContentAccessor contentAccessor;

  @InjectMocks private GraphRebuilder graphRebuilder;

  private List<RdfClaim> claims;

  @BeforeEach
  void setUp() {
    claims = List.of(new RdfClaim(
        "<" + SUBJECT_ID + ">",
        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
        "<https://w3id.org/gaia-x/2511#Participant>"));

    // Asset fetch path — one asset on the first page, then empty so the rebuild loop exits.
    // Stubbed four times to cover two full rebuild invocations (each rebuild calls
    // getActiveAssetHashes once with the asset and once to detect end-of-stream).
    lenient().when(assetStore.getActiveAssetHashes(any(), eq(100), eq(1), eq(0)))
        .thenReturn(List.of(ASSET_HASH), List.of(), List.of(ASSET_HASH), List.of());
    lenient().when(assetStore.getByHash(ASSET_HASH)).thenReturn(assetMetadata);
    lenient().when(assetMetadata.getContentAccessor()).thenReturn(contentAccessor);
    lenient().when(assetMetadata.getId()).thenReturn(SUBJECT_ID);
    lenient().when(assetMetadata.getContentType()).thenReturn(VerificationConstants.MEDIA_TYPE_TURTLE);

    // Claim extraction path — turtle goes through extractAllTriples directly (no unwrap).
    lenient().when(claimExtractionService.extractAllTriples(contentAccessor)).thenReturn(claims);
    lenient().when(protectedNamespaceFilter.filterClaims(eq(claims), anyString()))
        .thenReturn(new FilteredClaims(claims, null));

    // Link triples + validation results passes are no-ops for this fixture.
    lenient().when(assetRepository.findByAssetTypeWithLink(any())).thenReturn(List.of());
    Page<ValidationResult> emptyPage = new PageImpl<>(List.of());
    lenient().when(validationResultStore.findAll(any())).thenReturn(emptyPage);
  }

  @Test
  void rebuildGraphDb_singleAsset_deleteClaimsCalledBeforeAddClaims() {
    graphRebuilder.rebuildGraphDb(1, 0, 1, 100);

    InOrder ordered = inOrder(graphStore);
    ordered.verify(graphStore).deleteClaims(SUBJECT_ID);
    ordered.verify(graphStore).addClaims(claims, SUBJECT_ID);
  }

  @Test
  void rebuildGraphDb_runTwice_perSubjectDeleteHappensOnEveryPass() {
    graphRebuilder.rebuildGraphDb(1, 0, 1, 100);
    graphRebuilder.rebuildGraphDb(1, 0, 1, 100);

    // Two passes, each does delete+add for the same subject. If the deleteClaims call were
    // accidentally removed, the rebuild would silently accumulate claims across passes — the
    // demo-day regression this contract prevents.
    verify(graphStore, times(2)).deleteClaims(SUBJECT_ID);
    verify(graphStore, times(2)).addClaims(claims, SUBJECT_ID);
  }

  @Test
  void rebuildGraphDb_validationResultPassDoesNotIncrementAssetProgressCounter() {
    // The validation-result pass receives a null progressCallback so its per-result ticks
    // cannot leak into the asset-progress counter (which is sized to assets only and would
    // otherwise overshoot 100% — observed historically as "24 / 10 assets · 240% · done").
    // Concretely: only `addClaims` (asset path) should be invoked here; the validation-
    // result pass over an empty page does no writes.
    graphRebuilder.rebuildGraphDb(1, 0, 1, 100);

    verify(graphStore, times(1)).addClaims(anyList(), eq(SUBJECT_ID));
    verify(graphStore, never()).addClaims(anyList(), eq("not-a-real-subject"));
  }
}
