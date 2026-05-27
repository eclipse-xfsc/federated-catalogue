package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.isA;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.server.service.GraphStoreProbe;
import eu.xfsc.fc.server.service.graphdb.RoutingGraphStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * Integration tests for Graph Database Admin endpoints.
 *
 * <p>{@link GraphStoreProbe} is mocked here — the probe makes real network connections
 * to the configured backend URIs, which the embedded Fuseki / unit-test environment
 * cannot satisfy. The probe itself is covered by its own unit test.
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

  @Autowired
  private AdminConfigRepository adminConfigRepository;

  @MockitoBean
  private GraphStoreProbe graphStoreProbe;

  @BeforeEach
  void resetProbe() {
    when(graphStoreProbe.probe(any()))
        .thenReturn(GraphStoreProbe.Result.reachable("ok"));
    adminConfigRepository.deleteById(RoutingGraphStore.KEY_PREFERRED_BACKEND);
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  void getGraphDatabaseStatus_withAdminRole_returnsStatus() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph-database")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeBackend").value(isA(String.class)))
        .andExpect(jsonPath("$.connected").value(isA(Boolean.class)))
        .andExpect(jsonPath("$.claimCount").value(isA(Number.class)))
        .andExpect(jsonPath("$.rebuildNeeded").value(isA(Boolean.class)))
        .andExpect(jsonPath("$.rdfAssetCount").value(isA(Number.class)))
        .andExpect(jsonPath("$.preferredBackend").doesNotExist())
        .andExpect(jsonPath("$.restartRequired").doesNotExist());
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
  void switchGraphDatabase_validBackend_appliesLiveAndPersists() throws Exception {
    // FUSEKI is the adapter wired in this test context (graphstore.impl=fuseki).
    mockMvc.perform(MockMvcRequestBuilders.post("/admin/graph-database/switch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"backend\":\"FUSEKI\"}")
            .with(csrf()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value(isA(String.class)))
        .andExpect(jsonPath("$.restartRequired").doesNotExist());

    String persisted = adminConfigRepository.findById(RoutingGraphStore.KEY_PREFERRED_BACKEND)
        .orElseThrow().getConfigValue();
    org.junit.jupiter.api.Assertions.assertEquals("FUSEKI", persisted);

    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph-database")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.activeBackend").value("FUSEKI"));
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
  @WithMockUser(roles = {ADMIN_ALL})
  void switchGraphDatabase_targetUnreachable_returns400WithReason() throws Exception {
    when(graphStoreProbe.probe(any()))
        .thenReturn(GraphStoreProbe.Result.unreachable("Fuseki at http://fuseki:3030/ds connection refused"));

    mockMvc.perform(MockMvcRequestBuilders.post("/admin/graph-database/switch")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"backend\":\"FUSEKI\"}")
            .with(csrf()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value(containsString("connection refused")));

    org.junit.jupiter.api.Assertions.assertTrue(
        adminConfigRepository.findById(RoutingGraphStore.KEY_PREFERRED_BACKEND).isEmpty(),
        "Preference must not be persisted when probe rejects the target");
  }

  @Test
  void getGraphDatabaseStatus_unauthenticated_returns401() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph-database")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());
  }
}
