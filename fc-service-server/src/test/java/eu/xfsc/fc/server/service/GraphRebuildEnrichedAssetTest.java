package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildProgress;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Proves against a real backend that a rebuild counts and processes an enriched non-RDF asset,
 * and that its claims are genuinely extractable under production content-type conditions.
 *
 * <p>The unit-level {@code GraphRebuildProgressOvershootTest} pins the counting invariant with a
 * fake store, but stubs claim extraction and forces an RDF content type on every asset. That hides
 * the condition an enriched asset actually meets: {@code storeUnverified} persists the upload's own
 * MIME type and {@code saveEnrichedContent} writes only {@code content} and {@code changeComment},
 * never {@code content_type}. So a real enriched asset keeps its non-RDF type and takes the
 * credential-unwrap fallback in {@code GraphRebuilder#extractClaims}, not the direct-RDF branch.</p>
 *
 * <p>This class runs the whole path — upload, enrich, empty the graph, rebuild — against embedded
 * PostgreSQL and the real Fuseki adapter, and asserts both that the counter is consistent and that
 * the enrichment triples come back out of the store.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.EMBEDDED)
class GraphRebuildEnrichedAssetTest {

  private static final String TEST_ISSUER = "http://example.org/rebuild-enriched-test-issuer";
  private static final String NON_RDF_CONTENT_TYPE = "text/plain";
  private static final String ENRICHMENT_CONTENT_TYPE = "application/ld+json";
  private static final String ENRICHMENT_TITLE_PREDICATE = "http://example.org/title";
  private static final String CRED_SUBJECT_URI =
      "https://www.w3.org/2018/credentials#credentialSubject";
  private static final int SINGLE_CHUNK = 1;
  private static final int FIRST_CHUNK_ID = 0;
  private static final int SINGLE_THREAD = 1;
  private static final int BATCH_SIZE = 100;
  private static final long COMPLETION_TIMEOUT_MS = 30_000L;
  private static final long POLL_INTERVAL_MS = 50L;

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;
  @Autowired
  private GraphStore graphStore;
  @Autowired
  private GraphRebuildService graphRebuildService;

  private MockMvc mockMvc;

  private final List<String> touchedSubjects = new ArrayList<>();

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    assetStore.clear();
    touchedSubjects.forEach(graphStore::deleteClaims);
    touchedSubjects.clear();
  }

  @Test
  void graphStoreBean_fusekiConfigured_reportsFusekiBackend() {
    assertEquals(GraphBackendType.FUSEKI, graphStore.getBackendType(),
        "this class must be wired to the real Fuseki adapter (graphstore.impl=fuseki);"
            + " GraphBackendType.NONE here would mean every assertion below is silently"
            + " exercising the disabled store");
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void triggerRebuild_enrichedNonRdfAsset_isCountedProcessedAndItsClaimsRestored() throws Exception {
    final Asset created = uploadNonRdfAsset("rebuild original content " + UUID.randomUUID());
    final String titleValue = "Rebuild-restored enrichment title";
    final AssetEnrichmentResponse enrichment =
        enrichAsset(created.getId(), enrichmentPayload(created.getId(), titleValue));
    assertTrue(enrichment.getTriplesAdded() > 0, "precondition: enrichment must add triples");

    // The asset kept its upload content type: enrichment writes content, never content_type.
    assertEquals(NON_RDF_CONTENT_TYPE, assetStore.getById(created.getId()).getContentType(),
        "precondition: an enriched asset keeps its non-RDF content type, which is what forces"
            + " extractClaims down the credential-unwrap fallback rather than the direct-RDF branch");

    // Empty the graph so the rebuild has work to do and the readback proves restoration.
    graphStore.deleteClaims(created.getId());
    assertTrue(queryClaimsBySubject(created.getId()).isEmpty(),
        "precondition: the graph must be empty for this subject before the rebuild");

    final GraphRebuildProgress status = runRebuild();

    assertFalse(status.isFailed(), "rebuild failed: " + status.getErrorMessage());
    assertEquals(1L, status.getTotal(),
        "the enriched asset holds content, so a content-based count includes it — a content-kind"
            + " count would report 0 here while still processing 1");
    assertEquals(status.getTotal(), status.getProcessedCount(),
        "the counting predicate must select exactly the assets that tick");
    assertEquals(100, status.getPercentComplete(), "a fully processed rebuild reports 100%");

    assertTrue(titleTripleQueryable(created.getId(), titleValue),
        "the enrichment triple must be back in the graph after the rebuild — proving the asset was"
            + " not merely counted and ticked but genuinely re-indexed through the fallback path;"
            + " rows returned: " + queryClaimsBySubject(created.getId()));
  }

  /**
   * Triggers a rebuild and waits for it to finish.
   *
   * @return the final progress
   * @throws InterruptedException if the wait is interrupted
   */
  private GraphRebuildProgress runRebuild() throws InterruptedException {
    assertTrue(
        graphRebuildService.triggerRebuild(SINGLE_CHUNK, FIRST_CHUNK_ID, SINGLE_THREAD, BATCH_SIZE),
        "precondition: no other rebuild may be in progress");
    final GraphRebuildProgress status = graphRebuildService.getStatus();
    final long deadline = System.currentTimeMillis() + COMPLETION_TIMEOUT_MS;
    while (!status.isComplete() && !status.isFailed()
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(POLL_INTERVAL_MS);
    }
    assertTrue(status.isComplete() || status.isFailed(),
        "rebuild did not finish within " + COMPLETION_TIMEOUT_MS + "ms");
    return status;
  }

  private Asset uploadNonRdfAsset(String text) throws Exception {
    final MockMultipartFile file = new MockMultipartFile("file", "standalone.txt",
        NON_RDF_CONTENT_TYPE, text.getBytes(StandardCharsets.UTF_8));

    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    final Asset asset =
        objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    touchedSubjects.add(asset.getId());
    return asset;
  }

  private AssetEnrichmentResponse enrichAsset(String assetId, String rdfPayload) throws Exception {
    final MockMultipartFile file = new MockMultipartFile("file", "metadata.jsonld",
        ENRICHMENT_CONTENT_TYPE, rdfPayload.getBytes(StandardCharsets.UTF_8));

    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    touchedSubjects.add(assetId);
    return objectMapper.readValue(result.getResponse().getContentAsString(),
        AssetEnrichmentResponse.class);
  }

  private static String enrichmentPayload(String subjectId, String titleValue) {
    return """
        {
          "@context": {"ex": "http://example.org/"},
          "@id": "%s",
          "ex:title": "%s"
        }
        """.formatted(subjectId, titleValue);
  }

  /**
   * Queries the real graph store for triples recorded under the given enrichment subject, using
   * the SPARQL-star annotation pattern the production write path tags each triple with.
   *
   * @param subjectId the enriched asset's id
   * @return each matching triple as a row with {@code s}/{@code p}/{@code o} bindings
   */
  private List<Map<String, Object>> queryClaimsBySubject(String subjectId) {
    final String sparql = "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> <" + CRED_SUBJECT_URI + "> <"
        + subjectId + "> }";
    return graphStore.queryData(
        new GraphQuery(sparql, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false)
    ).getResults();
  }

  private boolean titleTripleQueryable(String subjectId, String titleValue) {
    return queryClaimsBySubject(subjectId).stream().anyMatch(row ->
        subjectId.equals(row.get("s"))
            && ENRICHMENT_TITLE_PREDICATE.equals(row.get("p"))
            && titleValue.equals(row.get("o")));
  }
}
