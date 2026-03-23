package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_READ;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_CREATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_DELETE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_READ_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.ASSET_UPDATE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.Asset;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.Assets;
import eu.xfsc.fc.api.generated.model.Error;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.core.util.HashUtils;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.neo4j.harness.Neo4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=neo4j"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class AssetControllerTest {
    private final static String TEST_ISSUER = "http://example.org/test-issuer";
    private final static String PARTICIPANT_ISSUER = "did:example:issuer";
    private final static String RESOURCE_ISSUER = "did:web:compliance.lab.gaia-x.eu";
    private final static String ASSET_FILE_NAME = "default-credential.json";

    @Autowired
    private Neo4j embeddedDatabaseServer;
    @Autowired
    private GraphStore graphStore;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private AssetStore assetStorePublisher;
    // can't remove it for some reason, many tests fails with auth error
    @MockitoSpyBean(name = "schemaFileStore")
    private FileStore fileStore;

    @Autowired
    private SchemaStore schemaStore;
    @Autowired
    private VerificationService verificationService;

    private static AssetMetadata assetMeta;
    
    @BeforeAll
    public void setup() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        assetMeta = createAssetMetadata();
    }

    @AfterAll
    public void storageSelfCleaning() throws IOException {
        embeddedDatabaseServer.close();
    }
    
    @AfterEach
    public void deleteTestAsset() throws IOException {
        try {
            assetStorePublisher.deleteAsset(assetMeta.getAssetHash());
        } catch (NotFoundException e) {
            // expected
        }
    }
    
    @Test
    public void readAssetsShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAssetsShouldReturnBadRequestResponse() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.get("/assets?statuses=123")
              .with(csrf())
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAssetsShouldReturnSuccessResponse() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());
        MvcResult result =  mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
            .andReturn();

        Assets assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
        assertNotNull(assets);
        assertEquals(1, assets.getItems().size());
        assertEquals(1, assets.getTotalCount());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAssetsByFilterShouldReturnSuccessResponse() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());
        
        MvcResult result =  mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                        .accept(MediaType.APPLICATION_JSON)
                .queryParam("issuers", assetMeta.getIssuer()))  
                .andExpect(status().isOk())
            .andReturn();
        Assets assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
        assertNotNull(assets);
        assertEquals(1, assets.getItems().size());
        assertEquals(1, assets.getTotalCount());
        
        String statusTr = assetMeta.getStatusDatetime().minusSeconds(5).toString() + "/" + assetMeta.getStatusDatetime().plusSeconds(5).toString();
        result =  mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("hashes", assetMeta.getAssetHash())  
                .queryParam("statusTimerange", statusTr))  
                .andExpect(status().isOk())
                .andReturn();
        assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
        assertNotNull(assets);
        assertEquals(1, assets.getItems().size());
        assertEquals(1, assets.getTotalCount());

        String uploadTr = assetMeta.getUploadDatetime().minusSeconds(5).toString() + "/" + assetMeta.getUploadDatetime().plusSeconds(5).toString();
        result =  mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("ids", assetMeta.getId())  
                .queryParam("uploadTimerange", uploadTr))  
                .andExpect(status().isOk())
                .andReturn();
        assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
        assertNotNull(assets);
        assertEquals(1, assets.getItems().size());
        assertEquals(1, assets.getTotalCount());

        if (assetMeta.getValidatorDids() != null && !assetMeta.getValidatorDids().isEmpty()) {
            result =  mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                    .accept(MediaType.APPLICATION_JSON)
                    .queryParam("validators", assetMeta.getValidatorDids().stream().collect(Collectors.joining(","))))
                    .andExpect(status().isOk())
                    .andReturn();
            assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
            assertNotNull(assets);
            assertEquals(1, assets.getItems().size());
            assertEquals(1, assets.getTotalCount());
        }

        result =  mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("withMeta", "false") //default is true
                .queryParam("withContent", "true"))  //default is false
            .andExpect(status().isOk())
            .andReturn();
        assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
        assertNotNull(assets);
        assertEquals(1, assets.getItems().size());
        assertEquals(1, assets.getTotalCount());
        assertNotNull(assets.getItems().get(0).getContent());
        assertNull(assets.getItems().get(0).getMeta());

        result =  mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("withMeta", "true") //default is true
                .queryParam("withContent", "false"))  //default is false
            .andExpect(status().isOk())
            .andReturn();
        assets = objectMapper.readValue(result.getResponse().getContentAsString(), Assets.class);
        assertNotNull(assets);
        assertEquals(1, assets.getItems().size());
        assertEquals(1, assets.getTotalCount());
        assertNull(assets.getItems().get(0).getContent());
        assertNotNull(assets.getItems().get(0).getMeta());
    }

    @Test
    public void readAssetByHashShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets/" + assetMeta.getAssetHash())
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAssetByHashShouldReturnNotFoundResponse() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.get("/assets/123").with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAssetByHashShouldReturnSuccessResponse() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());

        mockMvc.perform(MockMvcRequestBuilders.get("/assets/" + assetMeta.getAssetHash())
                .with(csrf()))
                //.accept("application/ld+json"))
            .andExpect(status().isOk());
    }

    @Test
    public void deleteAssetShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/" + assetMeta.getAssetHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void deleteAssetReturnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/" + assetMeta.getAssetHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = ASSET_DELETE_WITH_PREFIX, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = "")})))
    public void deleteAssetWithoutIssuerReturnForbiddenResponse() throws Exception {
      assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());
      mockMvc.perform(MockMvcRequestBuilders.delete("/assets/" + assetMeta.getAssetHash())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_DELETE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteAssetReturnNotFoundResponse() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.delete("/assets/" + assetMeta.getAssetHash())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_DELETE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteAssetReturnSuccessResponse() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());

        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/" + assetMeta.getAssetHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void addAssetShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void addAssetReturnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                        .content(getMockFileDataAsString(ASSET_FILE_NAME))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addAssetWithoutIssuerReturnUnprocessableEntity() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.post("/assets")
              .content(getMockFileDataAsString("credential-without-issuer.json"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addAssetReturnCreatedResponse() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(getMockFileDataAsString(ASSET_FILE_NAME))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertTrue(asset.getWarnings() == null || asset.getWarnings().isEmpty(),
            "Clean asset upload should produce no warnings");
        assetStorePublisher.deleteAsset(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addAssetWithFcmetaTriples_returnsCreated_withWarning() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(getMockFileDataAsString("credential-with-fcmeta.json"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertNotNull(asset.getWarnings(), "Warnings should be present when fcmeta triples were filtered");
        assertFalse(asset.getWarnings().isEmpty(), "Warnings list should not be empty when fcmeta triples were filtered");
        assertTrue(asset.getWarnings().get(0).contains("triple(s)"), "Warning should mention filtered triple count");
        assertTrue(asset.getWarnings().get(0).contains("federated-catalogue/meta#"), "Warning should contain the reserved namespace URI");
        assetStorePublisher.deleteAsset(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = RESOURCE_ISSUER)})))
    public void addResourceReturnCreatedResponse() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(getMockFileDataAsString("credential-resource.json"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assetStorePublisher.deleteAsset(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = PARTICIPANT_ISSUER)})))
    public void addParicipantReturnCreatedResponse() throws Exception {
        schemaStore.addSchema(getAccessor("mock-data/gax-test-ontology.ttl"));
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(getMockFileDataAsString("default-participant.json"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assetStorePublisher.deleteAsset(asset.getAssetHash());
        schemaStore.clear();
    }
    
    /**
     * POST /assets accepts a SHACL-invalid asset with default config (verifySchema=false).
     * Same asset as addParicipantReturnCreatedResponse, but with a SHACL shape loaded that would reject it.
     * Proves that schema validation is disabled in the upload flow.
     */
    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = PARTICIPANT_ISSUER)})))
    public void addShaclInvalidAssetReturnCreatedResponse() throws Exception {
        schemaStore.addSchema(getAccessor("mock-data/gax-test-ontology.ttl"));
        schemaStore.addSchema(getAccessor("mock-data/legal-personShape.ttl"));
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(getMockFileDataAsString("default-participant.json"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assetStorePublisher.deleteAsset(asset.getAssetHash());
        schemaStore.clear();
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addDuplicateAssetReturnConflictWithAssetStorage() throws Exception {
      String asset = getMockFileDataAsString(ASSET_FILE_NAME);
      ContentAccessorDirect contentAccessor = new ContentAccessorDirect(asset);

      AssetMetadata assetMetadata = new AssetMetadata("id123", TEST_ISSUER, new ArrayList<>(), contentAccessor);

      assetStorePublisher.storeCredential(assetMetadata, getStaticVerificationResult());
      mockMvc.perform(MockMvcRequestBuilders
              .post("/assets")
              .content(asset)
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict());
      assetStorePublisher.deleteAsset(assetMetadata.getAssetHash());
      assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(assetMetadata.getAssetHash()));
    }

    // TODO: 05.09.2022 Need to add a test to check the correct scenario with graph storage when it is added
    //@Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addAssetFailedThenAllTransactionRolledBack() throws Exception {
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        //doThrow((new IOException("Some server exception")))
        //    .when(fileStore).storeFile(hashCaptor.capture(), any());

        mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(getMockFileDataAsString(ASSET_FILE_NAME))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError());

        String hash = hashCaptor.getValue();

        //assertThrowsExactly(FileNotFoundException.class,
        //    () -> fileStore.readFile(hash));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
    }

    @Test
    public void revokeAssetShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/123/revoke")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void revokeAssetReturnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/123/revoke")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeAssetReturnNotFound() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.post("/assets/" + assetMeta.getAssetHash() + "/revoke")
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeAssetReturnSuccessResponse() throws Exception {
        final CredentialVerificationResult vr = new CredentialVerificationResult(Instant.now(), AssetStatus.ACTIVE.getValue(), "issuer",
                Instant.now(), "vhash", List.of(), List.of());
        assetStorePublisher.storeCredential(assetMeta, vr);
//        assetStore.storeCredential(assetMeta, getStaticVerificationResult());
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/" + assetMeta.getAssetHash() + "/revoke")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // Requires both ASSET_UPDATE (revoke) and ASSET_CREATE (re-add) to test the composite flow.
    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeThenAddAssetReturnCorrectResponse() throws Exception {
        String content = getMockFileDataAsString(ASSET_FILE_NAME);
        String hash = HashUtils.calculateSha256AsHex(content);
        try {
          assetStorePublisher.deleteAsset(hash);
        } catch (NotFoundException ex) {
            // expected
        }
        
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(content)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
            	.with(csrf()))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assertEquals(hash, asset.getAssetHash());

        List<Map<String, Object>> nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n {claimsGraphUri: [$uri]}) RETURN n", Map.of("uri", TEST_ISSUER)
        )).getResults();
        log.debug("revokeThenAddAssetReturnCorrectResponse.1; got {} nodes", nodes.size());
        assertEquals(2, nodes.size());

        mockMvc.perform(MockMvcRequestBuilders.post("/assets/" + hash + "/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
            	.with(csrf()))
                .andExpect(status().isOk());
        
        nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n {claimsGraphUri: [$uri]}) RETURN n", Map.of("uri", TEST_ISSUER)
        )).getResults();
        log.debug("revokeThenAddAssetReturnCorrectResponse.2; got {} nodes", nodes.size());
        assertEquals(0, nodes.size());
        
        result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(content)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
            	.with(csrf()))
            .andExpect(status().isConflict())
            .andReturn();

        nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n {claimsGraphUri: [$uri]}) RETURN n", Map.of("uri", TEST_ISSUER)
        )).getResults();
        log.debug("revokeThenAddAssetReturnCorrectResponse.3; got {} nodes", nodes.size());
        assertEquals(0, nodes.size());
        
        // this call fails for some reason. why cannot we delete revoked asset? 
        assetStorePublisher.deleteAsset(hash);
    }
    
    @Test
    @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeAssetWithNotActiveStatusReturnConflictResponse() throws Exception {
        final CredentialVerificationResult vr = new CredentialVerificationResult(Instant.now(), AssetStatus.ACTIVE.getValue(), "issuer",
                Instant.now(), "vhash", List.of(), List.of());
        AssetMetadata metadata = assetMeta;
        metadata.setStatus(AssetStatus.DEPRECATED);
        assetStorePublisher.storeCredential(metadata, vr);
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.post("/assets/" + assetMeta.getAssetHash() + "/revoke")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict()).andReturn();
        Error error = objectMapper.readValue(result.getResponse().getContentAsString(), Error.class);
        assertEquals("The asset status cannot be changed because the asset metadata status is deprecated", error.getMessage());
        assetStorePublisher.deleteAsset(metadata.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addAsset_withReadOnlyPermission_returnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                        .content(getMockFileDataAsString(ASSET_FILE_NAME))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    public void readAssets_withoutPermissionRole_shouldReturnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {ADMIN_ALL_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = "admin-participant")})))
    public void addAsset_withAdminAllRole_returnCreatedResponse() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(getMockFileDataAsString(ASSET_FILE_NAME))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assetStorePublisher.deleteAsset(asset.getAssetHash());
    }

    @Test
    @WithMockUser(roles = {"ADMIN_ALL"})
    public void readAssets_withAdminAllRole_returnSuccessResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    private static AssetMetadata createAssetMetadata() throws IOException {
        AssetMetadata assetMeta = new AssetMetadata();
        assetMeta.setId("test id");
        assetMeta.setIssuer(TEST_ISSUER);
        assetMeta.setAssetHash(HashUtils.calculateSha256AsHex("test hash"));
        assetMeta.setStatus(AssetStatus.ACTIVE);
        assetMeta.setStatusDatetime(Instant.parse("2022-01-01T12:00:00Z"));
        assetMeta.setUploadDatetime(Instant.parse("2022-01-02T12:00:00Z"));
        assetMeta.setContentAccessor(new ContentAccessorDirect(getMockFileDataAsString(ASSET_FILE_NAME)));
        return assetMeta;
    }

    private CredentialVerificationResultOffering getStaticVerificationResult() {
        return verificationService.verifyOfferingCredential(assetMeta.getContentAccessor());
    }
}
