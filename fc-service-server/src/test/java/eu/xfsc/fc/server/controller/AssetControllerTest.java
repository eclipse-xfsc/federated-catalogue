package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_CREATE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_DELETE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_READ;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_UPDATE;
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
import java.util.UUID;
import java.util.stream.Collectors;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import eu.xfsc.fc.server.helper.KeycloakJwtTestSupport;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.jose4j.lang.JoseException;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:wiremock.properties", properties = {"graphstore.impl=neo4j"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@AutoConfigureWireMock(port = 0)
@Import(EmbeddedNeo4JConfig.class)
public class AssetControllerTest {
    private final static String TEST_ISSUER = "http://example.org/test-issuer";
    private final static String PARTICIPANT_ISSUER = "did:example:issuer";
    private final static String RESOURCE_ISSUER = "did:web:compliance.lab.gaia-x.eu";
    private final static String ASSET_FILE_NAME = "default-credential.json";
    private static final byte[] NON_RDF_PDF_BYTES = "%PDF-1.4 fake".getBytes(StandardCharsets.UTF_8);
    private static final String NON_RDF_PDF_HASH = HashUtils.calculateSha256AsHex(NON_RDF_PDF_BYTES);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final boolean WITH_CSRF_TOKEN = true;
    private static final boolean WITHOUT_CSRF_TOKEN = false;

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

    // ===== Real-JWT fine-grained RBAC matrix — WireMock-backed JWKS/issuer =====
    @Value("${wiremock.server.baseUrl}")
    private String keycloakBaseUrl;
    @Value("${keycloak.resource}")
    private String resourceId;
    private KeycloakJwtTestSupport jwtSupport;

    // credential-resource.json's credentialSubject "@id" is DID-style (no forward slashes), so it
    // round-trips safely as a single MockMvc path segment on PUT /assets/{id} — unlike TEST_ISSUER's
    // "http://..." form, which is only safe for assetMeta's direct-store (bypass-HTTP) usages below.
    private static final String RESOURCE_CREDENTIAL_FILE = "credential-resource.json";
    private static final String UPDATABLE_ASSET_ID = "did:example:fad49ec6-d488-4bf9-bae5-d0ffa62a9bd2";
    private static String resourceAssetHash;
    private static String updatedResourceAssetHash;

