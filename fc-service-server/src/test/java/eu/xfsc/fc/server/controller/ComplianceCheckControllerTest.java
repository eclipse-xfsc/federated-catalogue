package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ASSET_READ;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_UPDATE;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.core.service.trustframework.ValidationType;
import eu.xfsc.fc.core.service.trustframework.compliance.ComplianceCheckOrchestrator;
import eu.xfsc.fc.core.service.trustframework.compliance.IssuedAttestation;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * Integration tests for the compliance check endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class ComplianceCheckControllerTest {

  private static final String ASSET_ID = "urn:example:test-asset-001";
  private static final String MOCK_PROFILE_ID = "mock-2026";
  private static final String UNKNOWN_PROFILE_ID = "no-such-profile";
  private static final String CANNED_VC_JWT =
      "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
          + ".eyJpc3MiOiJkaWQ6d2ViOmNvbXBsaWFuY2UuZXhhbXBsZSIsImV4cCI6MTc2NzIyMzk5OX0.";
  // JWT with payload {"id":"urn:example:test-asset-001"}
  private static final String TEST_VP_JWT =
      "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
          + ".eyJpZCI6InVybjpleGFtcGxlOnRlc3QtYXNzZXQtMDAxIn0.";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ComplianceCheckOrchestrator orchestrator;

  @MockitoBean
  private TrustFrameworkService trustFrameworkService;

  @MockitoBean
  private TrustFrameworkRegistry registry;

  @Test
  @WithMockUser(roles = {ASSET_UPDATE})
  void runComplianceCheck_unknownProfileId_returns400() throws Exception {
    when(orchestrator.check(any(), eq(UNKNOWN_PROFILE_ID), any()))
        .thenThrow(new ClientException("Unknown trust-framework profile: " + UNKNOWN_PROFILE_ID));

    String body = """
        {"frameworkProfileId": "%s", "credential": "%s"}
        """.formatted(UNKNOWN_PROFILE_ID, TEST_VP_JWT);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/compliance-check", ASSET_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isBadRequest());
  }

  @Test
  @WithMockUser(roles = {ASSET_UPDATE})
  void runComplianceCheck_familyDisabled_returns409() throws Exception {
    when(orchestrator.check(any(), eq(MOCK_PROFILE_ID), any()))
        .thenThrow(new ConflictException("Trust-framework family is disabled: mock"));

    String body = """
        {"frameworkProfileId": "%s", "credential": "%s"}
        """.formatted(MOCK_PROFILE_ID, TEST_VP_JWT);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/compliance-check", ASSET_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isConflict());
  }

  @Test
  @WithMockUser(roles = {ASSET_UPDATE})
  void runComplianceCheck_compliantOutcome_returns200WithConformsTrue() throws Exception {
    when(orchestrator.check(any(), eq(MOCK_PROFILE_ID), any()))
        .thenReturn(new IssuedAttestation(CANNED_VC_JWT, null));

    String body = """
        {"frameworkProfileId": "%s", "credential": "%s"}
        """.formatted(MOCK_PROFILE_ID, TEST_VP_JWT);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/compliance-check", ASSET_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.conforms").value(true))
        .andExpect(jsonPath("$.attestationCredential").value(CANNED_VC_JWT));
  }

  @Test
  @WithMockUser(roles = {ASSET_READ})
  void getComplianceChecks_authenticated_returns200WithArray() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/compliance-checks", ASSET_ID)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray());
  }

  @Test
  @WithMockUser
  void getTrustFrameworksPublic_noEnabledFrameworks_returnsEmptyArray() throws Exception {
    when(trustFrameworkService.findAll()).thenReturn(List.of(
        new TrustFrameworkConfig("gaia-x", "Gaia-X Trust Framework", null, "22.10", 30, false, null, null)));
    when(registry.getActiveBundles()).thenReturn(List.of());

    mockMvc.perform(MockMvcRequestBuilders.get("/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  @WithMockUser
  void getTrustFrameworksPublic_enabledFramework_returnsEntryWithIdAndName() throws Exception {
    when(trustFrameworkService.findAll()).thenReturn(List.of(
        new TrustFrameworkConfig("gaia-x", "Gaia-X Trust Framework", null, "22.10", 30, true, null, null)));
    when(registry.getActiveBundles()).thenReturn(List.of());

    mockMvc.perform(MockMvcRequestBuilders.get("/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("gaia-x"))
        .andExpect(jsonPath("$[0].name").value("Gaia-X Trust Framework"))
        .andExpect(jsonPath("$[0].profiles").isArray())
        .andExpect(jsonPath("$[0].profiles").isEmpty());
  }

  @Test
  @WithMockUser
  void getTrustFrameworksPublic_enabledFrameworkWithActiveProfile_returnsProfileInEntry() throws Exception {
    when(trustFrameworkService.findAll()).thenReturn(List.of(
        new TrustFrameworkConfig("gaia-x", "Gaia-X Trust Framework", null, "22.10", 30, true, null, null)));
    var bundleCfg = new FrameworkBundleConfig("gaia-x-2511", "gaia-x",
        "https://compliance.gaia-x.eu/", ValidationType.SHACL, Map.of(), Map.of());
    when(registry.getActiveBundles()).thenReturn(List.of(new TrustFrameworkBundle(bundleCfg, null, null)));

    mockMvc.perform(MockMvcRequestBuilders.get("/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("gaia-x"))
        .andExpect(jsonPath("$[0].profiles[0]").value("gaia-x-2511"));
  }

  @Test
  @WithMockUser(roles = {ASSET_UPDATE})
  void runComplianceCheck_serviceTimeout_returns504() throws Exception {
    when(orchestrator.check(any(), eq(MOCK_PROFILE_ID), any()))
        .thenThrow(new TimeoutException("Compliance service timed out"));

    String body = """
        {"frameworkProfileId": "%s", "credential": "%s"}
        """.formatted(MOCK_PROFILE_ID, TEST_VP_JWT);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/compliance-check", ASSET_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isGatewayTimeout());
  }

  // Security: unauthenticated POST → 401
  @Test
  void runComplianceCheck_unauthenticated_returns401() throws Exception {
    String body = """
        {"frameworkProfileId": "%s", "credential": "%s"}
        """.formatted(MOCK_PROFILE_ID, TEST_VP_JWT);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/compliance-check", ASSET_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isUnauthorized());
  }

  // Security: wrong role for POST → 403
  @Test
  @WithMockUser(roles = {ASSET_READ})
  void runComplianceCheck_insufficientRole_returns403() throws Exception {
    String body = """
        {"frameworkProfileId": "%s", "credential": "%s"}
        """.formatted(MOCK_PROFILE_ID, TEST_VP_JWT);

    mockMvc.perform(MockMvcRequestBuilders.post("/assets/{id}/compliance-check", ASSET_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(body)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  // Security: unauthenticated GET compliance-checks → 401
  @Test
  void getComplianceChecks_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/assets/{id}/compliance-checks", ASSET_ID)
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }

  // Security: unauthenticated GET trust-frameworks → 401
  @Test
  void getTrustFrameworksPublic_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/trust-frameworks")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
}
