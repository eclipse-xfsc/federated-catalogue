package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.isA;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * Integration tests for Graph Database Admin endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class GraphDatabaseAdminControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getGraphDatabaseStatus_withAdminRole_returnsStatus() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph-database")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeBackend").value(isA(String.class)))
        .andExpect(jsonPath("$.connected").value(isA(Boolean.class)))
        .andExpect(jsonPath("$.claimCount").value(isA(Number.class)));
  }

  @Test
  @WithMockUser
  void getGraphDatabaseStatus_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph-database")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser
  void switchGraphDatabase_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/admin/graph-database/switch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"backend\":\"FUSEKI\"}")
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void switchGraphDatabase_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/admin/graph-database/switch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"backend\":\"FUSEKI\"}")
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void switchGraphDatabase_validBackend_returnsRestartRequired() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/admin/graph-database/switch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"backend\":\"FUSEKI\"}")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.restartRequired").value(true))
        .andExpect(jsonPath("$.message").value(isA(String.class)));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void switchGraphDatabase_invalidBackend_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/admin/graph-database/switch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"backend\":\"INVALID\"}")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getGraphDatabaseStatus_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph-database")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
}
