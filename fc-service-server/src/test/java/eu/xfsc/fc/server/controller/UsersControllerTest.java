package eu.xfsc.fc.server.controller;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.unauthorized;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static eu.xfsc.fc.core.dao.impl.UserDaoImpl.toUserRepo;
import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static eu.xfsc.fc.server.helper.UserServiceHelper.getAllRoles;
import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.SD_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.TestCommonConstants.DEFAULT_PARTICIPANT_ID;
import static eu.xfsc.fc.server.util.TestCommonConstants.SD_ADMIN_ROLE_WITH_PREFIX;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.keycloak.OAuth2Constants.CLIENT_ID;
import static org.keycloak.OAuth2Constants.CLIENT_SECRET;
import static org.keycloak.OAuth2Constants.GRANT_TYPE;
import static org.keycloak.OAuth2Constants.PASSWORD;
import static org.keycloak.OAuth2Constants.USERNAME;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.annotation.BeforeTestClass;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.WebApplicationContext;

import com.c4_soft.springaddons.security.oauth2.test.annotations.Claims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.OpenIdClaims;
import com.c4_soft.springaddons.security.oauth2.test.annotations.StringClaim;
import com.c4_soft.springaddons.security.oauth2.test.annotations.WithMockJwtAuth;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;

import eu.xfsc.fc.api.generated.model.Error;
import eu.xfsc.fc.api.generated.model.User;
import eu.xfsc.fc.api.generated.model.UserProfile;
import eu.xfsc.fc.api.generated.model.UserProfiles;
import eu.xfsc.fc.core.dao.UserDao;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureWireMock(port = 0)
@TestPropertySource(locations = "classpath:wiremock.properties")
public class UsersControllerTest {
    @Value("${wiremock.server.baseUrl}")
    private String keycloakBaseUrl;
    @Value("${keycloak.resource}")
    private String clientId;
    @Value("${keycloak.credentials.secret}")
    private String clientSecret;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private KeycloakBuilder builder;
    @MockitoBean
    private Keycloak keycloak;
    @MockitoBean
    private RealmResource realmResource;
    @MockitoBean
    private UsersResource usersResource;
    @MockitoBean
    private ClientsResource clientsResource;
    @MockitoBean
    private ClientResource clientResource;
    @MockitoBean
    private RolesResource rolesResource;
    @MockitoBean
    private GroupsResource groupsResource;
    @MockitoBean
    private RoleMappingResource roleMappingResource;
    @MockitoBean
    private RoleScopeResource roleScopeResource;
    @MockitoBean
    private UserResource userResource;
    @Autowired
    private UserDao userDao;
    @Autowired
    private ObjectMapper objectMapper;

    private static RsaJsonWebKey rsaJsonWebKey;

