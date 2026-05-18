package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import eu.xfsc.fc.api.generated.model.Error;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
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

/**
 * Integration tests for asset enrichment when graph store is disabled
 * ({@code graphstore.impl=none}). When enrichment is attempted with a disabled
 * graph backend, the service should respond with HTTP 503 Service Unavailable
 * and a clear error message.
 */
@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=none"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetUploadControllerDisabledGraphTest {

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
    public void enrichNonRdfAsset_graphStoreDisabled_returns503() throws Exception {
        // Arrange: Create initial non-RDF asset
        byte[] plainContent = "plain text document".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "doc.txt", "text/plain", plainContent);

        MvcResult createResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                .file(file)
                .with(csrf())
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset createdAsset = objectMapper.readValue(createResult.getResponse().getContentAsString(), Asset.class);
        String assetId = createdAsset.getId();

        try {
            // Act: Attempt to enrich with RDF metadata (should fail because graph store is disabled)
            String rdfPayload = """
                {
                  "@context": {"ex": "http://example.org/"},
                  "@id": "%s",
                  "ex:title": "Enriched Document",
                  "ex:creator": "Test User"
                }
                """.formatted(assetId);
            byte[] rdfContent = rdfPayload.getBytes(StandardCharsets.UTF_8);
            MockMultipartFile rdfFile = new MockMultipartFile("file", "metadata.jsonld", "application/ld+json", rdfContent);

            MvcResult enrichResult = mockMvc.perform(MockMvcRequestBuilders.multipart("/assets")
                    .file(rdfFile)
                    .with(csrf())
                    .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isServiceUnavailable())
                .andReturn();

            // Assert: Check the error response
            Error errorResponse = objectMapper.readValue(enrichResult.getResponse().getContentAsString(), Error.class);
            assertNotNull(errorResponse);
            assertEquals("graph_store_disabled", errorResponse.getCode());
            assertNotNull(errorResponse.getMessage());
            assertNotNull(errorResponse.getMessage(), "Error message should not be empty");
            // Verify the message mentions the disabled graph store
            String message = errorResponse.getMessage();
            assertTrue(message.contains("graph store") || message.contains("disabled") || message.contains("enrichment"));
        } finally {
            deleteAssetQuietly(createdAsset.getAssetHash());
        }
    }
}
