package eu.xfsc.fc.server.controller;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.util.GraphRebuilder;
import eu.xfsc.fc.core.util.HashUtils;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.api.generated.model.AssetStatus;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;

/**
 * Integration test verifying that graph rebuild restores link triples from PostgreSQL.
 *
 * <p>Uses the Fuseki graph store so that SPARQL queries can assert triples are present
 * after rebuild.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class GraphRebuildLinkRestoreTest {

  private static final String TEST_ISSUER = "http://example.org/test-issuer";
  private static final String FCMETA_NS =
      "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#";
  private static final String FCMETA_HAS_HUMAN_READABLE = FCMETA_NS + "hasHumanReadable";
  /** The RDF-star wrapping predicate used by {@link eu.xfsc.fc.graphdb.service.SparqlGraphStore}. */
  private static final String CRED_SUBJECT_URI = "https://www.w3.org/2018/credentials#credentialSubject";

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;
  @Autowired
  private GraphStore graphStore;
  @Autowired
  private GraphRebuilder graphRebuilder;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    // Delete assets individually so each deletion also purges the Fuseki named graph.
    // assetStore.clear() does a bulk SQL delete that bypasses the graph store cleanup.
    assetStore.getActiveAssetHashes(null, 100, 1, 0)
        .forEach(hash -> {
          try {
            assetStore.deleteAsset(hash);
          } catch (eu.xfsc.fc.core.exception.NotFoundException e) {
            // already gone — skip
          }
        });
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void rebuildGraphDb_afterLinkCreated_restoresLinkTriples() throws Exception {
    // Store a machine-readable non-RDF asset
    final var mrContent = "machine-readable content for rebuild test".getBytes(StandardCharsets.UTF_8);
    final var mrFile = new MockMultipartFile("file", "mr.bin", "application/octet-stream", mrContent);
    final var mrResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(mrFile)
            .with(csrf()))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn();
    final var mrAsset = objectMapper.readValue(mrResult.getResponse().getContentAsString(), Asset.class);

    // Store a human-readable asset
    final var hrContent = "human-readable PDF content for rebuild test".getBytes(StandardCharsets.UTF_8);
    final var hrFile = new MockMultipartFile("file", "hr.pdf", "application/pdf", hrContent);

    final var hrUrl = String.format("/assets/%s/human-readable",
        java.net.URLEncoder.encode(mrAsset.getId(), StandardCharsets.UTF_8));
    final var hrResult = mockMvc.perform(MockMvcRequestBuilders.multipart(hrUrl)
            .file(hrFile)
            .with(csrf()))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn();
    final var hrAsset = objectMapper.readValue(hrResult.getResponse().getContentAsString(), Asset.class);

    // Wipe the graph store so we can verify rebuild restores the triples
    graphStore.deleteClaims(mrAsset.getId());
    graphStore.deleteClaims(hrAsset.getId());

    // Run rebuild
    graphRebuilder.rebuildGraphDb(1, 0, 1, 100);

    // Query Fuseki for the hasHumanReadable triple.
    // The Fuseki store uses RDF-star format: <<s p o>> cred:credentialSubject <credSubject>.
    // A plain triple pattern does not match this format. Use the all-variables RDF-star pattern
    // with a FILTER to locate the specific inner triple, filtering by inner triple subject (mrIri)
    // and predicate (fcmeta:hasHumanReadable).
    final var sparql = """
        SELECT ?s ?p ?o WHERE {
          <<(?s ?p ?o)>> <%s> <%s> .
          FILTER(?s = <%s> && ?p = <%s>)
        }
        """.formatted(CRED_SUBJECT_URI, mrAsset.getId(), mrAsset.getId(), FCMETA_HAS_HUMAN_READABLE);
    final var query = new GraphQuery(sparql, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false);

    final var results = graphStore.queryData(query);
    assertTrue(results.getTotalCount() > 0,
        "fcmeta:hasHumanReadable triple must be present after graph rebuild");

    final var hrIriInGraph = results.getResults().stream()
        .map(row -> String.valueOf(row.get("o")))
        .findFirst()
        .orElse("");
    assertTrue(hrIriInGraph.contains(hrAsset.getId()),
        "Linked HR IRI must appear in graph after rebuild, got: " + hrIriInGraph);

    // Cleanup
    deleteAssetQuietly(mrAsset.getAssetHash());
    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
      @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void rebuildGraphDb_noLinks_completesWithoutError() throws Exception {
    // Single non-RDF asset, no links — rebuild should not throw
    final var content = "standalone rebuild content".getBytes(StandardCharsets.UTF_8);
    final var file = new MockMultipartFile("file", "standalone.txt", "text/plain", content);
    final var result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf()))
        .andExpect(MockMvcResultMatchers.status().isCreated())
        .andReturn();
    final var asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);

    // Should not throw
    graphRebuilder.rebuildGraphDb(1, 0, 1, 100);

    deleteAssetQuietly(asset.getAssetHash());
  }

  // ===== helper =====

  private void deleteAssetQuietly(String hash) {
    try {
      assetStore.deleteAsset(hash);
    } catch (NotFoundException e) {
      // expected
    }
  }
}
