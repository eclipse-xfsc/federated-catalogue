package eu.xfsc.fc.server.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;

import org.apache.http.HttpStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.annotation.BeforeTestClass;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.Session;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class SessionControllerTest {
    
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper jsonMapper;

    @MockBean
    private KeycloakBuilder builder;
    @MockBean
    private Keycloak keycloak;
    @MockBean
    private RealmResource realmResource;
    @MockBean
    private UsersResource usersResource;
    @MockBean
    private UserResource userResource;
    @MockBean
    private RoleMappingResource rmResource;
    
    @BeforeTestClass
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    public void sessionAuthShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.get("/session")).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void getSessionShouldReturnSuccessResponse() throws Exception {
      
        String id = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, id);
        
        String response = mockMvc
            .perform(MockMvcRequestBuilders.get("/session")
            .contentType(MediaType.APPLICATION_JSON)
        	.with(csrf()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        Session session = jsonMapper.readValue(response, Session.class);
        assertNotNull(session);
        assertEquals(id, session.getUserId());
    }

    @Test
    @WithMockUser
    public void deleteSessionShouldReturnSuccessResponse() throws Exception {
      
        String id = UUID.randomUUID().toString();
        setupKeycloak(HttpStatus.SC_OK, id);
        
        mockMvc
            .perform(MockMvcRequestBuilders.delete("/session")
            .contentType(MediaType.APPLICATION_JSON)
        	.with(csrf()))
            .andExpect(status().isOk());
    }
    
    private void setupKeycloak(int status, String id) {
        when(builder.build()).thenReturn(keycloak);
        when(keycloak.realm("gaia-x")).thenReturn(realmResource);
        when(realmResource.users()).thenReturn(usersResource);
        when(usersResource.create(any())).thenReturn(Response.status(status).build());
        if (id == null) {
            when(usersResource.delete(any())).thenThrow(new NotFoundException("404 NOT FOUND"));
            when(usersResource.list(any(), any())).thenReturn(List.of());
            when(usersResource.search(any())).thenReturn(List.of());
            when(usersResource.get(any())).thenThrow(new NotFoundException("404 NOT FOUND"));
        } else {
            UserRepresentation userRepo = new UserRepresentation();
            userRepo.setId(id);
            when(usersResource.get(any())).thenReturn(userResource);
            when(userResource.toRepresentation()).thenReturn(userRepo);
            UserSessionRepresentation usr = new UserSessionRepresentation();
            usr.setUserId(id);
            usr.setStart(System.currentTimeMillis());
            usr.setId(UUID.randomUUID().toString());
            when(userResource.getUserSessions()).thenReturn(List.of(usr));
            when(userResource.roles()).thenReturn(rmResource);
            MappingsRepresentation mr = new MappingsRepresentation();
            RoleRepresentation rr = new RoleRepresentation();
            rr.setName("Ro-TEST-1");
            mr.setRealmMappings(List.of(rr));
            when(rmResource.getAll()).thenReturn(mr);
        }
    }
    
}
