package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Integration tests verifying that GET /assets/{id}/human-readable and
 * GET /assets/{id}/machine-readable work correctly when the graph store is disabled
 * ({@code graphstore.impl=none}). These endpoints read from PostgreSQL and FileStore only
 * and must not depend on a running graph DB.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=none"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetLinkControllerGraphlessTest {

  private static final String TEST_ISSUER = "http://example.org/test-issuer";
  private static final String HR_URL_TEMPLATE = "/assets/%s/human-readable";
  private static final String MR_URL_TEMPLATE = "/assets/%s/machine-readable";

  /** Minimal valid PDF header bytes. */
  private static final byte[] PDF_CONTENT = "%PDF-1.4\n%test asset graphless".getBytes(StandardCharsets.UTF_8);

  @Autowired
  private WebApplicationContext context;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private AssetStore assetStore;

  /** Hash of the MR asset — tracked for cleanup. */
  private String mrHash;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @AfterEach
  void cleanUp() {
    if (mrHash != null) {
      deleteAssetQuietly(mrHash);
      mrHash = null;
    }
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void getHumanReadable_graphStoreDisabled_returnsOk() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);
    final var uploadResult = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrAsset.getId())))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    final var hrAsset = objectMapper.readValue(uploadResult.getResponse().getContentAsString(), Asset.class);

    final var getResult = mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(HR_URL_TEMPLATE, encode(mrAsset.getId())))
            .with(csrf()))
        .andExpect(status().isOk())
        .andReturn();

    assertEquals(PDF_CONTENT.length, getResult.getResponse().getContentAsByteArray().length);

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  @Test
  @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_READ_WITH_PREFIX},
      claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
          @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
  void getMachineReadable_graphStoreDisabled_returnsOk() throws Exception {
    final var mrAsset = uploadMachineReadableAsset();
    mrHash = mrAsset.getAssetHash();

    final var file = new MockMultipartFile("file", "doc.pdf", "application/pdf", PDF_CONTENT);
    final var uploadResult = mockMvc.perform(MockMvcRequestBuilders
            .multipart(String.format(HR_URL_TEMPLATE, encode(mrAsset.getId())))
            .file(file)
            .with(csrf())
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated())
        .andReturn();
    final var hrAsset = objectMapper.readValue(uploadResult.getResponse().getContentAsString(), Asset.class);

    mockMvc.perform(MockMvcRequestBuilders
            .get(String.format(MR_URL_TEMPLATE, encode(hrAsset.getId())))
            .with(csrf()))
        .andExpect(status().isOk());

    deleteAssetQuietly(hrAsset.getAssetHash());
  }

  // ===== helpers =====

  private Asset uploadMachineReadableAsset() throws Exception {
    final var content = "machine-readable asset for graphless test".getBytes(StandardCharsets.UTF_8);
    final var file = new MockMultipartFile("file", "asset.txt", "text/plain", content);

    final MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
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
    return java.net.URLEncoder.encode(iri, StandardCharsets.UTF_8);
  }
}
