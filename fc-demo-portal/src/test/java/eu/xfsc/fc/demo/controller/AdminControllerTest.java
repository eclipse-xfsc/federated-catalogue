package eu.xfsc.fc.demo.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Client;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import eu.xfsc.fc.api.generated.model.RebuildStatus;
import eu.xfsc.fc.api.generated.model.SchemaModulePatch;
import eu.xfsc.fc.api.generated.model.TrustFrameworkPatch;
import eu.xfsc.fc.api.generated.model.TrustFrameworkBaseClassPatch;
import eu.xfsc.fc.client.AdminClient;
import eu.xfsc.fc.demo.config.SecurityConfig;

/**
 * Tests for the AdminController proxy endpoints. Covers both authentication enforcement and
 * that authenticated requests forward to the {@link AdminClient} for each PATCH route. The
 * proxy layer is thin, but mis-mapped routes silently fall through to Spring's
 * static-resource handler and surface as a confusing 404 to the browser — these tests pin
 * each route mapping.
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

  private static final String REG_ID = "fc-client-oidc";
  private static final String ENABLED_TRUE = """
      {"enabled":true}
      """;
  private static final String BUNDLE_CONFIG_PATCH = """
      {"serviceUrl":"https://mock.test/v2"}
      """;

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminClient adminClient;

  @MockitoBean
  private ClientRegistrationRepository clientRegistrationRepository;

  @Test
  void getAdminStats_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/admin/stats"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void getAdminHealth_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/admin/health"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void getTrustFrameworks_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/admin/trust-frameworks"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void patchTrustFramework_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(patch("/admin/trust-frameworks/gaia-x")
            .contentType(MediaType.APPLICATION_JSON)
            .content(ENABLED_TRUE))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void patchTrustFrameworkBaseClass_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(patch("/admin/trust-frameworks/gaia-x-2511/base-classes/Participant")
            .contentType(MediaType.APPLICATION_JSON)
            .content(ENABLED_TRUE))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void patchSchemaModule_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(patch("/admin/schema-validation/modules/SHACL")
            .contentType(MediaType.APPLICATION_JSON)
            .content(ENABLED_TRUE))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void patchTrustFramework_authenticated_forwardsToAdminClient() throws Exception {
    mockMvc.perform(patch("/admin/trust-frameworks/gaia-x")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(ENABLED_TRUE))
        .andExpect(status().isOk());

    verify(adminClient).patchTrustFramework(eq("gaia-x"), any(TrustFrameworkPatch.class),
        any(OAuth2AuthorizedClient.class));
  }

  @Test
  void patchTrustFrameworkBaseClass_authenticated_forwardsToAdminClient() throws Exception {
    mockMvc.perform(patch("/admin/trust-frameworks/gaia-x-2511/base-classes/ServiceOffering")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(ENABLED_TRUE))
        .andExpect(status().isOk());

    verify(adminClient).patchTrustFrameworkBaseClass(eq("gaia-x-2511"), eq("ServiceOffering"),
        any(TrustFrameworkBaseClassPatch.class), any(OAuth2AuthorizedClient.class));
  }

  @Test
  void patchSchemaModule_authenticated_forwardsToAdminClient() throws Exception {
    mockMvc.perform(patch("/admin/schema-validation/modules/SHACL")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(ENABLED_TRUE))
        .andExpect(status().isOk());

    verify(adminClient).patchSchemaModule(eq("SHACL"), any(SchemaModulePatch.class),
        any(OAuth2AuthorizedClient.class));
  }

  @Test
  void patchTrustFrameworkBundleConfig_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .contentType(MediaType.APPLICATION_JSON)
            .content(BUNDLE_CONFIG_PATCH))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void patchTrustFrameworkBundleConfig_authenticated_forwardsToAdminClient() throws Exception {
    mockMvc.perform(patch("/admin/trust-frameworks/bundles/gaia-x-2511")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID))
            .contentType(MediaType.APPLICATION_JSON)
            .content(BUNDLE_CONFIG_PATCH))
        .andExpect(status().isOk());

    verify(adminClient).patchTrustFrameworkBundleConfig(eq("gaia-x-2511"),
        any(Map.class), any(OAuth2AuthorizedClient.class));
  }

  @Test
  void deleteTrustFrameworkBundleConfig_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(delete("/admin/trust-frameworks/bundles/gaia-x-2511"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void deleteTrustFrameworkBundleConfig_authenticated_returns200() throws Exception {
    mockMvc.perform(delete("/admin/trust-frameworks/bundles/gaia-x-2511")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID)))
        .andExpect(status().isOk());
  }

  @Test
  void deleteTrustFrameworkBundleConfig_authenticated_forwardsToAdminClient() throws Exception {
    mockMvc.perform(delete("/admin/trust-frameworks/bundles/gaia-x-2511")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID)))
        .andExpect(status().isOk());

    verify(adminClient).deleteTrustFrameworkBundleConfig(eq("gaia-x-2511"),
        any(OAuth2AuthorizedClient.class));
  }

  @Test
  void triggerGraphRebuild_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(post("/admin/graph/rebuild"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void getGraphRebuildStatus_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/admin/graph/rebuild/status"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void triggerGraphRebuild_authenticated_started_returns202() throws Exception {
    when(adminClient.triggerGraphRebuild(any(OAuth2AuthorizedClient.class)))
        .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body(new RebuildStatus()));

    mockMvc.perform(post("/admin/graph/rebuild")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID)))
        .andExpect(status().isAccepted());

    verify(adminClient).triggerGraphRebuild(any(OAuth2AuthorizedClient.class));
  }

  @Test
  void triggerGraphRebuild_authenticated_alreadyRunning_returns409() throws Exception {
    when(adminClient.triggerGraphRebuild(any(OAuth2AuthorizedClient.class)))
        .thenReturn(ResponseEntity.status(HttpStatus.CONFLICT).body(new RebuildStatus()));

    mockMvc.perform(post("/admin/graph/rebuild")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID)))
        .andExpect(status().isConflict());
  }

  @Test
  void getGraphRebuildStatus_authenticated_forwardsToAdminClient() throws Exception {
    when(adminClient.getGraphRebuildStatus(any(OAuth2AuthorizedClient.class)))
        .thenReturn(new RebuildStatus());

    mockMvc.perform(get("/admin/graph/rebuild/status")
            .with(oauth2Login())
            .with(oauth2Client(REG_ID)))
        .andExpect(status().isOk());

    verify(adminClient).getGraphRebuildStatus(any(OAuth2AuthorizedClient.class));
  }
}
