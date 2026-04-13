package eu.xfsc.fc.server.controller;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.AssetVersionList;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.util.HashUtils;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.List;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_UPDATE_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for asset version control endpoints
 *
 * <p>Uses Fuseki graph store backend. Asset versions are created directly via
 * {@link AssetStore#storeCredential} to bypass the HTTP credential-verification pipeline,
 * then the HTTP version endpoints are tested through MockMvc.
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetVersionControllerTest {

  private static final String TEST_ISSUER = "http://example.org/test-issuer";
  private static final String ASSET_IRI = "did:web:example.org:version-test-asset";
  private static final String CONTENT_V1 = "{\"version\":\"one\"}";
  private static final String CONTENT_V2 = "{\"version\":\"two\"}";

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStorePublisher;

  @BeforeAll
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  public void cleanUp() {
    try {
      // getVersionHistoryPage queries Envers and works regardless of live status (ACTIVE or REVOKED).
      // The most-recent snapshot's assetHash matches the live row's hash.
      PaginatedResults<AssetRecord> page =
          assetStorePublisher.getVersionHistoryPage(ASSET_IRI, 0, 1);
      if (!page.getResults().isEmpty()) {
        assetStorePublisher.deleteAsset(page.getResults().getFirst().getAssetHash());
        assertEquals(0, assetStorePublisher.getVersionCount(ASSET_IRI), "asset should be deleted after test");
      }
    } catch (NotFoundException e) {
      // asset not created or already deleted — expected
    }
  }

  // ===== version count =====

  @Test
  public void storeCredential_firstUpload_versionCountIsOne() {
    storeVersion(CONTENT_V1, null);

    int count = assetStorePublisher.getVersionCount(ASSET_IRI);

    assertEquals(1, count);
  }

  @Test
  public void storeCredential_secondUpload_versionCountIsTwo() {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, "v2 change");

    int count = assetStorePublisher.getVersionCount(ASSET_IRI);

    assertEquals(2, count);
  }

  // ===== GET /assets/{id}/versions =====

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_afterTwoUploads_returnsTwoVersionsDescending() throws Exception {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);

    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    AssetVersionList list = objectMapper.readValue(result.getResponse().getContentAsString(), AssetVersionList.class);

    assertNotNull(list);
    assertEquals(2, list.getTotal());
    assertEquals(2, list.getVersions().size());
    assertEquals(2, list.getVersions().get(0).getVersion());
    assertEquals(1, list.getVersions().get(1).getVersion());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_currentVersionHasIsCurrentTrue() throws Exception {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);

    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    AssetVersionList list = objectMapper.readValue(result.getResponse().getContentAsString(), AssetVersionList.class);

    assertTrue(list.getVersions().get(0).getIsCurrent(), "first item (newest) should be isCurrent=true");
    assertFalse(list.getVersions().get(1).getIsCurrent(), "second item should be isCurrent=false");
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_olderVersionsHaveStatusDeprecated() throws Exception {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);

    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    AssetVersionList list = objectMapper.readValue(result.getResponse().getContentAsString(), AssetVersionList.class);

    assertEquals(AssetStatus.DEPRECATED, list.getVersions().get(1).getStatus());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_withChangeComment_commentAppearsInVersionHistory() throws Exception {
    String comment = "updated description";
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, comment);

    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    AssetVersionList list = objectMapper.readValue(result.getResponse().getContentAsString(), AssetVersionList.class);

    assertEquals(comment, list.getVersions().get(0).getChangeComment());
    assertNull(list.getVersions().get(1).getChangeComment());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_pagination_returnsCorrectPage() throws Exception {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);
    storeVersion("{\"version\":\"three\"}", null);

    MvcResult result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/{id}/versions?page=0&size=2", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    AssetVersionList list = objectMapper.readValue(result.getResponse().getContentAsString(), AssetVersionList.class);

    assertEquals(3, list.getTotal());
    assertEquals(2, list.getVersions().size());
    assertEquals(3, list.getVersions().get(0).getVersion());
    assertEquals(2, list.getVersions().get(1).getVersion());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_unknownId_returnsNotFound() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", "did:web:unknown-asset")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  public void readAssetVersions_withoutPermission_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_negativePage_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", ASSET_IRI)
            .param("page", "-1")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_zeroSize_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", ASSET_IRI)
            .param("size", "0")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetVersions_sizeExceedsMax_returnsBadRequest() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/versions", ASSET_IRI)
            .param("size", "101")
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  // ===== GET /assets/{id}?version=X =====

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetById_withVersionParam_returnsHistoricalContent() throws Exception {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);

    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}?version=1", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("one"), "version 1 content should contain 'one'");
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetById_withoutVersionParam_returnsMostRecentContent() throws Exception {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);

    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    String body = result.getResponse().getContentAsString();
    assertTrue(body.contains("two"), "current content should contain 'two'");
  }

  @Test
  public void readAssetById_withoutPermission_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetById_withVersionParamOutOfRange_returnsNotFound() throws Exception {
    storeVersion(CONTENT_V1, null);

    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}?version=99", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetById_withVersionParamZero_returnsBadRequest() throws Exception {
    storeVersion(CONTENT_V1, null);

    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}?version=0", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {"ASSET_READ"})
  public void readAssetById_withNegativeVersionParam_returnsBadRequest() throws Exception {
    storeVersion(CONTENT_V1, null);

    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}?version=-1", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  // ===== POST /assets/{id}/versions/{version}/revoke =====

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  public void revokeAssetVersion_currentVersion_assetBecomesUnavailable() throws Exception {
    storeVersion(CONTENT_V1, null);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/versions/{version}/revoke", ASSET_IRI, 1)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // GET /assets/{id} should now return 404 (no ACTIVE row)
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}", ASSET_IRI)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  public void revokeAssetVersion_historicalVersion_returnsConflict() throws Exception {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);

    // v1 is historical — only v2 (current) can be revoked
    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/versions/{version}/revoke", ASSET_IRI, 1)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  public void revokeAssetVersion_alreadyRevokedVersion_returnsConflict() throws Exception {
    storeVersion(CONTENT_V1, null);
    // Revoke the current version
    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/versions/{version}/revoke", ASSET_IRI, 1)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    // Revoking again should 409 (already REVOKED — getByIdAndVersion returns snapshot,
    // but changeLifeCycleStatus finds a non-ACTIVE row)
    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/versions/{version}/revoke", ASSET_IRI, 1)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  public void revokeAssetVersion_unknownVersion_returnsNotFound() throws Exception {
    storeVersion(CONTENT_V1, null);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/versions/{version}/revoke", ASSET_IRI, 99)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(
      stringClaims = {@StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  public void revokeAssetVersion_negativeVersion_returnsBadRequest() throws Exception {
    storeVersion(CONTENT_V1, null);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/versions/{version}/revoke", ASSET_IRI, -1)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  public void revokeAssetVersion_withoutPermission_returnsUnauthorized() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/versions/{version}/revoke", ASSET_IRI, 1)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // ===== DAO / Envers layer (via service) =====

  @Test
  public void getVersionHistoryPage_afterTwoUploads_correctTotalAndOrder() {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);

    PaginatedResults<AssetRecord> page = assetStorePublisher.getVersionHistoryPage(ASSET_IRI, 0, 10);

    assertEquals(2, page.getTotalCount());
    assertEquals(2, page.getResults().size());
    assertEquals(2, page.getResults().get(0).getVersion());
    assertEquals(1, page.getResults().get(1).getVersion());
  }

  @Test
  public void getByIdAndVersion_existingVersion_returnsHistoricalRecord() {
    storeVersion(CONTENT_V1, null);
    storeVersion(CONTENT_V2, null);

    AssetRecord v1 = assetStorePublisher.getByIdAndVersion(ASSET_IRI, 1);

    assertNotNull(v1);
    assertEquals(CONTENT_V1, v1.getContent());
    assertFalse(v1.getIsCurrent());
  }

  @Test
  public void getByIdAndVersion_unknownVersion_throwsNotFoundException() {
    storeVersion(CONTENT_V1, null);

    assertThrows(NotFoundException.class,
        () -> assetStorePublisher.getByIdAndVersion(ASSET_IRI, 99));
  }

  @Test
  public void getVersionHistoryPage_migrationScenario_existingAssetHasVersionOne() {
    // Simulates a "migrated" asset — first-ever upload creates version 1
    storeVersion(CONTENT_V1, null);

    AssetRecord v1 = assetStorePublisher.getByIdAndVersion(ASSET_IRI, 1);

    assertEquals(1, v1.getVersion());
  }

  // ===== helpers =====

  private void storeVersion(String content, String changeComment) {
    String hash = HashUtils.calculateSha256AsHex(content);
    AssetMetadata meta = new AssetMetadata();
    meta.setId(ASSET_IRI);
    meta.setIssuer(TEST_ISSUER);
    meta.setAssetHash(hash);
    meta.setStatus(AssetStatus.ACTIVE);
    meta.setUploadDatetime(Instant.now());
    meta.setStatusDatetime(Instant.now());
    meta.setContentAccessor(new ContentAccessorDirect(content));
    meta.setChangeComment(changeComment);

    CredentialVerificationResult vr = new CredentialVerificationResult(
        Instant.now(), AssetStatus.ACTIVE.getValue(),
        TEST_ISSUER, Instant.now(), ASSET_IRI, List.of(), List.of());
    assetStorePublisher.storeCredential(meta, vr);
  }
}