    @BeforeTestClass
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    public void userAuthShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/users").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void userAuthShouldReturnForbiddenResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/users").with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void addUserShouldReturnCreatedResponse() throws Exception {
        User user = getTestUser("name1", "surname2");
        String userId = UUID.randomUUID().toString();
        setupKeycloak(SC_CREATED, user, userId);
        String response = mockMvc
            .perform(MockMvcRequestBuilders.post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user))
            	.with(csrf()))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UserProfile profile = objectMapper.readValue(response, UserProfile.class);
        assertThatResponseUserHasValidData(user, profile);
    }

    @Test
    @WithMockUser(authorities = SD_ADMIN_ROLE_WITH_PREFIX)
    public void addUserShouldReturnForbiddenResponse() throws Exception {
        mockMvc
            .perform(MockMvcRequestBuilders.post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new User()))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockJwtAuth(authorities = {PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX}, claims = @OpenIdClaims(otherClaims = @Claims(stringClaims = {
        @StringClaim(name = "participant_id", value = DEFAULT_PARTICIPANT_ID)})))
    public void addAdminUserShouldReturnForbiddenResponse() throws Exception {
        User user = getTestUser("adminName", "adminSurname");
        user.roleIds(List.of(CATALOGUE_ADMIN_ROLE));
        String userId = UUID.randomUUID().toString();
        setupKeycloak(SC_CREATED, user, userId);
        mockMvc
            .perform(MockMvcRequestBuilders.post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void addDuplicateSDReturnConflictWithKeycloak() throws Exception {
        User user = getTestUser("name2", "surname2");
        setupKeycloak(HttpStatus.SC_CREATED, user, UUID.randomUUID().toString());
        userDao.create(user);
        when(usersResource.search(user.getEmail())).thenReturn(List.of(toUserRepo(user)));
        when(usersResource.create(any()))
            .thenReturn(Response.status(HttpStatus.SC_CONFLICT, "Conflict")
                .entity(new ByteArrayInputStream("{ \"errorMessage\" : \"User exists with same username\"}".getBytes()))
                .build());
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user))
                .with(csrf()))
            .andExpect(status().isConflict())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Error error = objectMapper.readValue(response, Error.class);
        assertNotNull(error);
        assertEquals("User exists with same username", error.getMessage());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void getUserShouldReturnSuccessResponse() throws Exception {
        User user = getTestUser("name3", "surname3");
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, user, userId);
        UserProfile existed = userDao.create(user);
        mockMvc
            .perform(MockMvcRequestBuilders.get("/users/{userId}", existed.getId())
                .contentType(MediaType.APPLICATION_JSON)
            	.with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = SD_ADMIN_ROLE_WITH_PREFIX)
    public void getUserShouldReturnForbiddenResponse() throws Exception {
        mockMvc
            .perform(MockMvcRequestBuilders.get("/users/{userId}", "123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new User()))
            	.with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void wrongUserShouldReturnNotFoundResponse() throws Exception {
        setupKeycloak(HttpStatus.SC_NOT_FOUND, null, "123");

        String result = mockMvc
            .perform(MockMvcRequestBuilders.get("/users/{userId}", "123")
                .contentType(MediaType.APPLICATION_JSON)
            	.with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn().getResponse().getContentAsString();
        Error error = objectMapper.readValue(result, Error.class);
        assertNotNull(error);
        assertEquals("User with id 123 not found", error.getMessage());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void getUsersShouldReturnCorrectNumber() throws Exception {
        User user = getTestUser("name4", "surname4");
        setupKeycloak(HttpStatus.SC_OK, user, null);

        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/users?offset={offset}&limit={limit}", null, null)
                .contentType(MediaType.APPLICATION_JSON)
            	.with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
        UserProfiles users = objectMapper.readValue(result.getResponse().getContentAsString(), UserProfiles.class);
        assertNotNull(users);
        // Counts with catalogue administrator user
        assertEquals(1, users.getItems().size());
    }

    @Test
    @WithMockUser(authorities = SD_ADMIN_ROLE_WITH_PREFIX)
    public void getUsersShouldReturnForbiddenResponse() throws Exception {
        mockMvc
            .perform(MockMvcRequestBuilders.get("/users")
                .contentType(MediaType.APPLICATION_JSON)
        		.with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void deleteUserShouldReturnSuccessResponse() throws Exception {
        User user = getTestUser("name5", "surname5");
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_NO_CONTENT, user, userId);
        UserProfile existed = userDao.create(user);
        String response = mockMvc
            .perform(MockMvcRequestBuilders.delete("/users/{userId}", existed.getId())
               	.with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UserProfile profile = objectMapper.readValue(response, UserProfile.class);
        assertThatResponseUserHasValidData(user, profile);
    }

    @Test
    @WithMockUser(authorities = {SD_ADMIN_ROLE_WITH_PREFIX})
    public void deleteUserShouldReturnForbiddenResponse() throws Exception {
        mockMvc
            .perform(MockMvcRequestBuilders.delete("/users/{userId}", "123")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void updateNonexistentUserShouldReturnNotFoundResponse() throws Exception {
        setupKeycloak(HttpStatus.SC_NOT_FOUND, null, "123");

        String result = mockMvc
            .perform(MockMvcRequestBuilders.delete("/users/{userId}", "123")
            	.with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn()
            .getResponse()
            .getContentAsString();

        Error error = objectMapper.readValue(result, Error.class);
        assertNotNull(error);
        assertEquals("User with id 123 not found", error.getMessage());
    }

    @Test
    @Disabled // TODO: fix me!!
    public void deleteUserAndKeycloakAccessShouldReturnUnauthorizedError() throws Exception {
        User user = getTestUser("newuser", "newuser").addRoleIdsItem(CATALOGUE_ADMIN_ROLE);
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_NO_CONTENT, user, userId);
        UserProfile existed = userDao.create(user);
        setUpKeycloakAuth(user);

        mockMvc.perform(MockMvcRequestBuilders.delete("/users/{userId}", existed.getId())
                .with(authentication(new BearerTokenAuthenticationToken(grantAccessToken("newuser", "newuser"))))
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk());

        stubFor(WireMock.post(urlEqualTo("/auth/realms/gaia-x/protocol/openid-connect/token"))
            .willReturn(unauthorized().withBody("{ \"error\": \"HTTP 401 Unauthorized\"}")));
        when(usersResource.delete(any())).thenThrow(new NotFoundException("404 NOT FOUND"));

        assertThrows(NotFoundException.class, () -> userDao.delete(existed.getId()));
        assertThrows(HttpClientErrorException.Unauthorized.class, () -> grantAccessToken("newuser", "newuser"));
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void updateUserShouldReturnSuccessResponse() throws Exception {
        User user = getTestUser("name6", "surname6");
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, user, userId);
        UserProfile existed = userDao.create(user);
        user = getTestUser("changed name", "changed surname");
        mockMvc
            .perform(MockMvcRequestBuilders.put("/users/{userId}", existed.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(user))
                .with(csrf()))
            .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = {SD_ADMIN_ROLE_WITH_PREFIX})
    public void updateUserShouldReturnForbiddenResponse() throws Exception {
        mockMvc
            .perform(MockMvcRequestBuilders.put("/users/{userId}", "123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new User()))
                .with(csrf()))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void updateUserRolesShouldReturnSuccessResponse() throws Exception {
        User user = getTestUser("name7", "surname7");
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, user, userId);
        UserProfile existed = userDao.create(user);

        when(roleScopeResource.listAll())
            .thenReturn(List.of(new RoleRepresentation(PARTICIPANT_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, false),
                new RoleRepresentation(SD_ADMIN_ROLE, SD_ADMIN_ROLE, false)));

        String response = mockMvc
            .perform(MockMvcRequestBuilders.put("/users/{userId}/roles", existed.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(SD_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE)))
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        UserProfile profile = objectMapper.readValue(response, UserProfile.class);
        assertEquals(2, profile.getRoleIds().size());
        assertTrue(profile.getRoleIds().containsAll(List.of(PARTICIPANT_ADMIN_ROLE, SD_ADMIN_ROLE)));
    }

    //Role Assignment can be done on this criteria
    //    role :-> can given by
    //    Ro-MU-CA :-> Ro-MU-CA
    //    Ro-MU-A :-> Ro-MU-CA, Ro-MU-A
    //    Ro-SD-A :-> Ro-MU-CA, Ro-MU-A, Ro-Pa-A (if not self)
    //    Ro-Pa-A :-> Ro-MU-CA, Ro-MU-A, Ro-Pa-A

    //Please see above criteria for detailed role assignment rule.
    @Test
    @WithMockUser(authorities = {PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX})
    public void updateUserRolesShouldReturnErrorResponse() throws Exception {
        User user = getTestUser("name7", "surname7");
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, user, userId);
        UserProfile existed = userDao.create(user);

        when(roleScopeResource.listAll())
            .thenReturn(List.of(new RoleRepresentation(PARTICIPANT_USER_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE, false)));

        String response = mockMvc
            .perform(MockMvcRequestBuilders.put("/users/{userId}/roles", existed.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(SD_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE)))
                .with(csrf()))
            .andExpect(status().isForbidden())
            .andReturn().getResponse().getContentAsString();

        Error error = objectMapper.readValue(response, Error.class);
        assertNotNull(error);
        assertEquals("User does not have permission to execute this request.", error.getMessage());
    }
    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void updateNonexistentUserRolesShouldReturnNotFoundResponse() throws Exception {
        setupKeycloak(HttpStatus.SC_NOT_FOUND, null, "123");

        String result = mockMvc
            .perform(MockMvcRequestBuilders.put("/users/{userId}/roles", "123")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(SD_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE)))
                .with(csrf()))
            .andExpect(status().isNotFound())
            .andReturn().getResponse().getContentAsString();

        Error error = objectMapper.readValue(result, Error.class);
        assertNotNull(error);
        assertEquals("User with id 123 not found", error.getMessage());
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void updateDuplicatedUserRoleShouldReturnSuccessResponse() throws Exception {
        User user = getTestUser("name8", "surname8").addRoleIdsItem(PARTICIPANT_ADMIN_ROLE);
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, user, userId);
        UserProfile existed = userDao.create(user);

        mockMvc.perform(MockMvcRequestBuilders.put("/users/{userId}/roles", existed.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(PARTICIPANT_ADMIN_ROLE)))
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        UserProfile newProfile = userDao.select(existed.getId());
        assertNotNull(newProfile);
        assertEquals(2, newProfile.getRoleIds().size());
        assertTrue(newProfile.getRoleIds().containsAll(List.of(PARTICIPANT_ADMIN_ROLE, SD_ADMIN_ROLE)));
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void revokeUserRoleShouldNotContainThisRoleInSubsequentRequest() throws Exception {
        User user = getTestUser("test_name", "test_surname");
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, user, userId);
        UserProfile existed = userDao.create(user);
        when(roleScopeResource.listAll())
            .thenReturn(List.of(new RoleRepresentation(PARTICIPANT_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, false)));
        String response = mockMvc
            .perform(MockMvcRequestBuilders.put("/users/{userId}/roles", existed.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(PARTICIPANT_ADMIN_ROLE)))
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        UserProfile profile = objectMapper.readValue(response, UserProfile.class);
        assertEquals(1, profile.getRoleIds().size());
        assertTrue(profile.getRoleIds().contains(PARTICIPANT_ADMIN_ROLE));

        List<String> roles = userDao.select(profile.getId()).getRoleIds();
        assertEquals(1, roles.size());
        assertTrue(roles.contains(PARTICIPANT_ADMIN_ROLE));
    }

    @Test
    @WithMockUser(authorities = {CATALOGUE_ADMIN_ROLE_WITH_PREFIX})
    public void changeUserPermissionShouldReturnSuccessResponse() throws Exception {
        User user = getTestUser("new_user", "new_user");
        String userId = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, user, userId);

        UserProfile existed = userDao.create(user);
        assertEquals(1, existed.getRoleIds().size());
        assertTrue( existed.getRoleIds().contains(SD_ADMIN_ROLE));

        when(roleScopeResource.listAll())
            .thenReturn(List.of(new RoleRepresentation(PARTICIPANT_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, false)));

        String response = mockMvc
            .perform(MockMvcRequestBuilders.put("/users/{userId}/roles", existed.getId())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(List.of(PARTICIPANT_ADMIN_ROLE)))
                .with(csrf()))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        UserProfile updated = objectMapper.readValue(response, UserProfile.class);
        assertEquals(1, updated.getRoleIds().size());
        assertTrue(updated.getRoleIds().containsAll(List.of(PARTICIPANT_ADMIN_ROLE)));
    }

    private void setupKeycloak(int status, User user, String id) {
        when(builder.build()).thenReturn(keycloak);
        when(keycloak.realm("gaia-x")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(realmResource.groups()).thenReturn(groupsResource);
        when(realmResource.clients()).thenReturn(clientsResource);
        if (user == null) {
            when(usersResource.create(any())).thenReturn(Response.status(status).build());
            when(usersResource.delete(any())).thenThrow(new NotFoundException("User with id " + id + " not found"));
            when(usersResource.list(any(), any())).thenReturn(List.of());
            when(usersResource.search(any())).thenReturn(List.of());
            when(usersResource.get(any())).thenThrow(new NotFoundException("User with id " + id + " not found"));
        } else {
            when(usersResource.create(any())).thenReturn(Response.status(SC_CREATED).entity(user).build());
            when(usersResource.delete(any())).thenReturn(Response.status(status).build());
            UserRepresentation userRepo = toUserRepo(user);
            userRepo.setId(id);
            when(usersResource.list(any(), any())).thenReturn(List.of(userRepo));
            when(usersResource.search(user.getEmail())).thenReturn(List.of(userRepo));
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
            List<RoleRepresentation> roleRepresentations =  new ArrayList<>();
            user.getRoleIds().forEach(roleId-> roleRepresentations.add(new RoleRepresentation(roleId, roleId, false)));
            when(roleScopeResource.listAll()).thenReturn(roleRepresentations);
            GroupRepresentation group = new GroupRepresentation();
            group.setId(UUID.randomUUID().toString());
            group.setName(user.getParticipantId());
            when(groupsResource.groups(eq(user.getParticipantId()), any(), any(), anyBoolean())).thenReturn(List.of(group));
            when(userResource.groups()).thenReturn(List.of(group));
        }
    }

    private User getTestUser(String firstName, String lastName) {
        return new User()
            .email(firstName + "." + lastName + "@test.org")
            .participantId(DEFAULT_PARTICIPANT_ID)
            .firstName(firstName)
            .lastName(lastName)
            .addRoleIdsItem(SD_ADMIN_ROLE);
    }

    private static void assertThatResponseUserHasValidData(final User excepted, final UserProfile actual) {
        assertNotNull(actual);
        assertNotNull(actual.getId());
        assertNotNull(actual.getEmail());
        assertEquals(excepted.getEmail(), actual.getEmail());
        assertEquals(excepted.getFirstName(), actual.getFirstName());
        assertEquals(excepted.getLastName(), actual.getLastName());
        assertEquals(excepted.getParticipantId(), actual.getParticipantId());
    }

    private void setUpKeycloakAuth(User user) throws IOException, JoseException {
        rsaJsonWebKey = RsaJwkGenerator.generateJwk(2048);
        rsaJsonWebKey.setKeyId("k1");
        rsaJsonWebKey.setAlgorithm(AlgorithmIdentifiers.RSA_USING_SHA256);
        rsaJsonWebKey.setUse("sig");

        String openidConfig = getMockFileDataAsString("openid-configs.json")
            .replace("keycloakBaseUrl", keycloakBaseUrl);

        stubFor(WireMock.get(urlEqualTo("/auth/realms/gaia-x/.well-known/openid-configuration"))
            .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE).withBody(openidConfig)));
        stubFor(WireMock.get(urlEqualTo("/auth/realms/gaia-x/protocol/openid-connect/certs"))
            .willReturn(aResponse().withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE).withBody(openidConfig)
                .withBody(new JsonWebKeySet(rsaJsonWebKey).toJson())));

        stubFor(WireMock.post(urlEqualTo("/auth/realms/gaia-x/protocol/openid-connect/token"))
            .willReturn(ok().withBody("{\"access_token\": \"" + generateToken(user) + "\", \"expires_in\": 900," +
                " \"refresh_expires_in\": 1800, \"token_type\": \"Bearer\", \"scope\": \"gaia-x\"}")));
    }

    private String generateToken(User user) throws JoseException {
        JwtClaims claims = new JwtClaims();
        claims.setJwtId(UUID.randomUUID().toString());
        claims.setExpirationTimeMinutesInTheFuture(10);
        claims.setNotBeforeMinutesInThePast(0);
        claims.setIssuedAtToNow();
        claims.setAudience("account");
        claims.setIssuer(String.format("%s/auth/realms/gaia-x", keycloakBaseUrl));
        claims.setSubject(UUID.randomUUID().toString());
        claims.setClaim("typ", "Bearer");
        claims.setClaim("azp", clientId);
        claims.setClaim("session_state", UUID.randomUUID().toString());
        claims.setClaim("resource_access", Map.of(clientId, Map.of("roles", List.of(CATALOGUE_ADMIN_ROLE))));
        claims.setClaim("scope", "openid gaia-x");
        claims.setClaim("email_verified", true);
        claims.setClaim("preferred_username", user.getEmail());
        claims.setClaim("given_name", user.getFirstName());
        claims.setClaim("family_name", user.getLastName());

        JsonWebSignature jws = new JsonWebSignature();
        jws.setPayload(claims.toJson());
        jws.setKey(rsaJsonWebKey.getPrivateKey());
        jws.setKeyIdHeaderValue(rsaJsonWebKey.getKeyId());
        jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
        jws.setHeader("typ","JWT");
        return jws.getCompactSerialization();
    }

    private String grantAccessToken(String username, String password) throws Exception {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add(GRANT_TYPE, PASSWORD);
        map.add(USERNAME, username);
        map.add(PASSWORD, password);
        map.add(CLIENT_ID, clientId);
        map.add(CLIENT_SECRET, clientSecret);

        String uri = keycloakBaseUrl + "/auth/realms/gaia-x/protocol/openid-connect/token";
        return objectMapper.readValue(
            new RestTemplate().exchange(uri, HttpMethod.POST, new HttpEntity<>(map, headers), String.class)
                .getBody(), AccessTokenResponse.class).getToken();
    }
}
