package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
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
 * Integration tests for Schema Validation Admin endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class SchemaValidationAdminControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getSchemaValidation_withAdminRole_returnsModules() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/schema-validation")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalSchemaCount").value(greaterThanOrEqualTo(0)))
        .andExpect(jsonPath("$.modules", hasSize(4)))
        .andExpect(jsonPath("$.modules[?(@.type=='SHACL')]").exists())
        .andExpect(jsonPath("$.modules[?(@.type=='JSON_SCHEMA')]").exists())
        .andExpect(jsonPath("$.modules[?(@.type=='XML_SCHEMA')]").exists())
        .andExpect(jsonPath("$.modules[?(@.type=='OWL')]").exists());
  }

  @Test
  @WithMockUser
  void getSchemaValidation_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/schema-validation")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void getSchemaValidation_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/schema-validation")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void setModuleEnabled_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/schema-validation/modules/SHACL")
            .param("enabled", "false")
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void setModuleEnabled_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/schema-validation/modules/SHACL")
            .param("enabled", "false")
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setModuleEnabled_validType_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/schema-validation/modules/SHACL")
            .param("enabled", "false")
            .with(csrf()))
        .andExpect(status().isOk());

    // Verify toggle took effect
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/schema-validation")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.modules[?(@.type=='SHACL')].enabled").value(false));

    // Reset
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/schema-validation/modules/SHACL")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void setModuleEnabled_invalidType_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/schema-validation/modules/INVALID")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }
}
