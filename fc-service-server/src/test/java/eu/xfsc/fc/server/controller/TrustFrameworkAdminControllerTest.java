package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
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

  public static final String ENABLED_FALSE = """
      {"enabled":false}
      """;
  public static final String ENABLED_TRUE = """
      {"enabled":true}
      """;
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

  private static final String MERGE_PATCH_JSON = "application/merge-patch+json";

  @Test
  @WithMockUser
  void patchTrustFramework_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void patchTrustFramework_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFramework_enabledField_togglesState() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].enabled").value(hasItem(true)));

    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_FALSE)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFramework_unknownId_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/nonexistent")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFramework_configFields_updatesConfig() throws Exception {
    String body = """
        {
          "serviceUrl":"https://new.example.com",
          "apiVersion":"v2",
          "timeoutSeconds":60
        }
        """;

    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].serviceUrl").value(hasItem("https://new.example.com")))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].apiVersion").value(hasItem("v2")))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].timeoutSeconds").value(hasItem(60)));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFramework_enabledAndConfigFields_appliesBoth() throws Exception {
    String body = """
        {
          "enabled":true,
          "serviceUrl":"https://combined.example.com",
          "apiVersion":"v3",
          "timeoutSeconds":45
        }
        """;

    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].enabled").value(hasItem(true)))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].serviceUrl").value(hasItem("https://combined.example.com")))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].apiVersion").value(hasItem("v3")))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].timeoutSeconds").value(hasItem(45)));

    // Reset
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_FALSE)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkRole_knownBundleAndRole_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_FALSE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkRole_unknownBundle_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/nonexistent-bundle/roles/Participant")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", notNullValue()));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkRole_unknownRole_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/UnknownRole")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", notNullValue()));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkRole_missingBody_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON)
            // missing body
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkRole_emptyBody_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON)
            .content("{}")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchTrustFrameworkRole_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void patchTrustFrameworkRole_wrongRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
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
  void patchTrustFramework_mockFamily_activatesAndReflectsInGet() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/mock")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[?(@.id == 'mock')].enabled").value(hasItem(true)));

    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/mock")
            .contentType(MERGE_PATCH_JSON)
            .content(ENABLED_FALSE)
            .with(csrf()))
        .andExpect(status().isOk());
  }
}
