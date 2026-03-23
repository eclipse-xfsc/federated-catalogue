package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import eu.xfsc.fc.core.util.HashUtils;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;

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

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetUploadControllerTest {

    private static final String TEST_ISSUER = "http://example.org/test-issuer";

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

    private void deleteAssetQuietly(String hash) {
        try {
            assetStorePublisher.deleteAsset(hash);
        } catch (NotFoundException e) {
            // expected
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPlainTextMultipartReturnsCreated() throws Exception {
        byte[] content = "Hello, this is a plain text template.".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "template.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals("urn:asset:sha256:" + asset.getAssetHash(), asset.getId());
        assertEquals("text/plain", asset.getContentType());
        assertEquals(content.length, asset.getFileSize());
        assertNull(asset.getValidatorDids());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPdfMultipartReturnsCreated() throws Exception {
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A}; // %PDF-1.4\n
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals("application/pdf", asset.getContentType());
        assertEquals(HashUtils.calculateSha256AsHex(content), asset.getAssetHash());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPlainJsonNoContextReturnsCreated() throws Exception {
        byte[] content = "{\"name\": \"contract\", \"version\": 1}".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "contract.json", "application/json", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals("application/json", asset.getContentType());
        assertNull(asset.getValidatorDids());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadOctetStreamReturnsCreated() throws Exception {
        byte[] content = "raw binary content for testing".getBytes(StandardCharsets.UTF_8);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(content)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getAssetHash());
        assertEquals(HashUtils.calculateSha256AsHex(content), asset.getAssetHash());
        assertEquals(content.length, asset.getFileSize());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadDuplicateAssetReturnsConflict() throws Exception {
        byte[] content = "duplicate-test-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "dup.txt", "text/plain", content);

        MvcResult firstResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(firstResult.getResponse().getContentAsString(), Asset.class);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());

        deleteAssetQuietly(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipart_withWrongRole_returnsForbidden() throws Exception {
        byte[] content = "wrong role test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    public void uploadMultipartWithoutAuthReturnsUnauthorized() throws Exception {
        byte[] content = "unauthorized test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipartWithAssetAdminRoleReturnsCreated() throws Exception {
        byte[] content = "asset-admin upload test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "admin.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        deleteAssetQuietly(asset.getAssetHash());
    }
}