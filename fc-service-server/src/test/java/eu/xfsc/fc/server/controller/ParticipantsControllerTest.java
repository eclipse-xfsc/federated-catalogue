package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.helper.UserServiceHelper.getAllRoles;
import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.TestCommonConstants.SD_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.neo4j.harness.Neo4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringArrayClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Participants;
import eu.xfsc.fc.api.generated.model.SelfDescriptions;
import eu.xfsc.fc.api.generated.model.User;
import eu.xfsc.fc.api.generated.model.UserProfiles;
import eu.xfsc.fc.core.dao.impl.ParticipantDaoImpl;
import eu.xfsc.fc.core.dao.impl.UserDaoImpl;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.ParticipantMetaData;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResultParticipant;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStoreImpl;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Import(EmbeddedNeo4JConfig.class)
public class ParticipantsControllerTest {

  @Value("${keycloak.resource}")
  private String clientId;
  @Autowired
  private WebApplicationContext context;
  @Autowired
  private SelfDescriptionStoreImpl sdStorePublisher;
  @Autowired
  private Neo4j embeddedDatabaseServer;
  @Autowired
  private GraphStore graphStore;
  @Autowired
  private VerificationService verificationService;
  @Autowired
  private UserDaoImpl userDao;
  @Autowired
  private ObjectMapper objectMapper;
  @Autowired
  private MockMvc mockMvc;
  @Autowired
  private SchemaStore schemaStore;

  @MockitoBean
  private KeycloakBuilder builder;
  @MockitoBean
  private Keycloak keycloak;
  @MockitoBean
  private RealmResource realmResource;
  @MockitoBean
  private ClientsResource clientsResource;
  @MockitoBean
  private ClientResource clientResource;
  @MockitoBean
  private GroupsResource groupsResource;
  @MockitoBean
  private GroupResource groupResource;
  @MockitoBean
  private UsersResource usersResource;
  @MockitoBean
  private UserResource userResource;
  @MockitoBean
  private RolesResource rolesResource;
  @MockitoBean
  private RoleMappingResource roleMappingResource;
  @MockitoBean
  private RoleScopeResource roleScopeResource;

  private final String userId = "ae366624-8371-401d-b2c4-518d2f308a15";
  private final String DEFAULT_PARTICIPANT_FILE = "default_participant.json";
  private final String ALTERNATIVE_PARTICIPANT_FILE = "alternative_participant.json";
  private final String ALTERNATIVE2_PARTICIPANT_FILE = "alternative2_participant.json";
  private final String UNIQUE_PARTICIPANT_FILE = "unique_participant.json";
  private final String PUBLIC_KEY_AS_JWK = "{\"kty\":\"RSA\",\"e\":\"AQAB\",\"alg\":\"PS256\",\"n\":\"0nYZU6EuuzHKBCzkcBZqsMkVZXngYO7VujfLU_4ys7onF4HxTJPP3OGKEjbjbMgmpa7vKaWRomt_XXTjemA3r3f5t8bj0IoqFfvbTIq65GUIIh4y2mVbomdcQLRK2Auf79vDiqiONknTSstoPjAiCg6t6z_KruGFZbDOhYkZwqrjGnmB_LfFSlpeLwkQQ-5dVLhhXkImmWhnACoAo8ECny24Ap7wLbN9i9o1fNSz2uszACj0zxFhl3NGunHFUm3YkGd0URvoToXpK9a4zfihSUxHjeT0_7a9puVF4E3w1AAjSh4nV3pLE0cJyDITVb2M4d3m9tjjz_3XwjYiAAJ1MKVBSKDM27pexRFCJj_Dvb-dr-AImhqBhPDHn_gjdaRZIVoADC4zwBULkpvUaUIKmNFyYOjDYWWTBzTf4Gs9QL5adlVfVyK14MZPBOyq-cqIIymgp6A5_R3hKnCCBP8C_S0-VDidhI6Pr5VJPx9DydI0eB2DiOyOZvbfg7sKVkJXFUEJRiBTMhujyjYqeTtCHjCFHctZVQ8hU279eyk7mpmpDrktfCFJFi-00ZzQWTgtzBoGhke5hj0hjtG1n4jN6BfypdT5oB-DeXl2P1hp_hNC9I5gveWUYHAqN4VKve_52A3ub8vBlISQhEUeZoFUterTiDA3NyK7wsj_V7-KM6U\"}";

