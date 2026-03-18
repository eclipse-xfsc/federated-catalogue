package eu.xfsc.fc.server.controller;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=neo4j"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class GraphAdminControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  public void postRebuild_withAdminRole_returns202() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/admin/graph/rebuild")
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf()))
        .andExpect(status().isAccepted());
  }

  @Test
  @WithMockUser
  public void postRebuild_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.post("/admin/graph/rebuild")
            .contentType(MediaType.APPLICATION_JSON)
            .with(csrf()))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  public void getRebuildStatus_withAdminRole_returns200() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph/rebuild/status")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.total").exists())
        .andExpect(jsonPath("$.processed").exists())
        .andExpect(jsonPath("$.percentComplete").exists());
  }

  @Test
  @WithMockUser
  public void getRebuildStatus_withoutAdminRole_returns403() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph/rebuild/status")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(roles = {ADMIN_ALL})
  public void getGraphStatus_withAdminRole_returnsBackendInfo() throws Exception {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/graph/status")
            .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.backend").exists())
        .andExpect(jsonPath("$.enabled").exists())
        .andExpect(jsonPath("$.healthy").exists())
        .andExpect(jsonPath("$.syncAssessment").exists());
  }
}
