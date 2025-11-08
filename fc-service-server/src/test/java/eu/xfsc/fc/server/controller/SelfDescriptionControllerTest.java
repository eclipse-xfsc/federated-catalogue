package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestCommonConstants.SD_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.Error;
import eu.xfsc.fc.api.generated.model.SelfDescription;
import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.api.generated.model.SelfDescriptions;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.core.util.HashUtils;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class SelfDescriptionControllerTest {
    private final static String TEST_ISSUER = "http://example.org/test-issuer";
    private final static String SD_FILE_NAME = "default-sd.json";

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
    private SelfDescriptionStore sdStorePublisher;
    // can't remove it for some reason, many tests fails with auth error
    @MockitoSpyBean(name = "schemaFileStore") // "sdFileStore")
    private FileStore fileStore;

    @Autowired
    private SchemaStore schemaStore;
    @Autowired
    private VerificationService verificationService;

    private static SelfDescriptionMetadata sdMeta;
    
    @BeforeAll
    public void setup() throws IOException {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        sdMeta = createSdMetadata();
    }

    @AfterAll
    public void storageSelfCleaning() throws IOException {
        embeddedDatabaseServer.close();
    }
    
    @AfterEach
    public void deleteTestSD() throws IOException {
        try {
            sdStorePublisher.deleteSelfDescription(sdMeta.getSdHash());
        } catch (NotFoundException e) {
            // expected
        }
    }
    
    @Test
    public void readSDsShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void readSDsShouldReturnBadRequestResponse() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions?statuses=123")
              .with(csrf())
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    public void readSDsShouldReturnSuccessResponse() throws Exception {
        sdStorePublisher.storeSelfDescription(sdMeta, getStaticVerificationResult());
        MvcResult result =  mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions")
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
            .andReturn();

        SelfDescriptions selfDescriptions = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescriptions.class);
        assertNotNull(selfDescriptions);
        assertEquals(1, selfDescriptions.getItems().size());
        assertEquals(1, selfDescriptions.getTotalCount());
    }

    @Test
    @WithMockUser
    public void readSDsByFilterShouldReturnSuccessResponse() throws Exception {
        sdStorePublisher.storeSelfDescription(sdMeta, getStaticVerificationResult());
        
        MvcResult result =  mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions")
                        .accept(MediaType.APPLICATION_JSON)
                .queryParam("issuers", sdMeta.getIssuer()))  
                .andExpect(status().isOk())
            .andReturn();
        SelfDescriptions selfDescriptions = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescriptions.class);
        assertNotNull(selfDescriptions);
        assertEquals(1, selfDescriptions.getItems().size());
        assertEquals(1, selfDescriptions.getTotalCount());
        
        String statusTr = sdMeta.getStatusDatetime().minusSeconds(5).toString() + "/" + sdMeta.getStatusDatetime().plusSeconds(5).toString();
        result =  mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions")
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("hashes", sdMeta.getSdHash())  
                .queryParam("statusTimerange", statusTr))  
                .andExpect(status().isOk())
                .andReturn();
        selfDescriptions = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescriptions.class);
        assertNotNull(selfDescriptions);
        assertEquals(1, selfDescriptions.getItems().size());
        assertEquals(1, selfDescriptions.getTotalCount());

        String uploadTr = sdMeta.getUploadDatetime().minusSeconds(5).toString() + "/" + sdMeta.getUploadDatetime().plusSeconds(5).toString();
        result =  mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions")
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("ids", sdMeta.getId())  
                .queryParam("uploadTimerange", uploadTr))  
                .andExpect(status().isOk())
                .andReturn();
        selfDescriptions = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescriptions.class);
        assertNotNull(selfDescriptions);
        assertEquals(1, selfDescriptions.getItems().size());
        assertEquals(1, selfDescriptions.getTotalCount());

        if (sdMeta.getValidatorDids() != null && !sdMeta.getValidatorDids().isEmpty()) {
            result =  mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions")
                    .accept(MediaType.APPLICATION_JSON)
                    .queryParam("validators", sdMeta.getValidatorDids().stream().collect(Collectors.joining(","))))
                    .andExpect(status().isOk())
                    .andReturn();
            selfDescriptions = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescriptions.class);
            assertNotNull(selfDescriptions);
            assertEquals(1, selfDescriptions.getItems().size());
            assertEquals(1, selfDescriptions.getTotalCount());
        }

        result =  mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions")
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("withMeta", "false") //default is true
                .queryParam("withContent", "true"))  //default is false
            .andExpect(status().isOk())
            .andReturn();
        selfDescriptions = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescriptions.class);
        assertNotNull(selfDescriptions);
        assertEquals(1, selfDescriptions.getItems().size());
        assertEquals(1, selfDescriptions.getTotalCount());
        assertNotNull(selfDescriptions.getItems().get(0).getContent());
        assertNull(selfDescriptions.getItems().get(0).getMeta());

        result =  mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions")
                .accept(MediaType.APPLICATION_JSON)
                .queryParam("withMeta", "true") //default is true
                .queryParam("withContent", "false"))  //default is false
            .andExpect(status().isOk())
            .andReturn();
        selfDescriptions = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescriptions.class);
        assertNotNull(selfDescriptions);
        assertEquals(1, selfDescriptions.getItems().size());
        assertEquals(1, selfDescriptions.getTotalCount());
        assertNull(selfDescriptions.getItems().get(0).getContent());
        assertNotNull(selfDescriptions.getItems().get(0).getMeta());
    }

    @Test
    public void readSDByHashShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions/" + sdMeta.getSdHash())
                        .with(csrf())
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void readSDByHashShouldReturnNotFoundResponse() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions/123").with(csrf()))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    public void readSDByHashShouldReturnSuccessResponse() throws Exception {
        sdStorePublisher.storeSelfDescription(sdMeta, getStaticVerificationResult());

        mockMvc.perform(MockMvcRequestBuilders.get("/self-descriptions/" + sdMeta.getSdHash())
                .with(csrf()))
                //.accept("application/ld+json"))
            .andExpect(status().isOk());
    }

    @Test
    public void deleteSDhShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/self-descriptions/" + sdMeta.getSdHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void deleteSDReturnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.delete("/self-descriptions/" + sdMeta.getSdHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = SD_ADMIN_ROLE_WITH_PREFIX, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = "")})))
    public void deleteSdWithoutIssuerReturnForbiddenResponse() throws Exception {
      sdStorePublisher.storeSelfDescription(sdMeta, getStaticVerificationResult());
      mockMvc.perform(MockMvcRequestBuilders.delete("/self-descriptions/" + sdMeta.getSdHash())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteSDReturnNotFoundResponse() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.delete("/self-descriptions/" + sdMeta.getSdHash())
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void deleteSDReturnSuccessResponse() throws Exception {
        sdStorePublisher.storeSelfDescription(sdMeta, getStaticVerificationResult());

        mockMvc.perform(MockMvcRequestBuilders.delete("/self-descriptions/" + sdMeta.getSdHash())
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void addSDShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void addSDReturnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                        .content(getMockFileDataAsString(SD_FILE_NAME))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addSDWithoutIssuerReturnUnprocessableEntity() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
              .content(getMockFileDataAsString("sd-without-issuer.json"))
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addSDReturnCreatedResponse() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                .content(getMockFileDataAsString(SD_FILE_NAME))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        sdStorePublisher.deleteSelfDescription(sd.getSdHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addResourceReturnCreatedResponse() throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                .content(getMockFileDataAsString("sd_resource.json"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        sdStorePublisher.deleteSelfDescription(sd.getSdHash());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addParicipantReturnCreatedResponse() throws Exception {
        schemaStore.addSchema(getAccessor("mock-data/gax-test-ontology.ttl"));
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                .content(getMockFileDataAsString("default_participant.json"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        sdStorePublisher.deleteSelfDescription(sd.getSdHash());
        schemaStore.clear();
    }
    
    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addDuplicateSDReturnConflictWithSdStorage() throws Exception {
      String sd = getMockFileDataAsString(SD_FILE_NAME);
      ContentAccessorDirect contentAccessor = new ContentAccessorDirect(sd);

      SelfDescriptionMetadata sdMetadata = new SelfDescriptionMetadata("id123", TEST_ISSUER, new ArrayList<>(), contentAccessor);

      sdStorePublisher.storeSelfDescription(sdMetadata, getStaticVerificationResult());
      mockMvc.perform(MockMvcRequestBuilders
              .post("/self-descriptions")
              .content(sd)
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isConflict());
      sdStorePublisher.deleteSelfDescription(sdMetadata.getSdHash());
      assertThrows(NotFoundException.class, () -> sdStorePublisher.getByHash(sdMetadata.getSdHash()));
    }

    // TODO: 05.09.2022 Need to add a test to check the correct scenario with graph storage when it is added
    //@Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void addSDFailedThenAllTransactionRolledBack() throws Exception {
        ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
        //doThrow((new IOException("Some server exception")))
        //    .when(fileStore).storeFile(hashCaptor.capture(), any());

        mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                .content(getMockFileDataAsString(SD_FILE_NAME))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isInternalServerError());

        String hash = hashCaptor.getValue();

        //assertThrowsExactly(FileNotFoundException.class,
        //    () -> fileStore.readFile(hash));
        assertThrows(NotFoundException.class, () -> sdStorePublisher.getByHash(hash));
    }

    @Test
    public void revokeSDhShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions/123/revoke")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void revokeSDReturnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions/123/revoke")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeSDReturnNotFound() throws Exception {
      mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions/" + sdMeta.getSdHash() + "/revoke")
              .with(csrf())
              .contentType(MediaType.APPLICATION_JSON)
              .accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isNotFound());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeSDReturnSuccessResponse() throws Exception {
        final VerificationResult vr = new VerificationResult(Instant.now(), SelfDescriptionStatus.ACTIVE.getValue(), "issuer", 
                Instant.now(), "vhash", List.of(), List.of());
        sdStorePublisher.storeSelfDescription(sdMeta, vr);
//        sdStore.storeSelfDescription(sdMeta, getStaticVerificationResult());
        mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions/" + sdMeta.getSdHash() + "/revoke")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeThenAddSDReturnCorrectResponse() throws Exception {
        String content = getMockFileDataAsString(SD_FILE_NAME);
        String hash = HashUtils.calculateSha256AsHex(content);
        try {
          sdStorePublisher.deleteSelfDescription(hash);
        } catch (NotFoundException ex) {
            // expected
        }
        
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                .content(content)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
            	.with(csrf()))
            .andExpect(status().isCreated())
            .andReturn();

        SelfDescription sd = objectMapper.readValue(result.getResponse().getContentAsString(), SelfDescription.class);
        Assertions.assertEquals(hash, sd.getSdHash());

        List<Map<String, Object>> nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n {claimsGraphUri: [$uri]}) RETURN n", Map.of("uri", TEST_ISSUER)
        )).getResults();
        log.debug("revokeThenAddSDReturnCorrectResponse.1; got {} nodes", nodes.size());
        Assertions.assertEquals(2, nodes.size());

        mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions/" + hash + "/revoke")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
            	.with(csrf()))
                .andExpect(status().isOk());
        
        nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n {claimsGraphUri: [$uri]}) RETURN n", Map.of("uri", TEST_ISSUER)
        )).getResults();
        log.debug("revokeThenAddSDReturnCorrectResponse.2; got {} nodes", nodes.size());
        Assertions.assertEquals(0, nodes.size());
        
        result = mockMvc.perform(MockMvcRequestBuilders.post("/self-descriptions")
                .content(content)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
            	.with(csrf()))
            .andExpect(status().isConflict())
            .andReturn();

        nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n {claimsGraphUri: [$uri]}) RETURN n", Map.of("uri", TEST_ISSUER)
        )).getResults();
        log.debug("revokeThenAddSDReturnCorrectResponse.3; got {} nodes", nodes.size());
        Assertions.assertEquals(0, nodes.size());
        
        // this call fails for some reason. why cannot we delete revoked SD? 
        sdStorePublisher.deleteSelfDescription(hash);
    }
    
    @Test
    @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = TEST_ISSUER)})))
    public void revokeSdWithNotActiveStatusReturnConflictResponse() throws Exception {
        final VerificationResult vr = new VerificationResult(Instant.now(), SelfDescriptionStatus.ACTIVE.getValue(), "issuer", 
                Instant.now(), "vhash", List.of(), List.of());
        SelfDescriptionMetadata metadata = sdMeta;
        metadata.setStatus(SelfDescriptionStatus.DEPRECATED);
        sdStorePublisher.storeSelfDescription(metadata, vr);
        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.post("/self-descriptions/" + sdMeta.getSdHash() + "/revoke")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict()).andReturn();
        Error error = objectMapper.readValue(result.getResponse().getContentAsString(), Error.class);
        assertEquals("The SD status cannot be changed because the SD Metadata status is deprecated", error.getMessage());
        sdStorePublisher.deleteSelfDescription(metadata.getSdHash());
    }

    private static SelfDescriptionMetadata createSdMetadata() throws IOException {
        SelfDescriptionMetadata sdMeta = new SelfDescriptionMetadata();
        sdMeta.setId("test id");
        sdMeta.setIssuer(TEST_ISSUER);
        sdMeta.setSdHash(HashUtils.calculateSha256AsHex("test hash"));
        sdMeta.setStatus(SelfDescriptionStatus.ACTIVE);
        sdMeta.setStatusDatetime(Instant.parse("2022-01-01T12:00:00Z"));
        sdMeta.setUploadDatetime(Instant.parse("2022-01-02T12:00:00Z"));
        sdMeta.setSelfDescription(new ContentAccessorDirect(getMockFileDataAsString(SD_FILE_NAME)));
        return sdMeta;
    }

    private VerificationResultOffering getStaticVerificationResult() {
        return verificationService.verifyOfferingSelfDescription(sdMeta.getSelfDescription());
    }
}
