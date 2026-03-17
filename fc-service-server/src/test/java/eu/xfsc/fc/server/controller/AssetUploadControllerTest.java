package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.SD_ADMIN_ROLE_WITH_PREFIX;
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

import eu.xfsc.fc.api.generated.model.SelfDescription;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
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
    private SelfDescriptionStore sdStorePublisher;

    @BeforeAll
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private void deleteSdQuietly(String hash) {
        try {
            sdStorePublisher.deleteSelfDescription(hash);
        } catch (NotFoundException e) {
            // expected
        }
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPlainTextMultipartReturnsCreated() throws Exception {
        byte[] content = "Hello, this is a plain text template.".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "template.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/self-descriptions")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        assertNotNull(sd.getSdHash());
        assertEquals("urn:asset:sha256:" + sd.getSdHash(), sd.getId());
        assertEquals("text/plain", sd.getContentType());
        assertEquals(content.length, sd.getFileSize());
        assertNull(sd.getValidatorDids());

        deleteSdQuietly(sd.getSdHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPdfMultipartReturnsCreated() throws Exception {
        byte[] content = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31, 0x2E, 0x34, 0x0A}; // %PDF-1.4\n
        MockMultipartFile file = new MockMultipartFile("file", "sample.pdf", "application/pdf", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/self-descriptions")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        assertNotNull(sd.getSdHash());
        assertEquals("application/pdf", sd.getContentType());
        assertEquals(HashUtils.calculateSha256AsHex(content), sd.getSdHash());

        deleteSdQuietly(sd.getSdHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadPlainJsonNoContextReturnsCreated() throws Exception {
        byte[] content = "{\"name\": \"contract\", \"version\": 1}".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "contract.json", "application/json", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/self-descriptions")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        assertNotNull(sd.getSdHash());
        assertEquals("application/json", sd.getContentType());
        assertNull(sd.getValidatorDids());

        deleteSdQuietly(sd.getSdHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadOctetStreamReturnsCreated() throws Exception {
        byte[] content = "raw binary content for testing".getBytes(StandardCharsets.UTF_8);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                .content(content)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        assertNotNull(sd.getSdHash());
        assertEquals(HashUtils.calculateSha256AsHex(content), sd.getSdHash());
        assertEquals(content.length, sd.getFileSize());

        deleteSdQuietly(sd.getSdHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadDuplicateAssetReturnsConflict() throws Exception {
        byte[] content = "duplicate-test-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "dup.txt", "text/plain", content);

        MvcResult firstResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/self-descriptions")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(firstResult.getResponse().getContentAsString(), SelfDescription.class);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/self-descriptions")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict());

        deleteSdQuietly(sd.getSdHash());
    }

    @Test
    public void uploadMultipartWithoutAuthReturnsUnauthorized() throws Exception {
        byte[] content = "unauthorized test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "test.txt", "text/plain", content);

        mockMvc.perform(MockMvcRequestBuilders.multipart("/self-descriptions")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockJwtAuth(authorities = {SD_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void uploadMultipartWithSdAdminRoleReturnsCreated() throws Exception {
        byte[] content = "sd-admin upload test".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "admin.txt", "text/plain", content);

        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.multipart("/self-descriptions")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        deleteSdQuietly(sd.getSdHash());
    }
}