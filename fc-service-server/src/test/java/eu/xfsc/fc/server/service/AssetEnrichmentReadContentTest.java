package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.core.util.HashUtils.calculateSha256AsHex;
import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.api.generated.model.AssetResult;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.Assets;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.server.service.graphdb.RoutingGraphStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests reproducing the content-swap defect on {@code GET /assets/{id}}: after a
 * non-RDF asset receives one or more metadata enrichments, the read path must keep returning the
 * original asset content with metadata that is internally consistent (reported file size and hash
 * must describe the content that is actually returned).
 *
 * <p>The graph store bean is replaced with a mock so the enrichment write path can be exercised
 * end-to-end over HTTP without a real Fuseki/Neo4j backend; only {@link GraphStore#getBackendType()}
 * is stubbed to report an enabled backend, so the enrichment code path is not short-circuited by the
 * disabled-backend guard. Enrichment content and metadata consistency is this class's concern; it is
 * deliberately decoupled from graph-store correctness (see {@link AssetEnrichmentGraphStoreTest}).</p>
 *
 * <p>AC-3 — that enrichment triples genuinely reach and remain retrievable from the graph store, not
 * merely that they are forwarded to a mock — is verified against a real embedded Fuseki backend in
 * {@link AssetEnrichmentGraphStoreTest}, which also re-covers this class's original-content
 * assertion for a single enrichment against that real backend. A mocked "was addClaims() called"
 * check was previously kept here as a stand-in for that AC; it added no coverage beyond what the
 * real-backend test now proves more strongly (that the triples survive and are actually queryable),
 * so it was removed rather than kept as a duplicate, weaker assertion of the same requirement.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AssetEnrichmentReadContentTest {

  private static final String TEST_ISSUER = "http://example.org/enrichment-content-test-issuer";
  private static final String ORIGINAL_CONTENT = "original standalone asset content, unchanged by enrichment";
  private static final String NON_RDF_CONTENT_TYPE = "text/plain";
  private static final String ENRICHMENT_CONTENT_TYPE = "application/ld+json";
  private static final String RDF_ASSET_FILE_NAME = "default-credential.json";
  private static final String RDF_ASSET_ID = "did:web:example.org:enrichment-content-test-rdf-asset";

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;
  @Autowired
  private VerificationService verificationService;
  @Autowired
  @Qualifier("assetFileStore")
  private FileStore assetFileStore;

  // Mocked as the concrete type: other beans (e.g. the graph-admin service) are wired against
  // RoutingGraphStore specifically, not the GraphStore interface, so a GraphStore-typed mock
  // would fail bean type resolution even though RoutingGraphStore implements GraphStore.
  @MockitoBean
  private RoutingGraphStore graphStore;

  private MockMvc mockMvc;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @BeforeEach
  void stubGraphStoreEnabled() {
    // Report an enabled backend so the enrichment path isn't short-circuited by the
    // disabled-graph-store guard (that behaviour is covered separately). addClaims/deleteClaims
    // remain unconfigured no-op mocks; AC-3 verifies their invocation directly.
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.FUSEKI);
  }

  @AfterEach
  void cleanUp() {
    assetStore.clear();
  }

  // ===== AC-1: original content survives enrichment =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_afterSingleEnrichment_returnsOriginalContentUnchanged() throws Exception {
    final Asset created = uploadNonRdfAsset(ORIGINAL_CONTENT);

    enrichAsset(created.getId(), enrichmentPayload(created.getId(), "First enrichment title"));

    final Map<String, Object> returned = readAssetById(created.getId());

    assertEquals(ORIGINAL_CONTENT, returned.get("rawContent"),
        "GET /assets/{id} must keep returning the original asset content after enrichment,"
            + " not the enrichment document");
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_afterSeveralSuccessiveEnrichments_returnsOriginalContentUnchanged() throws Exception {
    final Asset created = uploadNonRdfAsset(ORIGINAL_CONTENT);

    enrichAsset(created.getId(), enrichmentPayload(created.getId(), "Enrichment round one"));
    enrichAsset(created.getId(), enrichmentPayload(created.getId(), "Enrichment round two"));
    enrichAsset(created.getId(), enrichmentPayload(created.getId(), "Enrichment round three"));

    final Map<String, Object> returned = readAssetById(created.getId());

    assertEquals(ORIGINAL_CONTENT, returned.get("rawContent"),
        "GET /assets/{id} must return the original content after several successive enrichments,"
            + " not the most recent enrichment document");
  }

  // ===== AC-2: reported metadata must describe the content actually returned =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_afterEnrichment_reportedFileSizeMatchesActualContentLength() throws Exception {
    final Asset created = uploadNonRdfAsset(ORIGINAL_CONTENT);

    enrichAsset(created.getId(), enrichmentPayload(created.getId(), "Size consistency check"));

    final Map<String, Object> returned = readAssetById(created.getId());
    final String rawContent = (String) returned.get("rawContent");
    final long reportedFileSize = ((Number) returned.get("fileSize")).longValue();
    final int actualContentLength = rawContent == null
        ? 0 : rawContent.getBytes(StandardCharsets.UTF_8).length;

    assertEquals(actualContentLength, reportedFileSize,
        "reported fileSize (" + reportedFileSize + ") must equal the actual length of the"
            + " returned content (" + actualContentLength + "); actual rawContent was: " + rawContent);
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_afterEnrichment_reportedHashMatchesActualContentHash() throws Exception {
    final Asset created = uploadNonRdfAsset(ORIGINAL_CONTENT);

    enrichAsset(created.getId(), enrichmentPayload(created.getId(), "Hash consistency check"));

    final Map<String, Object> returned = readAssetById(created.getId());
    final String rawContent = (String) returned.get("rawContent");
    final String reportedHash = (String) returned.get("assetHash");
    final String actualContentHash = calculateSha256AsHex(
        (rawContent == null ? "" : rawContent).getBytes(StandardCharsets.UTF_8));

    assertEquals(actualContentHash, reportedHash,
        "reported assetHash must be the hash of the content actually returned in the body,"
            + " not the hash of the enrichment document; actual rawContent was: " + rawContent);
  }

  // ===== Boundary case: no enrichment applied must be unaffected =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_withoutEnrichment_metadataAndContentUnaffected() throws Exception {
    final Asset created = uploadNonRdfAsset(ORIGINAL_CONTENT);

    final Map<String, Object> returned = readAssetById(created.getId());

    assertEquals(created.getAssetHash(), returned.get("assetHash"));
    assertEquals(created.getFileSize(), ((Number) returned.get("fileSize")).longValue());
    assertNull(returned.get("rawContent"),
        "A non-RDF asset that was never enriched must not expose enrichment content it never received");
  }

  // ===== Fault handling: a single-asset read must fail loudly, not silently =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_withUnreadableFile_returnsServerError() throws Exception {
    final Asset created = uploadNonRdfAsset(ORIGINAL_CONTENT);
    enrichAsset(created.getId(), enrichmentPayload(created.getId(), "Single-asset fault path"));
    assetFileStore.deleteFile(created.getAssetHash());

    // A missing file-store entry must fail loudly here rather than return 200 with metadata
    // (assetHash, fileSize) that describes content the response body no longer carries — the
    // same content/metadata inconsistency this whole fix exists to remove.
    mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/" + encode(created.getId()))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isInternalServerError());
  }

  // ===== List endpoint: GET /assets?withContent=true must apply the same sourcing =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssets_withContentAfterEnrichment_returnsOriginalContentForNonRdfAssetAndLeavesRdfAssetUnaffected()
      throws Exception {
    final Asset nonRdfAsset = uploadNonRdfAsset(ORIGINAL_CONTENT);
    enrichAsset(nonRdfAsset.getId(), enrichmentPayload(nonRdfAsset.getId(), "List endpoint check"));
    final String rdfContent = storeRdfAsset();

    final Map<String, String> contentByAssetId = readAssetsContentById(nonRdfAsset.getId(), RDF_ASSET_ID);

    assertEquals(ORIGINAL_CONTENT, contentByAssetId.get(nonRdfAsset.getId()),
        "GET /assets?withContent=true must return the original content for an enriched non-RDF asset,"
            + " not the enrichment document");
    assertEquals(rdfContent, contentByAssetId.get(RDF_ASSET_ID),
        "GET /assets?withContent=true must keep returning an RDF asset's own content unchanged");
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssets_withContentAndUnreadableFile_omitsThatAssetContentButKeepsOthers() throws Exception {
    final Asset healthy = uploadNonRdfAsset(ORIGINAL_CONTENT);
    enrichAsset(healthy.getId(), enrichmentPayload(healthy.getId(), "Healthy asset"));
    final Asset broken = uploadNonRdfAsset("second asset content, file store entry will be deleted");
    enrichAsset(broken.getId(), enrichmentPayload(broken.getId(), "Broken asset"));
    assetFileStore.deleteFile(broken.getAssetHash());

    // Deliberately the opposite of the single-asset fault path: a fault reading one page item
    // must not turn an otherwise-successful page of results into a failed request, so it is
    // degraded to no content for that item rather than propagated.
    final Map<String, String> contentByAssetId = readAssetsContentById(healthy.getId(), broken.getId());

    assertEquals(ORIGINAL_CONTENT, contentByAssetId.get(healthy.getId()),
        "an unreadable file-store entry for one asset must not affect another asset's content"
            + " in the same list response");
    assertNull(contentByAssetId.get(broken.getId()),
        "an unreadable file-store entry must resolve to no content rather than fail the whole list response");
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssets_withContentAndWithoutMetaAfterEnrichment_returnsOriginalContentAndNoMetadata() throws Exception {
    final Asset created = uploadNonRdfAsset(ORIGINAL_CONTENT);
    enrichAsset(created.getId(), enrichmentPayload(created.getId(), "No-meta list check"));

    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/assets")
            .param("ids", created.getId())
            .param("withMeta", "false")
            .param("withContent", "true")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final Assets assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
    assertEquals(1, assets.getItems().size());
    final AssetResult item = assets.getItems().getFirst();

    // withMeta=false means content_kind is not part of the response payload at all; this
    // asserts that the content is still sourced correctly using content_kind read internally,
    // not that content_kind ever reaches the client.
    assertEquals(ORIGINAL_CONTENT, item.getContent(),
        "GET /assets?withMeta=false&withContent=true must still source a non-RDF asset's content"
            + " from the file store, not the persisted enrichment document");
    assertNull(item.getMeta(),
        "withMeta=false must suppress metadata in the response");
  }

  // ===== Security =====

  @Test
  void readAssetById_noAuth_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/{id}", "urn:uuid:00000000-0000-0000-0000-000000000001")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // ===== helpers =====

  private Asset uploadNonRdfAsset(String text) throws Exception {
    final byte[] content = text.getBytes(StandardCharsets.UTF_8);
    final MockMultipartFile file = new MockMultipartFile("file", "standalone.txt", NON_RDF_CONTENT_TYPE, content);

    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
  }

  private AssetEnrichmentResponse enrichAsset(String assetId, String rdfPayload) throws Exception {
    final byte[] content = rdfPayload.getBytes(StandardCharsets.UTF_8);
    final MockMultipartFile file = new MockMultipartFile("file", "metadata.jsonld", ENRICHMENT_CONTENT_TYPE, content);

    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), AssetEnrichmentResponse.class);
  }

  @SuppressWarnings("unchecked") // generic Map deserialization is the only way to read rawContent,
  // a field present on the runtime AssetMetadata but absent from the generated Asset model
  private Map<String, Object> readAssetById(String assetId) throws Exception {
    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/" + encode(assetId))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
  }

  /**
   * Stores an RDF asset directly via {@link AssetStore#storeCredential}, bypassing HTTP upload,
   * so a genuinely RDF (verified-credential) asset can sit alongside a non-RDF asset in the same
   * {@code GET /assets} response.
   *
   * @return the raw content of the stored RDF asset, for comparison against the response body
   */
  private String storeRdfAsset() throws Exception {
    final String rdfContent = getMockFileDataAsString(RDF_ASSET_FILE_NAME);
    final AssetMetadata rdfAssetMeta = new AssetMetadata();
    // DID-style id: HTTP-URL-style ids with slashes break MockMvc path matching elsewhere in
    // this class; a real upload would have the id extracted from the credential by the
    // verification service, but a direct storeCredential call requires it set explicitly.
    rdfAssetMeta.setId(RDF_ASSET_ID);
    rdfAssetMeta.setIssuer(TEST_ISSUER);
    rdfAssetMeta.setAssetHash(calculateSha256AsHex(rdfContent.getBytes(StandardCharsets.UTF_8)));
    rdfAssetMeta.setStatus(AssetStatus.ACTIVE);
    rdfAssetMeta.setStatusDatetime(Instant.now());
    rdfAssetMeta.setUploadDatetime(Instant.now());
    rdfAssetMeta.setContentAccessor(new ContentAccessorDirect(rdfContent));

    assetStore.storeCredential(rdfAssetMeta, verificationService.verifyCredential(rdfAssetMeta.getContentAccessor()));
    return rdfContent;
  }

  /**
   * Calls {@code GET /assets?withContent=true} filtered to the given ids and returns each asset's
   * reported content keyed by its id.
   *
   * @param assetIds ids to filter the list response to
   * @return content by asset id, as reported in the response
   */
  private Map<String, String> readAssetsContentById(String... assetIds) throws Exception {
    final MockHttpServletRequestBuilder request = MockMvcRequestBuilders.get("/assets")
        .param("ids", assetIds)
        .param("withContent", "true")
        .with(csrf())
        .accept(MediaType.APPLICATION_JSON);

    final MvcResult result = mockMvc.perform(request)
        .andExpect(status().isOk())
        .andReturn();

    final Assets assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
    // Collectors.toMap rejects null values (used by the unreadable-file test below to assert
    // that a missing file resolves to no content rather than failing the request), so the map
    // is built explicitly instead.
    final Map<String, String> contentByAssetId = new HashMap<>();
    for (AssetResult item : assets.getItems()) {
      contentByAssetId.put(item.getMeta().getId(), item.getContent());
    }
    return contentByAssetId;
  }

  private String enrichmentPayload(String assetId, String titleValue) {
    return """
        {
          "@context": {"ex": "http://example.org/"},
          "@id": "%s",
          "ex:title": "%s"
        }
        """.formatted(assetId, titleValue);
  }

  private static String encode(String iri) {
    return URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }
}
