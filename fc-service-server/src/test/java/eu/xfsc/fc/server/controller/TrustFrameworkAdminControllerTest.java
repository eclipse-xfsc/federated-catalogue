package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
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
 * Integration tests for Trust Framework Admin endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class TrustFrameworkAdminControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getTrustFrameworks_withAdminRole_returnsSeededList() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", hasSize(1)))
        .andExpect(jsonPath("$[0].id").value("gaia-x"))
        .andExpect(jsonPath("$[0].name").value("Gaia-X Trust Framework"))
        .andExpect(jsonPath("$[0].enabled").value(false))
        .andExpect(jsonPath("$[0].connected").value(isA(Boolean.class)));
  }

  @Test
  @WithMockUser
  void getTrustFrameworks_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void getTrustFrameworks_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void setTrustFrameworkEnabled_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void setTrustFrameworkEnabled_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void updateTrustFrameworkConfig_withoutAdminRole_returns403() throws Exception {
    String body = "{\"serviceUrl\":\"https://example.com\",\"apiVersion\":\"v2\",\"timeoutSeconds\":30}";
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateTrustFrameworkConfig_unauthenticated_returns401() throws Exception {
    String body = "{\"serviceUrl\":\"https://example.com\",\"apiVersion\":\"v2\",\"timeoutSeconds\":30}";
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkEnabled_validId_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isOk());

    // Verify it was enabled
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].enabled").value(true));

    // Reset
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x/enabled")
            .param("enabled", "false")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkEnabled_invalidId_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/nonexistent/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void updateTrustFrameworkConfig_validPayload_returns200() throws Exception {
    String body = "{\"serviceUrl\":\"https://new.example.com\","
        + "\"apiVersion\":\"v2\",\"timeoutSeconds\":60}";

    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isOk());

    // Verify update
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[0].serviceUrl").value("https://new.example.com"))
        .andExpect(jsonPath("$[0].apiVersion").value("v2"))
        .andExpect(jsonPath("$[0].timeoutSeconds").value(60));
  }

  // --- AC-3: role-toggle endpoint ---

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkRoleEnabled_knownBundleAndRole_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/trust-frameworks/gaia-x-2511/roles/Participant/enabled")
            .param("enabled", "false")
            .with(csrf()))
        .andExpect(status().isOk());

    // Reset to default
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/trust-frameworks/gaia-x-2511/roles/Participant/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkRoleEnabled_unknownBundle_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/trust-frameworks/nonexistent-bundle/roles/Participant/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", notNullValue()));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkRoleEnabled_unknownRole_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/trust-frameworks/gaia-x-2511/roles/UnknownRole/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", notNullValue()));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkRoleEnabled_missingEnabledParam_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/trust-frameworks/gaia-x-2511/roles/Participant/enabled")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void setTrustFrameworkRoleEnabled_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/trust-frameworks/gaia-x-2511/roles/Participant/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void setTrustFrameworkRoleEnabled_wrongRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/trust-frameworks/gaia-x-2511/roles/Participant/enabled")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getTrustFrameworks_includesRolesField() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].roles", notNullValue()))
        .andExpect(jsonPath("$[0].roles.Participant").value(true))
        .andExpect(jsonPath("$[0].roles.ServiceOffering").value(true))
        .andExpect(jsonPath("$[0].roles.Resource").value(true));
  }
}
