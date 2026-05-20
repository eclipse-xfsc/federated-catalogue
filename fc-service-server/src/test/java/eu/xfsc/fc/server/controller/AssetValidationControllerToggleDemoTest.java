package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
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
 * Demonstration of the AU-01 acceptance criterion "the toggle takes effect": the SHACL
 * module toggle, flipped via the admin endpoint, observably changes the
 * {@code POST /assets/validate} response body for a subsequent request.
 *
 * <p>Why the multi-asset payload is used here:
 * {@code AssetValidationServiceImpl.validateMultipleAssets} consults the SHACL toggle
 * before any asset or schema lookup, so the gate's effect is observable without seeding
 * fixtures. The non-existent IDs are deliberate — when SHACL is enabled, the route
 * advances past the gate and fails on lookup; when SHACL is disabled, the gate
 * short-circuits with {@code module_disabled:SHACL} before lookup is attempted.
 *
 * <p>Controller-level tests for the JSON Schema and XML Schema toggles are not included
 * here because those toggles only fire on the single-asset planning paths (planExplicit /
 * planAllApplicable), which require seeded assets and stored schemas to exercise.
 * Unit-level coverage for those toggles lives in
 * {@code AssetValidationServiceImplTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetValidationControllerToggleDemoTest {

  private static final String MULTI_ASSET_BODY = """
      {
        "assetIds": ["urn:fake-asset-1", "urn:fake-asset-2"]
      }
      """;

  @Autowired
  private MockMvc mockMvc;

  @AfterEach
  void resetShaclToEnabled() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/schema-validation/modules/SHACL")
            .param("enabled", "true")
            .with(csrf())
            .with(adminUser()))
        .andExpect(status().isOk());
  }

  @Test
  void validateAssets_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(MULTI_ASSET_BODY)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void validateAssets_shaclModuleDisabled_returns400ModuleDisabled() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/schema-validation/modules/SHACL")
            .param("enabled", "false")
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(MULTI_ASSET_BODY)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("module_disabled:SHACL")));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void validateAssets_shaclModuleEnabled_doesNotReturnModuleDisabled() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .put("/admin/schema-validation/modules/SHACL")
            .param("enabled", "true")
            .with(csrf()))
        .andExpect(status().isOk());

    // With SHACL enabled, the gate passes; the route then proceeds to asset lookup,
    // which fails because the IDs are fake. The specific 4xx code does not matter here
    // (404 vs 422 is a routing decision that should stay free to evolve) — what matters
    // is that the response is a client error and is not the disabled-module 400 body.
    mockMvc.perform(MockMvcRequestBuilders.post("/assets/validate")
            .with(csrf())
            .contentType(MediaType.APPLICATION_JSON)
            .content(MULTI_ASSET_BODY)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().is4xxClientError())
        .andExpect(content().string(not(containsString("module_disabled"))));
  }

  private static org.springframework.test.web.servlet.request.RequestPostProcessor adminUser() {
    return org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
        .user("admin").roles(ADMIN_ALL);
  }
}
