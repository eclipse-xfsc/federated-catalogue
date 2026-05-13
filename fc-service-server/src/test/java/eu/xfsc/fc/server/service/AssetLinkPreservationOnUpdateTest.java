package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_UPDATE_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.util.CredentialConstants;
import eu.xfsc.fc.core.util.HashUtils;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests verifying that asset links are preserved after operations
 * that do not explicitly remove them.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetLinkPreservationOnUpdateTest {

  private static final String TEST_ISSUER = "http://example.org/test-issuer";
  private static final String MR_IRI = "did:web:test:preservation-mr";

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;
  @Autowired
  private AssetRepository assetRepository;
  @Autowired
  private GraphStore graphStore;
  @Autowired
  private ProtectedNamespaceProperties namespaceProperties;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    assetStore.clear();
  }

  @Test
  void uploadHumanReadable_withoutAuthentication_returnsUnauthorized() throws Exception {
    final var file = new MockMultipartFile("file", "test.txt", "text/plain",
        "content".getBytes(StandardCharsets.UTF_8));

    mockMvc.perform(MockMvcRequestBuilders
            .multipart("/assets/" + URLEncoder.encode(MR_IRI, StandardCharsets.UTF_8) + "/human-readable")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_UPDATE_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void updateMachineReadableAsset_linksPreservedAfterUpdate() throws Exception {
    storeMrVersion("initial MR content v1");
    final var hrAsset = uploadHumanReadable(MR_IRI, "initial HR content", "text/plain", "hr.txt");

    final var mrBefore = assetRepository.findBySubjectIdWithLinkedAsset(MR_IRI).orElseThrow();
    assertNotNull(mrBefore.getLinkedAsset(), "Link must exist before update");

    storeMrVersion("updated MR content v2");

    final var mrAfterUpdate = assetRepository.findBySubjectIdWithLinkedAsset(MR_IRI).orElseThrow();
    assertNotNull(mrAfterUpdate.getLinkedAsset(), "Link must still be present after updating the MR asset");
    assertEquals(hrAsset.getId(), mrAfterUpdate.getLinkedAsset().getSubjectId(),
        "Link must still point from MR to HR IRI after update");

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_UPDATE_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void replaceHumanReadableAsset_machineReadableAssetUnchanged() throws Exception {
    storeMrVersion("MR content for HR replacement test");
    final var hrV1 = uploadHumanReadable(MR_IRI, "HR v1 content", "text/plain", "hr-v1.txt");
    final var mrAfterV1 = assetRepository.findBySubjectIdWithLinkedAsset(MR_IRI).orElseThrow();
    assertNotNull(mrAfterV1.getLinkedAsset(), "Link must exist after initial upload");

    // Delete HR v1 — ON DELETE SET NULL nulls linked_asset_id on MR; MR asset must survive
    deleteAssetQuietly(hrV1.getAssetHash());
    final var mrAfterDelete = assetRepository.findBySubjectIdWithLinkedAsset(MR_IRI).orElseThrow();
    assertNull(mrAfterDelete.getLinkedAsset(), "Link must be removed after HR deletion");

    // Upload HR v2 linked to the same MR
    final var hrV2 = uploadHumanReadable(MR_IRI, "HR v2 content", "text/plain", "hr-v2.txt");

    // MR asset must still exist and be unchanged
    final var mrMeta = assetStore.getById(MR_IRI);
    assertNotNull(mrMeta, "MR asset must still exist after replacing the HR asset");
    assertEquals(TEST_ISSUER, mrMeta.getIssuer(), "MR asset issuer must be unchanged");

    // Link must now point to HR v2
    final var mrAfterV2 = assetRepository.findBySubjectIdWithLinkedAsset(MR_IRI).orElseThrow();
    assertNotNull(mrAfterV2.getLinkedAsset(), "A new link must exist after re-upload");
    assertEquals(hrV2.getId(), mrAfterV2.getLinkedAsset().getSubjectId(),
        "Link must point to HR v2, not the deleted HR v1");

    deleteAssetQuietly(hrV2.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_UPDATE_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void updateMachineReadableAsset_hasHumanReadableTripleRemainsQueryableAfterUpdate() throws Exception {
    storeMrVersion("MR v1 for SPARQL check");
    final var hrAsset = uploadHumanReadable(MR_IRI, "HR for SPARQL check", "text/plain", "hr-sparql.txt");

    storeMrVersion("MR v2 for SPARQL check");

    final var sparql = """
        SELECT ?s ?p ?o WHERE {
          <<(?s ?p ?o)>> <%s> <%s> .
          FILTER(?s = <%s> && ?p = <%s>)
        }
        """.formatted(CredentialConstants.CREDENTIAL_SUBJECT_URI, MR_IRI, MR_IRI,
            namespaceProperties.getNamespace() + "hasHumanReadable");
    final var results = graphStore.queryData(
        new GraphQuery(sparql, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false));

    assertTrue(results.getTotalCount() > 0,
        "fcmeta:hasHumanReadable triple must remain queryable after MR asset update");

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  private void storeMrVersion(String content) {
    String hash = HashUtils.calculateSha256AsHex(content);
    AssetMetadata meta = new AssetMetadata();
    meta.setId(MR_IRI);
    meta.setIssuer(TEST_ISSUER);
    meta.setAssetHash(hash);
    meta.setStatus(AssetStatus.ACTIVE);
    meta.setUploadDatetime(Instant.now());
    meta.setStatusDatetime(Instant.now());
    meta.setContentAccessor(new ContentAccessorDirect(content));
    CredentialVerificationResult vr = new CredentialVerificationResult(
        Instant.now(), AssetStatus.ACTIVE.getValue(), TEST_ISSUER, Instant.now(), MR_IRI,
        List.of(), List.of(), "", "");
    assetStore.storeCredential(meta, vr);
  }

  private Asset uploadHumanReadable(String mrIri, String text, String contentType, String filename) throws Exception {
    final var content = text.getBytes(StandardCharsets.UTF_8);
    final var file = new MockMultipartFile("file", filename, contentType, content);

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .multipart("/assets/" + URLEncoder.encode(mrIri, StandardCharsets.UTF_8) + "/human-readable")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
  }

  private void deleteAssetQuietly(String hash) {
    try {
      assetStore.deleteAsset(hash);
    } catch (NotFoundException e) {
      // expected
    }
  }
}
