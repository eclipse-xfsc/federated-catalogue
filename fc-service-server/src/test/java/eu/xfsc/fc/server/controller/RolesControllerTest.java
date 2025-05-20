package eu.xfsc.fc.server.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.event.annotation.BeforeTestClass;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class RolesControllerTest {
    @Value("${keycloak.resource}")
    private String clientId;

    private static final TypeReference<List<?>> LIST_TYPE_REF = new TypeReference<>() {};

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private KeycloakBuilder builder;
    @MockBean
    private Keycloak keycloak;
    @MockBean
    private RealmResource realmResource;
    @MockBean
    private RolesResource rolesResource;
    @MockBean
    private ClientsResource clientsResource;
    @MockBean
    private ClientResource clientResource;
    @Autowired
    private  ObjectMapper objectMapper;

    @BeforeTestClass
    public void setup() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    public void getRolesShouldReturnUnauthorizedResponse() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders.post("/roles").with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser
    public void getRolesShouldReturnExpectedNumber() throws Exception {
        setupKeycloak();

        MvcResult result = mockMvc
            .perform(MockMvcRequestBuilders.get("/roles")
            .contentType(MediaType.APPLICATION_JSON)
        	.with(csrf()))
            .andExpect(status().isOk())
            .andReturn();
        List<?> roles = objectMapper.readValue(result.getResponse().getContentAsString(), LIST_TYPE_REF);
        assertNotNull(roles);
        assertEquals(3, roles.size());
    }

    private void setupKeycloak() {
        when(builder.build()).thenReturn(keycloak);
        when(keycloak.realm("gaia-x")).thenReturn(realmResource);
        when(realmResource.roles()).thenReturn(rolesResource);
        when(realmResource.clients()).thenReturn(clientsResource);
        ClientRepresentation client = new ClientRepresentation();
        client.setClientId(UUID.randomUUID().toString());
        client.setClientId(clientId);
        when(clientsResource.findByClientId(client.getClientId())).thenReturn(List.of(client));
        when(clientsResource.get(client.getId())).thenReturn(clientResource);
        when(clientResource.roles()).thenReturn(rolesResource);
        List<RoleRepresentation> roles = List.of(new RoleRepresentation("RO_MA_A", null, false),
                new RoleRepresentation("RO_MA_B", null, true), new RoleRepresentation("RO_MA_C", null, false));
        when(rolesResource.list()).thenReturn(roles);
    }
}
