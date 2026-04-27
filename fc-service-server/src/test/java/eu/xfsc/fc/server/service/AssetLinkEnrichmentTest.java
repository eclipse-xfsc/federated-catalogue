package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.util.Map;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
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
 * Integration tests verifying that GET /assets/{id} response includes link information
 * (humanReadableId / machineReadableId) when links exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetLinkEnrichmentTest {

  private static final String TEST_ISSUER = "http://example.org/test-issuer";

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    assetStore.clear();
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_machineReadableWithLinkedHr_includesHumanReadableId() throws Exception {
    final var mrAsset = uploadNonRdfAsset("mr content for enrichment test", "text/plain", "mr.txt");
    final var hrAsset = uploadHumanReadable(mrAsset.getId(), "pdf content", "application/pdf", "doc.pdf");

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/" + encode(mrAsset.getId()))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var returned = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    assertNotNull(returned.getHumanReadableId(),
        "GET /assets/{mrId} must include humanReadableId when a link exists");
    assertEquals(hrAsset.getId(), returned.getHumanReadableId());
    assertNull(returned.getMachineReadableId(),
        "MR asset should not have a machineReadableId link");

    deleteAssetQuietly(hrAsset.getAssetHash());
    deleteAssetQuietly(mrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_humanReadableWithLinkedMr_includesMachineReadableId() throws Exception {
    final var mrAsset = uploadNonRdfAsset("mr content v2 for enrichment test", "text/plain", "mr2.txt");
    final var hrAsset = uploadHumanReadable(mrAsset.getId(), "pdf content v2", "application/pdf", "doc2.pdf");

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/" + encode(hrAsset.getId()))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var returned = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    assertNotNull(returned.getMachineReadableId(),
        "GET /assets/{hrId} must include machineReadableId when a link exists");
    assertEquals(mrAsset.getId(), returned.getMachineReadableId());
    assertNull(returned.getHumanReadableId(),
        "HR asset should not have a humanReadableId link");

    deleteAssetQuietly(hrAsset.getAssetHash());
    deleteAssetQuietly(mrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void readAssetById_noLinksExist_linkFieldsAreNull() throws Exception {
    final var mrAsset = uploadNonRdfAsset("standalone mr no link", "text/plain", "standalone.txt");

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/" + encode(mrAsset.getId()))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final var returned = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
    assertNull(returned.getHumanReadableId(), "No link means humanReadableId must be null");
    assertNull(returned.getMachineReadableId(), "No link means machineReadableId must be null");

    deleteAssetQuietly(mrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void getAssetById_jsonLdAssetWithLinkedHr_returnsEnrichedMetadataWithRawContent() throws Exception {
    final var jsonLdAsset = uploadJsonLdAsset(getMockFileDataAsString("default-credential.json"));
    final var hrAsset = uploadHumanReadable(jsonLdAsset.getId(), "pdf content for jsonld test", "application/pdf", "jsonld-doc.pdf");

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/" + encode(jsonLdAsset.getId()))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    @SuppressWarnings("unchecked")
    final Map<String, Object> returned = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertNotNull(returned.get("humanReadableId"),
        "GET /assets/{jsonLdId} must include humanReadableId when a link exists");
    assertEquals(hrAsset.getId(), returned.get("humanReadableId"));
    assertNotNull(returned.get("rawContent"),
        "GET /assets/{jsonLdId} must include rawContent for JSON-LD assets (requires API-B1 fix)");

    deleteAssetQuietly(hrAsset.getAssetHash());
    deleteAssetQuietly(jsonLdAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void getAssetById_jsonLdAssetWithoutLinks_returnsMetadataWithRawContent() throws Exception {
    final String originalContent = getMockFileDataAsString("default-credential.json");
    final var jsonLdAsset = uploadJsonLdAsset(originalContent);

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .get("/assets/" + encode(jsonLdAsset.getId()))
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    @SuppressWarnings("unchecked")
    final Map<String, Object> returned = objectMapper.readValue(result.getResponse().getContentAsString(), Map.class);
    assertNull(returned.get("humanReadableId"),
        "Unlinked JSON-LD asset must have no humanReadableId");
    assertNotNull(returned.get("rawContent"),
        "GET /assets/{jsonLdId} must include rawContent for JSON-LD assets (requires API-B1 fix)");

    deleteAssetQuietly(jsonLdAsset.getAssetHash());
  }

  // ===== helpers =====

  private Asset uploadJsonLdAsset(String content) throws Exception {
    final var result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
            .content(content)
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
  }

  private Asset uploadNonRdfAsset(String text, String contentType, String filename) throws Exception {
    final var content = text.getBytes(StandardCharsets.UTF_8);
    final var file = new MockMultipartFile("file", filename, contentType, content);

    final var result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();

    return objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
  }

  private Asset uploadHumanReadable(String mrIri, String text, String contentType, String filename) throws Exception {
    final var content = text.getBytes(StandardCharsets.UTF_8);
    final var file = new MockMultipartFile("file", filename, contentType, content);

    final var result = mockMvc.perform(MockMvcRequestBuilders
            .multipart("/assets/" + encode(mrIri) + "/human-readable")
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

  private static String encode(String iri) {
    return URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }
}
