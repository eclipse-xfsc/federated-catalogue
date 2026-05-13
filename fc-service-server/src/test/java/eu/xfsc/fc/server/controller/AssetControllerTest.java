package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_READ;
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
import java.nio.charset.StandardCharsets;
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
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidationResultRepository;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.service.validation.ValidationResultHasher;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.api.generated.model.ValidationResponse;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.validation.AssetValidationService;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.core.util.HashUtils;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
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
    private static final byte[] NON_RDF_PDF_BYTES = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
    private static final String NON_RDF_PDF_HASH = HashUtils.calculateSha256AsHex(NON_RDF_PDF_BYTES);

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
    @MockitoSpyBean
    private AssetValidationService assetValidationService;

    @Autowired
    private SchemaStore schemaStore;
    @Autowired
    private VerificationService verificationService;
    @Autowired
    private ValidationResultRepository validationResultRepository;
    @Autowired
    private ValidationResultHasher validationResultHasher;
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
    public void deleteTestAsset() {
        try {
            assetStorePublisher.deleteAsset(assetMeta.getAssetHash());
        } catch (NotFoundException e) {
            // expected if not created
        }
        try {
            assetStorePublisher.deleteAsset(NON_RDF_PDF_HASH);
        } catch (NotFoundException e) {
            // expected if test did not create a non-RDF PDF asset
        }
        validationResultRepository.deleteAll();
    }

    @Test
    public void readAssets_noAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAssets_invalidParams_returnsBadRequest() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.get("/assets?statuses=123")
              .with(csrf())
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAssets_validRequest_returnsSuccess() throws Exception {
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
    public void readAssetsByFilter_validFilter_returnsSuccess() throws Exception {
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
                    .queryParam("validators", String.join(",", assetMeta.getValidatorDids())))
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
        assertNotNull(assets.getItems().getFirst().getContent());
        assertNull(assets.getItems().getFirst().getMeta());

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
        assertNull(assets.getItems().getFirst().getContent());
        assertNotNull(assets.getItems().getFirst().getMeta());
    }

    @Test
    public void readAsset_noAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets/" + assetMeta.getAssetHash())
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAsset_nonExistentId_returnsNotFound() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}", "urn:uuid:00000000-0000-0000-0000-000000000099").with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readAsset_existingId_returnsOk() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());

        mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}", assetMeta.getId())
                .with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void readNonRdfAssetById_returnsOkWithMetadata() throws Exception {
        Instant now = Instant.now();

        AssetMetadata nonRdfMeta = new AssetMetadata(NON_RDF_PDF_HASH, null, AssetStatus.ACTIVE,
                TEST_ISSUER, null, now, now, new ContentAccessorBinary(NON_RDF_PDF_BYTES));
        nonRdfMeta.setContentType("application/pdf");
        nonRdfMeta.setFileSize((long) NON_RDF_PDF_BYTES.length);
        assetStorePublisher.storeUnverified(nonRdfMeta, "test.pdf");

        mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}", nonRdfMeta.getId())
                .with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    public void deleteAsset_noAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/{asset_hash}", assetMeta.getAssetHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void deleteAsset_noPermission_returnsForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/{asset_hash}", assetMeta.getAssetHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = ASSET_DELETE_WITH_PREFIX, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = "")})))
    public void deleteAsset_withoutIssuer_returnsForbidden() throws Exception {
      assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());
      mockMvc.perform(MockMvcRequestBuilders.delete("/assets/{asset_hash}", assetMeta.getAssetHash())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_DELETE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteAsset_nonExistent_returnsNotFound() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.delete("/assets/{asset_hash}", assetMeta.getAssetHash())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_DELETE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteAsset_validRequest_returnsOk() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());

        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/{asset_hash}", assetMeta.getAssetHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_DELETE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteAsset_hasValidationResults_cascadesDelete() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());

        ValidationResult vr = new ValidationResult();
        vr.setAssetIds(new String[]{assetMeta.getId()});
        vr.setValidatorIds(new String[]{"https://example.org/schema/1"});
        vr.setValidatorType(ValidatorType.SHACL);
        vr.setConforms(true);
        vr.setValidatedAt(java.time.Instant.now());
        vr.setContentHash(validationResultHasher.hash(vr));
        validationResultRepository.saveAndFlush(vr);
        assertEquals(1L, validationResultRepository.count(), "Precondition: 1 result seeded");

        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/{asset_hash}", assetMeta.getAssetHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        assertEquals(0L, validationResultRepository.count(), "Validation results must be deleted with the asset");
    }

    @Test
    public void addAsset_noAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void addAsset_noPermission_returnsForbidden() throws Exception {
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
    public void addAsset_withoutIssuer_returnsUnprocessableEntity() throws Exception {
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
    public void addAsset_validRequest_returnsCreated() throws Exception {
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
        assertTrue(asset.getWarnings().getFirst().contains("triple(s)"), "Warning should mention filtered triple count");
        assertTrue(asset.getWarnings().getFirst().contains("federated-catalogue/meta#"), "Warning should contain the reserved namespace URI");
        assetStorePublisher.deleteAsset(asset.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = RESOURCE_ISSUER)})))
    public void addResource_validRequest_returnsCreated() throws Exception {
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
    public void addAsset_validParticipant_returnsCreated() throws Exception {
        schemaStore.initializeDefaultSchemas();
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                .content(getMockFileDataAsString("default-participant.json"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        Asset asset = objectMapper.readValue(result.getResponse().getContentAsString(), Asset.class);
        assetStorePublisher.deleteAsset(asset.getAssetHash());
    }
    
    /**
     * POST /assets accepts a SHACL-invalid asset with default config (verifySchema=false).
     * Same asset as addParicipantReturnCreatedResponse, but with a SHACL shape loaded that would reject it.
     * Proves that schema validation is disabled in the upload flow.
     */
    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = PARTICIPANT_ISSUER)})))
    public void addAsset_shaclInvalid_returnsCreated() throws Exception {
        schemaStore.initializeDefaultSchemas();
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
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addAsset_duplicate_returnsConflict() throws Exception {
      String asset = getMockFileDataAsString(ASSET_FILE_NAME);
      ContentAccessorDirect contentAccessor = new ContentAccessorDirect(asset);
      String hash = HashUtils.calculateSha256AsHex(asset);

      // Use actual ID from credential (matches credentialSubject.@id)
      AssetMetadata assetMetadata = new AssetMetadata(TEST_ISSUER, TEST_ISSUER, new ArrayList<>(), contentAccessor);
      assetMetadata.setAssetHash(hash);

      assetStorePublisher.storeCredential(assetMetadata, getStaticVerificationResult());
      mockMvc.perform(MockMvcRequestBuilders
              .post("/assets")
              .content(asset)
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict());
      assetStorePublisher.deleteAsset(hash);
      assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
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
    public void revokeAsset_noAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/123/revoke")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void revokeAsset_noPermission_returnsForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/123/revoke")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeAsset_nonExistentAsset_returnsNotFound() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.post("/assets/{asset_hash}/revoke", assetMeta.getAssetHash())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeAsset_validRequest_returnsOk() throws Exception {
        final CredentialVerificationResult vr = new CredentialVerificationResult(Instant.now(), AssetStatus.ACTIVE.getValue(), "issuer",
            Instant.now(), "vhash", List.of(), List.of(), "", "");
        assetStorePublisher.storeCredential(assetMeta, vr);
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/{asset_hash}/revoke", assetMeta.getAssetHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // Requires both ASSET_UPDATE (revoke) and ASSET_CREATE (re-add) to test the composite flow.
    @Test
    @WithMockJwtAuth(authorities = {ASSET_CREATE_WITH_PREFIX, ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeAndReaddAsset_validFlow_returnsCreated() throws Exception {
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

        assertEquals(2, nodes.size());

        mockMvc.perform(MockMvcRequestBuilders.post("/assets/{asset_hash}/revoke", asset.getAssetHash())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
            	.with(csrf()))
                .andExpect(status().isOk());
        
        nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n {claimsGraphUri: [$uri]}) RETURN n", Map.of("uri", TEST_ISSUER)
        )).getResults();

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

        assertEquals(0, nodes.size());
        
        assetStorePublisher.deleteAsset(hash);
    }
    
    /**
     * Revoking a non-active asset returns 409 Conflict because only ACTIVE assets
     * can be revoked. Hash-based lookup finds the asset regardless of status,
     * then the business logic rejects the transition.
     *
     * <p>When versioning is introduced (CAT-FR-LM-01), DEPRECATED → REVOKED transitions
     * will be supported via version-specific endpoints.</p>
     */
    @Test
    @WithMockJwtAuth(authorities = {ASSET_UPDATE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeAsset_nonActiveStatus_returnsConflict() throws Exception {
        final CredentialVerificationResult vr = new CredentialVerificationResult(Instant.now(), AssetStatus.ACTIVE.getValue(), "issuer",
            Instant.now(), "vhash", List.of(), List.of(), "", "");
        AssetMetadata deprecatedMeta = createAssetMetadata();
        deprecatedMeta.setStatus(AssetStatus.DEPRECATED);
        assetStorePublisher.storeCredential(deprecatedMeta, vr);
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.post("/assets/{asset_hash}/revoke", deprecatedMeta.getAssetHash())
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict()).andReturn();
        Error error = objectMapper.readValue(result.getResponse().getContentAsString(), Error.class);
        assertEquals("The asset status cannot be changed because the asset metadata status is deprecated", error.getMessage());
        assetStorePublisher.deleteAsset(deprecatedMeta.getAssetHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addAsset_withReadOnlyPermission_returnsForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets")
                        .content(getMockFileDataAsString(ASSET_FILE_NAME))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser
    public void readAssets_noPermission_returnsForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {ADMIN_ALL_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = "admin-participant")})))
    public void addAsset_withAdminAllRole_returnsCreated() throws Exception {
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
    public void readAssets_withAdminAllRole_returnsOk() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    // ===== Validation Result Retrieval Endpoints - Security Tests =====

    @Test
    public void getAssetValidations_withoutAuth_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets/did:web:example.org:asset1/validations")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser  // No specific role
    public void getAssetValidations_withoutRequiredRole_shouldReturnForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets/did:web:example.org:asset1/validations")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void getAssetValidations_nonExistentAsset_returnsNotFound() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/assets/did:web:example.org:nonexistent/validations")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void getAssetValidations_existingAssetNoResults_returnsEmptyList() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());

        String body = mockMvc.perform(MockMvcRequestBuilders.get(
                        "/assets/" + assetMeta.getId() + "/validations")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<?> list = objectMapper.readValue(body, List.class);
        assertTrue(list.isEmpty(), "Expected empty list for asset with no stored validations");
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void getAssetValidations_withResults_returnsContentAndRespectsLimit() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());

        for (int i = 1; i <= 3; i++) {
            ValidationResult vr = new ValidationResult();
            vr.setAssetIds(new String[]{assetMeta.getId()});
            vr.setValidatorIds(new String[]{"https://example.org/schema/" + i});
            vr.setValidatorType(ValidatorType.SHACL);
            vr.setConforms(true);
            vr.setValidatedAt(java.time.Instant.now());
            vr.setContentHash(validationResultHasher.hash(vr));
            validationResultRepository.saveAndFlush(vr);
        }

        assertEquals(3L, validationResultRepository.count(), "Precondition: 3 results seeded");

        String allBody = mockMvc.perform(MockMvcRequestBuilders.get(
                        "/assets/" + assetMeta.getId() + "/validations")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<?> allResults = objectMapper.readValue(allBody, List.class);
        assertEquals(3, allResults.size(), "Expected 3 stored validation results");
        assertTrue(allBody.contains("SHACL"), "Expected SHACL validatorType in response");

        String limitedBody = mockMvc.perform(MockMvcRequestBuilders.get(
                        "/assets/" + assetMeta.getId() + "/validations")
                        .param("offset", "0").param("limit", "1")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<?> limited = objectMapper.readValue(limitedBody, List.class);
        assertEquals(1, limited.size(), "limit=1 should return exactly 1 result");
    }

    @Test
    public void getValidationResult_withoutAuth_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/validations/1")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser  // No specific role
    public void getValidationResult_withoutRequiredRole_shouldReturnForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/validations/1")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void getValidationResult_nonExistentId_returnsNotFound() throws Exception {
        // Returns 404 if validation result doesn't exist, 200 if it does
        mockMvc.perform(MockMvcRequestBuilders.get("/validations/999999")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());  // Non-existent validation result
    }

    @Test
    @WithMockUser(roles = {ASSET_READ})
    public void getValidationResult_existingId_returnsResultWithCorrectFields() throws Exception {
        ValidationResult vr = new ValidationResult();
        vr.setAssetIds(new String[]{"did:web:example.org:test-asset"});
        vr.setValidatorIds(new String[]{"https://example.org/schema/1"});
        vr.setValidatorType(ValidatorType.SHACL);
        vr.setConforms(true);
        vr.setValidatedAt(java.time.Instant.parse("2024-06-01T12:00:00Z"));
        vr.setContentHash(validationResultHasher.hash(vr));
        ValidationResult saved = validationResultRepository.saveAndFlush(vr);

        String body = mockMvc.perform(MockMvcRequestBuilders.get("/validations/" + saved.getId())
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        var dto = objectMapper.readValue(body, java.util.Map.class);
        assertEquals(saved.getId().intValue(), dto.get("id"));
        assertEquals(List.of("did:web:example.org:test-asset"), dto.get("assetIds"));
        assertEquals("SHACL", dto.get("validatorType"));
        assertEquals(true, dto.get("conforms"));
        assertNotNull(dto.get("contentHash"));
    }

    // ===== Validate endpoints — auth rejection =====

    @Test
    public void validateAsset_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\": [\"urn:uuid:00000000-0000-0000-0000-000000000001\"]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void validateAsset_withoutPermission_returnsForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\": [\"urn:uuid:00000000-0000-0000-0000-000000000001\"]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    public void validateAssets_withoutAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void validateAssets_withoutPermission_returnsForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    // ===== validateAssets — input validation =====

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void validateAssets_emptyBody_returnsBadRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void validateAssets_emptyAssetIds_returnsBadRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\": []}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void validateAssets_tooManyAssetIds_returnsBadRequest() throws Exception {
        List<String> twentyOneIds = java.util.stream.IntStream.range(0, 21)
                .mapToObj(i -> "urn:uuid:00000000-0000-0000-0000-" + String.format("%012d", i))
            .toList();
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("assetIds", twentyOneIds)))
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void validateAssets_missingContentType_returnsBadRequest() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .content("{\"assetIds\": [\"urn:uuid:00000000-0000-0000-0000-000000000001\"]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());
    }

    // ===== validateAsset / validateAssets — boundary responses =====

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void validateAssets_storedRdfAsset_returnsOk() throws Exception {
        ValidationResponse mockedResponse = new ValidationResponse();
        mockedResponse.setConforms(true);
        doReturn(mockedResponse).when(assetValidationService).validateAssets(any());

        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\": [\"urn:uuid:00000000-0000-0000-0000-000000000001\"]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void validateAssets_nonExistentAsset_returnsNotFound() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\": [\"urn:uuid:00000000-0000-0000-0000-does-not-exist\"]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void validateAsset_nonExistentAsset_returnsNotFound() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\": [\"urn:uuid:00000000-0000-0000-0000-does-not-exist\"]}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_READ_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void validateAsset_unsupportedContentType_returnsUnprocessableEntity() throws Exception {
        doThrow(new VerificationException("unsupported content type for asset"))
            .when(assetValidationService).validateAssets(any());

        mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assetIds\": [\"" + assetMeta.getId() + "\"], \"validateAgainstAllSchemas\": true}")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnprocessableEntity());
    }

    // ===== Helpers =====

    private static AssetMetadata createAssetMetadata() throws IOException {
        String credentialContent = getMockFileDataAsString(ASSET_FILE_NAME);
        String actualHash = HashUtils.calculateSha256AsHex(credentialContent);

        AssetMetadata assetMeta = new AssetMetadata();
        // Use a DID-style ID for tests — HTTP URLs with slashes break MockMvc path matching.
        // Real RDF credentials have their ID extracted by the verification service;
        // for direct storeCredential calls in tests we set the ID explicitly.
        assetMeta.setId("did:web:example.org:test-issuer");
        assetMeta.setIssuer(TEST_ISSUER);
        assetMeta.setAssetHash(actualHash);
        assetMeta.setStatus(AssetStatus.ACTIVE);
        assetMeta.setStatusDatetime(Instant.parse("2022-01-01T12:00:00Z"));
        assetMeta.setUploadDatetime(Instant.parse("2022-01-02T12:00:00Z"));
        assetMeta.setContentAccessor(new ContentAccessorDirect(credentialContent));
        return assetMeta;
    }

    private CredentialVerificationResult getStaticVerificationResult() {
      return verificationService.verifyCredential(assetMeta.getContentAccessor());
    }
}