    @BeforeAll
    public void setup() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        jwtSupport = new KeycloakJwtTestSupport(keycloakBaseUrl);
        assetMeta = createAssetMetadata();
        String resourceCredential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);
        resourceAssetHash = HashUtils.calculateSha256AsHex(resourceCredential);
        updatedResourceAssetHash = HashUtils.calculateSha256AsHex(updatedResourceCredential(resourceCredential));
        setUpRbacJwtIssuer();
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
            assetStorePublisher.deleteAsset(resourceAssetHash);
        } catch (NotFoundException e) {
            // expected if the test did not create/update the credential-resource.json fixture asset
        }
        try {
            assetStorePublisher.deleteAsset(updatedResourceAssetHash);
        } catch (NotFoundException e) {
            // expected if the test did not successfully PUT the updated credential-resource.json content
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
    public void deleteAssetById_noAuth_returnsUnauthorized() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/by-id/{id}", assetMeta.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void deleteAssetById_noPermission_returnsForbidden() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/by-id/{id}", assetMeta.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {ASSET_DELETE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteAssetById_withPermission_returnsNoContent() throws Exception {
        assetStorePublisher.storeCredential(assetMeta, getStaticVerificationResult());

        mockMvc.perform(MockMvcRequestBuilders.delete("/assets/by-id/{id}", assetMeta.getId())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
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
     * POST /assets accepts a SHACL-invalid asset: SHACL validation is only available
     * on-demand via POST /assets/validate, never on the upload path.
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

    // ===== Real-JWT fine-grained role matrix =====
    // The single-role, no-roles, and role-combination tests below include a Spring-session CSRF
    // token (.with(csrf())) on every write, to isolate the RBAC dimension from CSRF entirely. The
    // tests further down that exercise all four roles together omit the CSRF token, matching how a
    // real bearer-token API client actually calls this service in production.

    @Test
    public void assetOperations_assetCreateRoleOnlyRealJwt_grantsOnlyCreateOperation() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_CREATE);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);

        MvcResult createResult = performCreateAsset(token, credential, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.CREATED.value(), createResult.getResponse().getStatus(),
            "ASSET_CREATE role via real JWT must grant POST /assets");

        MvcResult readResult = performReadAsset(UPDATABLE_ASSET_ID, token);
        MvcResult updateResult = performUpdateAsset(UPDATABLE_ASSET_ID, token, credential, WITH_CSRF_TOKEN);
        MvcResult deleteResult = performDeleteAsset(resourceAssetHash, token, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.FORBIDDEN.value(), readResult.getResponse().getStatus(),
            "ASSET_CREATE alone must not grant GET /assets/{id}");
        assertEquals(HttpStatus.FORBIDDEN.value(), updateResult.getResponse().getStatus(),
            "ASSET_CREATE alone must not grant PUT /assets/{id}");
        assertEquals(HttpStatus.FORBIDDEN.value(), deleteResult.getResponse().getStatus(),
            "ASSET_CREATE alone must not grant DELETE /assets/{asset_hash}");
    }

    @Test
    public void assetOperations_assetReadRoleOnlyRealJwt_grantsOnlyReadOperation() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_READ);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);
        storeResourceAsset(credential);

        MvcResult readResult = performReadAsset(UPDATABLE_ASSET_ID, token);

        assertEquals(HttpStatus.OK.value(), readResult.getResponse().getStatus(),
            "ASSET_READ role via real JWT must grant GET /assets/{id}");

        MvcResult createResult = performCreateAsset(token, credential, WITH_CSRF_TOKEN);
        MvcResult updateResult = performUpdateAsset(UPDATABLE_ASSET_ID, token, credential, WITH_CSRF_TOKEN);
        MvcResult deleteResult = performDeleteAsset(resourceAssetHash, token, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.FORBIDDEN.value(), createResult.getResponse().getStatus(),
            "ASSET_READ alone must not grant POST /assets");
        assertEquals(HttpStatus.FORBIDDEN.value(), updateResult.getResponse().getStatus(),
            "ASSET_READ alone must not grant PUT /assets/{id}");
        assertEquals(HttpStatus.FORBIDDEN.value(), deleteResult.getResponse().getStatus(),
            "ASSET_READ alone must not grant DELETE /assets/{asset_hash}");
    }

    @Test
    public void assetOperations_assetUpdateRoleOnlyRealJwt_grantsOnlyUpdateOperation() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_UPDATE);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);
        storeResourceAsset(credential);

        MvcResult updateResult = performUpdateAsset(UPDATABLE_ASSET_ID, token, updatedResourceCredential(credential), WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.OK.value(), updateResult.getResponse().getStatus(),
            "ASSET_UPDATE role via real JWT must grant PUT /assets/{id}");

        MvcResult createResult = performCreateAsset(token, credential, WITH_CSRF_TOKEN);
        MvcResult readResult = performReadAsset(UPDATABLE_ASSET_ID, token);
        MvcResult deleteResult = performDeleteAsset(resourceAssetHash, token, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.FORBIDDEN.value(), createResult.getResponse().getStatus(),
            "ASSET_UPDATE alone must not grant POST /assets");
        assertEquals(HttpStatus.FORBIDDEN.value(), readResult.getResponse().getStatus(),
            "ASSET_UPDATE alone must not grant GET /assets/{id}");
        assertEquals(HttpStatus.FORBIDDEN.value(), deleteResult.getResponse().getStatus(),
            "ASSET_UPDATE alone must not grant DELETE /assets/{asset_hash}");
    }

    @Test
    public void assetOperations_assetDeleteRoleOnlyRealJwt_grantsOnlyDeleteOperation() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_DELETE);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);
        storeResourceAsset(credential);

        MvcResult deleteResult = performDeleteAsset(resourceAssetHash, token, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.OK.value(), deleteResult.getResponse().getStatus(),
            "ASSET_DELETE role via real JWT must grant DELETE /assets/{asset_hash}");

        MvcResult createResult = performCreateAsset(token, credential, WITH_CSRF_TOKEN);
        MvcResult readResult = performReadAsset(UPDATABLE_ASSET_ID, token);
        MvcResult updateResult = performUpdateAsset(UPDATABLE_ASSET_ID, token, credential, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.FORBIDDEN.value(), createResult.getResponse().getStatus(),
            "ASSET_DELETE alone must not grant POST /assets");
        assertEquals(HttpStatus.FORBIDDEN.value(), readResult.getResponse().getStatus(),
            "ASSET_DELETE alone must not grant GET /assets/{id}");
        assertEquals(HttpStatus.FORBIDDEN.value(), updateResult.getResponse().getStatus(),
            "ASSET_DELETE alone must not grant PUT /assets/{id}");
    }

    @Test
    public void assetOperations_noFineGrainedRolesRealJwt_allOperationsForbidden() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);
        storeResourceAsset(credential);

        MvcResult createResult = performCreateAsset(token, credential, WITH_CSRF_TOKEN);
        MvcResult readResult = performReadAsset(UPDATABLE_ASSET_ID, token);
        MvcResult updateResult = performUpdateAsset(UPDATABLE_ASSET_ID, token, credential, WITH_CSRF_TOKEN);
        MvcResult deleteResult = performDeleteAsset(resourceAssetHash, token, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.FORBIDDEN.value(), createResult.getResponse().getStatus(),
            "A user with none of the four fine-grained roles must not be able to create an asset");
        assertEquals(HttpStatus.FORBIDDEN.value(), readResult.getResponse().getStatus(),
            "A user with none of the four fine-grained roles must not be able to read an asset");
        assertEquals(HttpStatus.FORBIDDEN.value(), updateResult.getResponse().getStatus(),
            "A user with none of the four fine-grained roles must not be able to update an asset");
        assertEquals(HttpStatus.FORBIDDEN.value(), deleteResult.getResponse().getStatus(),
            "A user with none of the four fine-grained roles must not be able to delete an asset");
    }

    /**
     * Neither the single-role tests above nor the all-four-roles tests below prove that a
     * genuine subset of roles grants exactly the union of its operations. This test grants
     * ASSET_CREATE and ASSET_READ together and asserts POST/GET succeed while PUT/DELETE
     * (the ungranted roles) are forbidden, closing that coverage gap.
     */
    @Test
    public void assetOperations_createAndReadRolesOnlyRealJwt_grantsOnlyCreateAndReadOperations() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_CREATE, ASSET_READ);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);

        MvcResult createResult = performCreateAsset(token, credential, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.CREATED.value(), createResult.getResponse().getStatus(),
            "ASSET_CREATE + ASSET_READ together must grant POST /assets");

        MvcResult readResult = performReadAsset(UPDATABLE_ASSET_ID, token);

        assertEquals(HttpStatus.OK.value(), readResult.getResponse().getStatus(),
            "ASSET_CREATE + ASSET_READ together must grant GET /assets/{id}");

        MvcResult updateResult = performUpdateAsset(UPDATABLE_ASSET_ID, token, updatedResourceCredential(credential), WITH_CSRF_TOKEN);
        MvcResult deleteResult = performDeleteAsset(resourceAssetHash, token, WITH_CSRF_TOKEN);

        assertEquals(HttpStatus.FORBIDDEN.value(), updateResult.getResponse().getStatus(),
            "ASSET_CREATE + ASSET_READ together must not grant PUT /assets/{id}");
        assertEquals(HttpStatus.FORBIDDEN.value(), deleteResult.getResponse().getStatus(),
            "ASSET_CREATE + ASSET_READ together must not grant DELETE /assets/{asset_hash}");
    }

    // ===== All four fine-grained roles together — no simulated CSRF token, matching a real =====
    // ===== bearer-token API client's actual calling convention in production ==================

    /**
     * A real bearer-token client with all four fine-grained roles (and therefore no
     * Spring-session CSRF token — bearer-token clients never have one) must be able to create an asset.
     */
    @Test
    public void addAsset_realBearerTokenClientWithAllFourRoles_succeeds() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_CREATE, ASSET_READ, ASSET_UPDATE, ASSET_DELETE);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);

        MvcResult result = performCreateAsset(token, credential, WITHOUT_CSRF_TOKEN);

        assertEquals(HttpStatus.CREATED.value(), result.getResponse().getStatus(),
            "A real bearer-token client (no Spring-session CSRF token) with ASSET_CREATE must be able to create an asset");
    }

    /**
     * A real bearer-token client with all four fine-grained roles must be able to read an asset.
     */
    @Test
    public void getAsset_realBearerTokenClientWithAllFourRoles_succeeds() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_CREATE, ASSET_READ, ASSET_UPDATE, ASSET_DELETE);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);
        storeResourceAsset(credential);

        MvcResult result = performReadAsset(UPDATABLE_ASSET_ID, token);

        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus(),
            "A real bearer-token client with ASSET_READ must be able to read an asset");
    }

    /**
     * A real bearer-token client with all four fine-grained roles must be able to update an asset.
     */
    @Test
    public void updateAsset_realBearerTokenClientWithAllFourRoles_succeeds() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_CREATE, ASSET_READ, ASSET_UPDATE, ASSET_DELETE);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);
        storeResourceAsset(credential);

        MvcResult result = performUpdateAsset(UPDATABLE_ASSET_ID, token, updatedResourceCredential(credential), WITHOUT_CSRF_TOKEN);

        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus(),
            "A real bearer-token client (no Spring-session CSRF token) with ASSET_UPDATE must be able to update an asset");
    }

    /**
     * A real bearer-token client with all four fine-grained roles must be able to delete an asset.
     */
    @Test
    public void deleteAsset_realBearerTokenClientWithAllFourRoles_succeeds() throws Exception {
        String token = mintFineGrainedAssetToken(RESOURCE_ISSUER, ASSET_CREATE, ASSET_READ, ASSET_UPDATE, ASSET_DELETE);
        String credential = getMockFileDataAsString(RESOURCE_CREDENTIAL_FILE);
        storeResourceAsset(credential);

        MvcResult result = performDeleteAsset(resourceAssetHash, token, WITHOUT_CSRF_TOKEN);

        assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus(),
            "A real bearer-token client (no Spring-session CSRF token) with ASSET_DELETE must be able to delete an asset");
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

    /**
     * Produces a byte-distinct variant of the credential-resource.json fixture (same asset IRI and
     * issuer, different literal value) so PUT /assets/{id} tests exercise a genuine content update
     * rather than resubmitting a byte-identical payload, which the store layer treats as a duplicate.
     */
    private static String updatedResourceCredential(String originalCredential) {
        return originalCredential.replace("ExampleResourceForFederatedCatalogue",
                "ExampleResourceForFederatedCatalogue (updated)");
    }

    private CredentialVerificationResult getStaticVerificationResult() {
        return verificationService.verifyCredential(assetMeta.getContentAccessor());
    }

    /**
     * Directly stores the {@code credential-resource.json} fixture (bypassing HTTP/authorization),
     * so role-isolation tests can assert a single operation's authorization outcome without first
     * needing the ASSET_CREATE role to establish that precondition.
     */
    private void storeResourceAsset(String credentialContent) {
        AssetMetadata meta = new AssetMetadata(UPDATABLE_ASSET_ID, RESOURCE_ISSUER, new ArrayList<>(),
                new ContentAccessorDirect(credentialContent));
        meta.setStatus(AssetStatus.ACTIVE);
        Instant now = Instant.now();
        meta.setStatusDatetime(now);
        meta.setUploadDatetime(now);
        assetStorePublisher.storeCredential(meta, verificationService.verifyCredential(meta.getContentAccessor()));
    }

    /**
     * Registers a WireMock-backed OIDC discovery document and JWKS so that real, RSA-signed JWTs
     * (minted by {@link #mintFineGrainedAssetToken}) are decoded and converted by the production
     * {@code CustomJwtAuthenticationConverter} pipeline — unlike {@code @WithMockJwtAuth}, which
     * injects a pre-built authority list directly into the security context.
     */
    private void setUpRbacJwtIssuer() throws IOException {
        try {
            jwtSupport.setUpOidcAndJwks("rbac-test-k1");
        } catch (JoseException ex) {
            throw new IllegalStateException("Failed to set up OIDC and JWKS", ex);
        }
    }

    /**
     * Mints a real, RSA-signed JWT carrying the given fine-grained roles under
     * {@code resource_access.<keycloak.resource>.roles}, matching the shape a Keycloak-issued
     * access token has in production. Routed through the WireMock JWKS registered in
     * {@link #setUpRbacJwtIssuer()}, so requests bearing this token exercise the real
     * {@code CustomJwtAuthenticationConverter}, not a test double.
     */
    private String mintFineGrainedAssetToken(String participantId, String... roles) throws JoseException {
        return jwtSupport.mintToken(resourceId, List.of(roles), participantId);
    }

    /**
     * Builds and executes a real-JWT POST /assets request. {@code includeCsrfToken} isolates the
     * CSRF-filter dimension from the RBAC dimension: a real bearer-token client never holds a
     * Spring-session CSRF token, so {@code false} reproduces genuine client behavior while
     * {@code true} isolates whether the role check itself behaves correctly once CSRF is out of the way.
     */
    private MvcResult performCreateAsset(String token, String credential, boolean includeCsrfToken) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.post("/assets")
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
                .content(credential)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
        return mockMvc.perform(includeCsrfToken ? request.with(csrf()) : request).andReturn();
    }

    private MvcResult performReadAsset(String id, String token) throws Exception {
        return mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}", id)
                        .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token))
                .andReturn();
    }

    private MvcResult performUpdateAsset(String id, String token, String credential, boolean includeCsrfToken) throws
            Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.put("/assets/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
                .content(credential)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
        return mockMvc.perform(includeCsrfToken ? request.with(csrf()) : request).andReturn();
    }

    private MvcResult performDeleteAsset(String assetHash, String token, boolean includeCsrfToken) throws Exception {
        MockHttpServletRequestBuilder request = MockMvcRequestBuilders.delete("/assets/{asset_hash}", assetHash)
                .header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON);
        return mockMvc.perform(includeCsrfToken ? request.with(csrf()) : request).andReturn();
    }
}