  @BeforeAll
  public void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    schemaStore.addSchema(getAccessor("mock-data/gax-test-ontology.ttl"));
  }

  @AfterAll
  public void storageSelfCleaning() throws IOException {
    schemaStore.clear();
    embeddedDatabaseServer.close();
  }

  @Test
  public void participantAuthShouldReturnUnauthorizedResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/participants").with(csrf())).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  public void participantAuthShouldReturnForbiddenResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/participants").with(csrf())).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(10)
  public void addParticipantShouldReturnCreatedResponse() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_CREATED, part);

    deleteParticipantFromSdStore(part);

    String response = mockMvc
            .perform(MockMvcRequestBuilders.post("/participants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(json))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ParticipantMetaData partResult = objectMapper.readValue(response, ParticipantMetaData.class);
    assertNotNull(part);
    assertEquals("did:example:issuer", partResult.getId());
    assertEquals("did:example:holder", partResult.getName());
    assertEquals(PUBLIC_KEY_AS_JWK, partResult.getPublicKey());
    assertEquals(part.getSelfDescription(), partResult.getSelfDescription());

    assertDoesNotThrow(() -> sdStorePublisher.getByHash(part.getSdHash()));
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(10)
  public void addDuplicateParticipantShouldReturnConflictResponse() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_CREATED, part);

    deleteParticipantFromSdStore(part);

    ContentAccessorDirect contentAccessor = new ContentAccessorDirect(json);
    VerificationResultParticipant verResult = verificationService.verifyParticipantSelfDescription(contentAccessor);
    SelfDescriptionMetadata sdMetadata = new SelfDescriptionMetadata(contentAccessor, verResult);
    sdStorePublisher.storeSelfDescription(sdMetadata, verResult);

    List<Map<String, Object>> nodes = graphStore.queryData(new GraphQuery(
            "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
            Map.of("graphUri", "did:example:issuer")
    )).getResults();
    log.debug("addDuplicateParticipantShouldReturnConflictResponse-1; got {} nodes", nodes.size());
    Assertions.assertEquals(1, nodes.size());

    mockMvc
            .perform(MockMvcRequestBuilders.post("/participants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf())
                    .content(json))
            .andExpect(status().isConflict());

    nodes = graphStore.queryData(new GraphQuery(
            "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
            Map.of("graphUri", "did:example:issuer")
    )).getResults();
    log.debug("addDuplicateParticipantShouldReturnConflictResponse-2; got {} nodes", nodes.size());
    Assertions.assertEquals(1, nodes.size());
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(15)
  public void getParticipantsShouldReturnEmptyResults() throws Exception {
    setupKeycloak(HttpStatus.SC_OK, null);
    MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/participants")
                    .contentType(MediaType.APPLICATION_JSON)
            		.with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    Participants parts = objectMapper.readValue(result.getResponse().getContentAsString(), Participants.class);
    assertNotNull(parts);
    assertEquals(0, parts.getItems().size());
    assertEquals(0, parts.getTotalCount());
  }
  
  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(20)
  public void getParticipantShouldReturnSuccessResponse() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_OK, part);
    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());

    mockMvc
            .perform(MockMvcRequestBuilders.get("/participants/{participantId}", partId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(authorities = SD_ADMIN_ROLE_WITH_PREFIX)
  public void getParticipantsShouldReturnForbiddenResponse() throws Exception {
    mockMvc
            .perform(MockMvcRequestBuilders.get("/participants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(20)
  public void getAddedParticipantSDShouldReturnSuccessResponseWithSameSD() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_OK, part);

    String response = mockMvc
            .perform(MockMvcRequestBuilders.get("/self-descriptions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .queryParam("id", part.getId()).queryParam("withContent", "true")
                    .with(csrf()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    SelfDescriptions selfDescriptions = objectMapper.readValue(response, SelfDescriptions.class);
    String sdHash = selfDescriptions.getItems().get(0).getMeta().getSdHash();
    String content = selfDescriptions.getItems().get(0).getContent();

    String responseOfSDContent = mockMvc
            .perform(MockMvcRequestBuilders.get("/self-descriptions/" + sdHash)
                    .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

    assertEquals(part.getSelfDescription(), responseOfSDContent);
    assertEquals(responseOfSDContent, content);
    assertEquals(1, selfDescriptions.getItems().size());
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(20)
  public void wrongParticipantShouldReturnNotFoundResponse() throws Exception {
    String partId = URLEncoder.encode("unknown", Charset.defaultCharset());
    setupKeycloak(HttpStatus.SC_NOT_FOUND, null);

    mockMvc
            .perform(MockMvcRequestBuilders.get("/participants/{participantId}", partId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(20)
  public void getParticipantsShouldReturnCorrectNumber() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_OK, part);

    MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/participants?offset={offset}&limit={limit}", null, 1)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    Participants parts = objectMapper.readValue(result.getResponse().getContentAsString(), Participants.class);
    assertNotNull(parts);
    assertEquals(1, parts.getItems().size());
    assertEquals(1, parts.getTotalCount());
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(20)
  public void getParticipantUsersShouldReturnCorrectNumber() throws Exception {
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key-1", "empty SD");
    setupKeycloak(HttpStatus.SC_OK, part);
    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());

    User userOfParticipant = getUserOfParticipant(part.getId());
    setupKeycloakForUsers(HttpStatus.SC_CREATED, userOfParticipant, userId);
    MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/participants/{participantId}/users", partId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
    UserProfiles users = objectMapper.readValue(result.getResponse().getContentAsString(), UserProfiles.class);
    assertNotNull(users);
    assertEquals(0, users.getItems().size());

  }

  @Test
  @WithMockUser(authorities = SD_ADMIN_ROLE_WITH_PREFIX)
  public void addParticipantShouldReturnForbiddenResponse() throws Exception {
    mockMvc
            .perform(MockMvcRequestBuilders.post("/participants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE))
                    .with(csrf()))
            .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(25)
  public void addParticipantFailWithSameSDShouldReturnConflictFromKeyCloakWithoutDBStore() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData partNew = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key", json);
    sdStorePublisher.deleteSelfDescription(partNew.getSdHash());
    setupKeycloak(HttpStatus.SC_CONFLICT, partNew);

    mockMvc
            .perform(MockMvcRequestBuilders.post("/participants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .with(csrf()))
            .andExpect(status().isConflict());

    NotFoundException exceptionSDStore = assertThrows(NotFoundException.class,
            () -> sdStorePublisher.getByHash(partNew.getSdHash()));
    assertEquals(NotFoundException.class, exceptionSDStore.getClass());
  }

  @Test
  @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
  @Order(26)
  public void addParticipantFailWithKeyCloakErrorShouldReturnErrorWithoutDBStore() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:wrong-issuer", "did:example:holder",
            "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_INTERNAL_SERVER_ERROR, part);

    mockMvc
            .perform(MockMvcRequestBuilders.post("/participants")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .with(csrf()))
            .andExpect(status().is5xxServerError());

    Throwable exceptionSD = assertThrows(Throwable.class,
            () -> sdStorePublisher.getByHash(part.getSdHash()));
    assertEquals(NotFoundException.class, exceptionSD.getClass());
  }

  @Test
  @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX}, // think how to provide claims to test Jwt converter
        claims = @OpenIdClaims(otherClaims = @Claims(
        	stringClaims = {@StringClaim(name = "participant_id", value = "did:example:issuer")},
        	stringArrayClaims = {@StringArrayClaim(name = "roles", value = "gaia-x-admin")}
        	)))
  @Order(30)
  public void updateParticipantShouldReturnSuccessResponse() throws Exception {

    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_OK, part);

    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());
    log.debug("updateParticipantShouldReturnSuccessResponse; the partId is: {}", partId);

    String response = mockMvc
            .perform(MockMvcRequestBuilders.put("/participants/{participantId}", partId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json)
                    .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ParticipantMetaData partResult = objectMapper.readValue(response, ParticipantMetaData.class);
    assertNotNull(part);
    assertEquals("did:example:issuer", partResult.getId());
    assertEquals("did:example:holder", partResult.getName());
    assertEquals(PUBLIC_KEY_AS_JWK, partResult.getPublicKey());

    assertDoesNotThrow(() -> sdStorePublisher.getByHash(part.getSdHash()));

    SelfDescriptionMetadata metadata = sdStorePublisher.getByHash(part.getSdHash());
    assertEquals(part.getSelfDescription(), metadata.getSelfDescription().getContentAsString());
  }

  @Test
  @WithMockJwtAuth(authorities = {"ROLE_" + PARTICIPANT_ADMIN_ROLE},
        claims = @OpenIdClaims(otherClaims = @Claims(stringClaims =
            {@StringClaim(name = "participant_id", value = "wrongId")})))
  public void updateParticipantShouldReturnForbiddenResponse() throws Exception {
    mockMvc
            .perform(MockMvcRequestBuilders.put("/participants/{participantId}", "123")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE))
                    .with(csrf()))
            .andExpect(status().isForbidden());
  }

  @Test
  @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX},
        claims = @OpenIdClaims(otherClaims = @Claims(stringClaims =
            {@StringClaim(name = "participant_id", value = "did:example:issuer")})))
  @Order(30)
  public void updateParticipantWithOtherIdShouldReturnBadClientResponse() throws Exception {

    String json = getMockFileDataAsString(ALTERNATIVE_PARTICIPANT_FILE);

    ParticipantMetaData part = new ParticipantMetaData("did:example:new-issuer", "did:example:holder", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_OK, part);

    ContentAccessorDirect contentAccessor = new ContentAccessorDirect(json);
    VerificationResultParticipant verResult = verificationService.verifyParticipantSelfDescription(contentAccessor);
    SelfDescriptionMetadata sdMetadata = new SelfDescriptionMetadata(contentAccessor, verResult);
    sdStorePublisher.storeSelfDescription(sdMetadata, verResult);

    String updatedParticipant = getMockFileDataAsString(ALTERNATIVE2_PARTICIPANT_FILE);
    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.put("/participants/{participantId}", partId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(updatedParticipant)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX},
        claims = @OpenIdClaims(otherClaims = @Claims(stringClaims =
            {@StringClaim(name = "participant_id", value = "did:example:new-issuer")})))
  @Order(30)
  public void updateParticipantFailWithKeycloakErrorShouldReturnErrorWithoutDBStore() throws Exception {

    String json = getMockFileDataAsString(ALTERNATIVE_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:new-issuer", "did:example:holder", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_INTERNAL_SERVER_ERROR, part);
    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.put("/participants/{participantId}", partId)
            .contentType(MediaType.APPLICATION_JSON)
            .content(json)
            .with(csrf()))
        .andExpect(status().is5xxServerError());

    Throwable exceptionSD = assertThrows(Throwable.class,
            () -> sdStorePublisher.getByHash(part.getSdHash()));
    assertEquals(NotFoundException.class, exceptionSD.getClass());
  }

  @Test
  @WithMockJwtAuth(authorities = {"ROLE_unknown"},
        claims = @OpenIdClaims(otherClaims = @Claims(stringClaims =
            {@StringClaim(name = "participant_id", value = "wrongId")})))
  @Order(40)
  public void deleteParticipantFailWithWrongSessionParticipantIdAndUnknownRoleShouldReturnForbiddenResponse() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:updated", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_OK, part);
    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.delete("/participants/{participantId}", partId)
            	.with(csrf()))
            .andExpect(status().isForbidden());
  }

  @Test
  @WithMockJwtAuth(authorities = {PARTICIPANT_ADMIN_ROLE_WITH_PREFIX},
        claims = @OpenIdClaims(otherClaims = @Claims(stringClaims =
            {@StringClaim(name = "participant_id", value = "wrongId")})))
  public void deleteParticipantShouldReturnForbiddenResponse() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.delete("/participants/{participantId}", "123")
            .contentType(MediaType.APPLICATION_JSON)
            .content(getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE))
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX},
        claims = @OpenIdClaims(otherClaims = @Claims(stringClaims =
            {@StringClaim(name = "participant_id", value = "did:example:wrong-issuer")})))
  @Order(40)
  public void deleteParticipantFailWithWrongParticipantIdShouldReturnNotFoundResponse() throws Exception {
    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE).replace("did:example:issuer", "did:example:wrong-issuer");
    //String json = getMockFileDataAsString(ALTERNATIVE_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:wrong-issuer", "did:example:updated", "did:example:holder#key", json);
    setupKeycloak(HttpStatus.SC_NOT_FOUND, part);
    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());

    mockMvc.perform(MockMvcRequestBuilders.delete("/participants/{participantId}", partId)
            .with(csrf()))
        .andExpect(status().isNotFound());

    Throwable exceptionSD = assertThrows(Throwable.class,
            () -> sdStorePublisher.getByHash(part.getSdHash()));
    assertEquals(NotFoundException.class, exceptionSD.getClass());

  }

  @Test
  @WithMockJwtAuth(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX},
        claims = @OpenIdClaims(otherClaims = @Claims(stringClaims =
            {@StringClaim(name = "participant_id", value = "did:example:issuer")})))
  @Order(50)
  public void deleteParticipantSuccessShouldReturnSuccessResponse() throws Exception {

    String json = getMockFileDataAsString(UNIQUE_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:unique-issuer", "did:example:holder", "did:example:holder#key", json);
    ContentAccessorDirect contentAccessor = new ContentAccessorDirect(json);
    VerificationResultParticipant verResult = verificationService.verifyParticipantSelfDescription(contentAccessor);
    SelfDescriptionMetadata sdMetadata = new SelfDescriptionMetadata(contentAccessor, verResult);
    sdStorePublisher.storeSelfDescription(sdMetadata, verResult);

    setupKeycloak(HttpStatus.SC_OK, part);
    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());

    String response = mockMvc
            .perform(MockMvcRequestBuilders.delete("/participants/{participantId}", partId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ParticipantMetaData participantMetaData = objectMapper.readValue(response, ParticipantMetaData.class);
    assertNotNull(part);
    assertEquals("did:example:unique-issuer", participantMetaData.getId());
    assertEquals("did:example:holder", participantMetaData.getName());
    assertEquals("did:example:holder#key", participantMetaData.getPublicKey());

    Throwable exceptionSD = assertThrows(Throwable.class,
            () -> sdStorePublisher.getByHash(part.getSdHash()));
    assertEquals(NotFoundException.class, exceptionSD.getClass());
  }

  @Test
  @WithMockJwtAuth(authorities = {"ROLE_Ro-MU-CA"},
          claims = @OpenIdClaims(otherClaims = @Claims(stringClaims
                  = {
            @StringClaim(name = "participant_id", value = "did:example:issuer")})))
  @Order(60)
  public void deleteParticipantWithAllUsersSuccessShouldReturnSuccessResponse() throws Exception {
    //Initially adding user
    addParticipantShouldReturnCreatedResponse();

    String json = getMockFileDataAsString(DEFAULT_PARTICIPANT_FILE);
    ParticipantMetaData part = new ParticipantMetaData("did:example:issuer", "did:example:holder", "did:example:holder#key-1", json);

    User userOfParticipant = getUserOfParticipant(part.getId());
    setupKeycloakForUsers(HttpStatus.SC_CREATED, userOfParticipant, userId);
    userDao.create(userOfParticipant);

    setupKeycloak(HttpStatus.SC_OK, part);
    setupKeycloakForUsers(HttpStatus.SC_NO_CONTENT, userOfParticipant, userId);
    String partId = URLEncoder.encode(part.getId(), Charset.defaultCharset());

    String response = mockMvc
            .perform(MockMvcRequestBuilders.delete("/participants/{participantId}", partId)
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    ParticipantMetaData participantMetaData = objectMapper.readValue(response, ParticipantMetaData.class);
    assertNotNull(participantMetaData);

    Throwable exceptionSD = assertThrows(Throwable.class,
            () -> sdStorePublisher.getByHash(part.getSdHash()));
    assertEquals(NotFoundException.class, exceptionSD.getClass());

    assertEquals(0, userDao.search(userId, 0, 1).getTotalCount());
  }

  private void setupKeycloak(int status, ParticipantMetaData part) {
    when(builder.build()).thenReturn(keycloak);
    when(keycloak.realm("gaia-x")).thenReturn(realmResource);
    when(realmResource.groups()).thenReturn(groupsResource);
    if (status == HttpStatus.SC_INTERNAL_SERVER_ERROR) {
      when(groupsResource.add(any())).thenThrow(new ServerException("500 Server Error"));
      doThrow(new ServerException("500 Server Error")).when(groupResource).update(any());
    } else if (status == HttpStatus.SC_CONFLICT) {
      String message = "{\"errorMessage\": \"User already exists\"}";
      String charset = "UTF-8";
      ByteArrayInputStream byteArrayInputStream;
      try {
        byteArrayInputStream = new ByteArrayInputStream(message.getBytes(charset));
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
      when(groupsResource.add(any())).thenReturn(Response.status(status).type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON).entity(byteArrayInputStream).build());
    } else {
      when(groupsResource.add(any())).thenReturn(Response.status(status).build());
    }

    if (part == null) {
      if (status == HttpStatus.SC_NOT_FOUND) {  
        when(groupsResource.group(any())).thenThrow(new NotFoundException("404 NOT FOUND"));
      } else {
         when(groupsResource.group(any())).thenReturn(groupResource);  
      }
      when(groupsResource.count()).thenReturn(Map.of("count", 0L));
      when(groupResource.members()).thenReturn(List.of());
      when(groupsResource.groups()).thenReturn(List.of());
      when(groupsResource.groups(any(), any())).thenReturn(List.of());
      when(groupsResource.groups(any(), any(), any())).thenReturn(List.of());
      when(groupsResource.groups(any(), any(), any(), anyBoolean())).thenReturn(List.of());
    } else {
      GroupRepresentation groupRepo = ParticipantDaoImpl.toGroupRepo(part);
      when(groupsResource.group(any())).thenReturn(groupResource);
      when(groupResource.members()).thenReturn(List.of());
      when(groupsResource.groups()).thenReturn(List.of(groupRepo));
      when(groupsResource.groups(any(), any())).thenReturn(List.of(groupRepo));
      when(groupsResource.groups(eq(part.getId()), any(), any())).thenReturn(List.of(groupRepo));
      when(groupsResource.groups(any(), any(), any(), anyBoolean())).thenReturn(List.of(groupRepo));
      when(groupsResource.count()).thenReturn(Map.of("count", 1L));
    }
  }

  private void setupKeycloakForUsers(int status, User user, String id) {
    when(builder.build()).thenReturn(keycloak);
    when(keycloak.realm("gaia-x")).thenReturn(realmResource);
    when(realmResource.users()).thenReturn(usersResource);
    when(usersResource.create(any())).thenReturn(Response.status(status).build());
    when(realmResource.roles()).thenReturn(rolesResource);
    when(realmResource.clients()).thenReturn(clientsResource);
    if (user == null) {
      when(usersResource.search(any())).thenReturn(List.of());
    } else {
      when(usersResource.delete(any())).thenReturn(Response.status(status).build());
      UserRepresentation userRepo = UserDaoImpl.toUserRepo(user);
      userRepo.setId(id);
      when(groupResource.members()).thenReturn(List.of());
      when(usersResource.list(any(), any())).thenReturn(List.of(userRepo));
      when(usersResource.search(userRepo.getUsername())).thenReturn(List.of(userRepo));
      when(usersResource.get(any())).thenReturn(userResource);
      when(userResource.toRepresentation()).thenReturn(userRepo);
      when(rolesResource.list()).thenReturn(getAllRoles());
      when(userResource.roles()).thenReturn(roleMappingResource);
      ClientRepresentation client = new ClientRepresentation();
      client.setClientId(UUID.randomUUID().toString());
      client.setClientId(clientId);
      when(clientsResource.findByClientId(client.getClientId())).thenReturn(List.of(client));
      when(clientsResource.get(client.getId())).thenReturn(clientResource);
      when(clientResource.roles()).thenReturn(rolesResource);
      when(roleMappingResource.clientLevel(any())).thenReturn(roleScopeResource);
      List<RoleRepresentation> roleRepresentations = new ArrayList<>();
      user.getRoleIds().forEach(roleId -> roleRepresentations.add(new RoleRepresentation(roleId, roleId, false)));
      when(roleScopeResource.listAll()).thenReturn(roleRepresentations);
    }
  }

  private void deleteParticipantFromSdStore(ParticipantMetaData part) {
    try {
      sdStorePublisher.deleteSelfDescription(part.getSdHash());
    } catch (Exception ex) {
    }
  }

  public User getUserOfParticipant(String partId) {
    return new User(partId, "testUserName", "testLastName", "test@gmail", List.of(PARTICIPANT_USER_ADMIN_ROLE));
  }
}
