package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.api.FcMediaTypes.MERGE_PATCH_JSON_VALUE;
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
  public static final String BUNDLE_CONFIG_SERVICE_URL_OVERRIDE = """
      {"serviceUrl":"https://mock.test/v2"}
      """;
  public static final String BUNDLE_CONFIG_SERVICE_URL_NULL = """
      {"serviceUrl":null}
      """;
  public static final String BUNDLE_CONFIG_UNKNOWN_PROPERTY = """
      {"unknownProperty":"x"}
      """;
  public static final String BUNDLE_CONFIG_WRONG_TYPE = """
      {"timeoutSeconds":"not-a-number"}
      """;
  public static final String BUNDLE_CONFIG_FULL_OVERRIDE = """
      {
        "clientType":"jwt-vc-compliance",
        "serviceUrl":"https://mock.test/v2",
        "compliancePath":"/api/credential-offers/standard-compliance",
        "apiVersion":"v2",
        "timeoutSeconds":15,
        "trustAnchorUrl":"https://registry.test/v1/trust-anchors"
      }
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


  @Test
  @WithMockUser
  void patchTrustFramework_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  void patchTrustFramework_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFramework_enabledField_togglesState() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[?(@.id == 'gaia-x')].enabled").value(hasItem(true)));

    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/gaia-x")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_FALSE)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFramework_unknownId_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/nonexistent")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkRole_knownBundleAndRole_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_FALSE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkRole_unknownBundle_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/nonexistent-bundle/roles/Participant")
            .contentType(MERGE_PATCH_JSON_VALUE)
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
            .contentType(MERGE_PATCH_JSON_VALUE)
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
            .contentType(MERGE_PATCH_JSON_VALUE)
            // missing body
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkRole_emptyBody_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content("{}")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchTrustFrameworkRole_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void patchTrustFrameworkRole_wrongRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/gaia-x-2511/roles/Participant")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getTrustFrameworks_bundleEntry_includesYamlEffectiveConfigBeforeAnyOverride() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'mock')].bundles[0].effectiveConfig.serviceUrl")
            .value(hasItem(notNullValue())))
        .andExpect(jsonPath("$[?(@.id == 'mock')].bundles[0].effectiveConfig.compliancePath")
            .value(hasItem(notNullValue())))
        .andExpect(jsonPath("$[?(@.id == 'mock')].bundles[0].overriddenFields")
            .value(hasItem(hasSize(0))));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getTrustFrameworks_bundleEntry_reflectsPatchedOverrideInEffectiveConfig() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/mock-2026")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_SERVICE_URL_OVERRIDE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[?(@.id == 'mock')].bundles[0].effectiveConfig.serviceUrl")
            .value(hasItem("https://mock.test/v2")))
        .andExpect(jsonPath("$[?(@.id == 'mock')].bundles[0].overriddenFields")
            .value(hasItem(hasItem("serviceUrl"))));

    mockMvc.perform(MockMvcRequestBuilders
            .delete("/admin/trust-frameworks/bundles/mock-2026")
            .with(csrf()))
        .andExpect(status().isOk());
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
  void patchTrustFrameworkBundleConfig_serviceUrlField_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_SERVICE_URL_OVERRIDE)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkBundleConfig_allFields_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_FULL_OVERRIDE)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkBundleConfig_unknownBundle_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/nonexistent-bundle")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_SERVICE_URL_OVERRIDE)
            .with(csrf()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message", notNullValue()));
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkBundleConfig_emptyBody_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content("{}")
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkBundleConfig_missingBody_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            // missing body
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void patchTrustFrameworkBundleConfig_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_SERVICE_URL_OVERRIDE)
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void patchTrustFrameworkBundleConfig_wrongRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_SERVICE_URL_OVERRIDE)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkBundleConfig_explicitNull_clearsOverride() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_SERVICE_URL_OVERRIDE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_SERVICE_URL_NULL)
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkBundleConfig_unknownProperty_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_UNKNOWN_PROPERTY)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFrameworkBundleConfig_wrongType_returns400() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_WRONG_TYPE)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void deleteTrustFrameworkBundleConfig_existingOverride_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(BUNDLE_CONFIG_SERVICE_URL_OVERRIDE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders
            .delete("/admin/trust-frameworks/bundles/gaia-x-2511")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void deleteTrustFrameworkBundleConfig_noOverrideRow_isIdempotent() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .delete("/admin/trust-frameworks/bundles/gaia-x-2511")
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders
            .delete("/admin/trust-frameworks/bundles/gaia-x-2511")
            .with(csrf()))
        .andExpect(status().isOk());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void deleteTrustFrameworkBundleConfig_unknownBundle_returns404() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .delete("/admin/trust-frameworks/bundles/nonexistent-bundle")
            .with(csrf()))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteTrustFrameworkBundleConfig_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .delete("/admin/trust-frameworks/bundles/gaia-x-2511")
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser
  void deleteTrustFrameworkBundleConfig_wrongRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders
            .delete("/admin/trust-frameworks/bundles/gaia-x-2511")
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void patchTrustFramework_mockFamily_activatesAndReflectsInGet() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/mock")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_TRUE)
            .with(csrf()))
        .andExpect(status().isOk());

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$[?(@.id == 'mock')].enabled").value(hasItem(true)));

    mockMvc.perform(MockMvcRequestBuilders.patch("/admin/trust-frameworks/mock")
            .contentType(MERGE_PATCH_JSON_VALUE)
            .content(ENABLED_FALSE)
            .with(csrf()))
        .andExpect(status().isOk());
  }
}
