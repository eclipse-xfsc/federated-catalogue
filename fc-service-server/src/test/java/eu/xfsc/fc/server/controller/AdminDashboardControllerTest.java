package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.isA;
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
 * Integration tests for Admin Dashboard endpoints (/admin/me, /admin/stats, /admin/health).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "graphstore.impl=fuseki",
    "keycloak.admin-console-url=http://localhost:8080/admin/master/console/#/test"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AdminDashboardControllerTest {

  @Autowired
  private MockMvc mockMvc;

  // --- /admin/me ---

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void checkAdminAccess_withAdminRole_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/me")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser
  void checkAdminAccess_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/me")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void checkAdminAccess_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/me")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // --- /admin/stats ---

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getAdminStats_withAdminRole_returnsAllFields() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/stats")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalAssets").value(greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.activeAssets").value(greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.activeTrustFrameworks").value(greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.totalUsers").value(isA(Number.class))) // Keycloak not available in tests
        .andExpect(jsonPath("$.totalSchemas").value(greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.totalParticipants").value(isA(Number.class))) // Keycloak not available in tests
        .andExpect(jsonPath("$.graphClaimCount").value(greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.graphBackend").value(isA(String.class)));
  }

  @Test
  @WithMockUser
  void getAdminStats_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/stats")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void getAdminStats_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/stats")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // --- /admin/health ---

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getAdminHealth_withAdminRole_returnsAllFields() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/health")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.catalogueStatus").value("UP"))
        .andExpect(jsonPath("$.graphDbStatus").exists())
        .andExpect(jsonPath("$.keycloakStatus").exists())
        .andExpect(jsonPath("$.fileStoreStatus").exists())
        .andExpect(jsonPath("$.databaseStatus").exists());
  }

  @Test
  @WithMockUser
  void getAdminHealth_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/health")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void getAdminHealth_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/health")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // --- /admin/keycloak-url ---

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getKeycloakAdminUrl_withAdminRole_returnsUrl() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/keycloak-url")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.url").value(containsString("/admin/master/console/")));
  }

  @Test
  @WithMockUser
  void getKeycloakAdminUrl_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/keycloak-url")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void getKeycloakAdminUrl_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/keycloak-url")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
}
