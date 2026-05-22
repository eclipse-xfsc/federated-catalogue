package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.isA;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static wiremock.org.hamcrest.Matchers.hasItem;

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
        .andExpect(jsonPath("$", hasSize(2)))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].name").value(hasItem("Gaia-X Trust Framework")))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].enabled").value(hasItem(false)))
        .andExpect(jsonPath("$[?(@.id == 'mock')].name").value(hasItem("Mock Trust Framework")))
        .andExpect(jsonPath("$[?(@.id == 'mock')].enabled").value(hasItem(false)));
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
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true}")
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void setTrustFrameworkEnabled_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true}")
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
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true}")
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].enabled").value(hasItem(true)));

    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkEnabled_missingBody_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/gaia-x/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkEnabled_invalidId_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/nonexistent/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true}")
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
        .andExpect(jsonPath("$[0].timeoutSeconds").value(60))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].serviceUrl").value(hasItem("https://new.example.com")))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].apiVersion").value(hasItem("v2")))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].timeoutSeconds").value(hasItem(60)));
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
  void getTrustFrameworks_includesBundlesField() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].bundles[0].id").value(hasItem("gaia-x-2511")))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].bundles[0].roles.Participant").value(hasItem(true)))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].bundles[0].roles.ServiceOffering").value(hasItem(true)))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].bundles[0].roles.Resource").value(hasItem(true)));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getTrustFrameworks_mockFamily_includesMock2026Bundle() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'mock')].bundles[0].id").value(hasItem("mock-2026")));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setTrustFrameworkEnabled_mockFamily_activatesAndReflectsInGet() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/mock/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":true}")
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[?(@.id == 'mock')].enabled").value(hasItem(true)));

    mockMvc.perform(MockMvcRequestBuilders.put("/admin/trust-frameworks/mock/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"enabled\":false}")
            .with(csrf()))
        .andExpect(status().isOk());
  }
}
