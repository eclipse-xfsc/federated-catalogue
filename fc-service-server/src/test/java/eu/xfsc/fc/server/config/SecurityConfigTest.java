package eu.xfsc.fc.server.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * Security-perimeter tests for {@link SecurityConfig}: unauthenticated
 * requests to {@code GET /api/**} (other than the generated OpenAPI document), {@code GET /actuator}
 * / {@code GET /actuator/**} (other than the health endpoint), and every method on
 * {@code /verification} must be rejected. {@code GET /actuator/health} and {@code GET /api/docs}
 * stay public — the former backs the container/orchestrator healthcheck and BDD "server is up"
 * precondition, the latter backs the deliberately-public Swagger UI.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class SecurityConfigTest {

  @Autowired
  private WebApplicationContext context;

  private MockMvc mockMvc;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  }

  @Test
  void getApiWildcard_unauthenticated_isRejected() throws Exception {
    // /api/docs (the generated OpenAPI document) is deliberately public — see getApiDocs below.
    // Every other path under /api/** is not: no controller is mounted there today (base-path is
    // empty, all controllers sit at root), so /api/probe reaches no handler, but the
    // AuthorizationFilter must still reject it with 401 BEFORE it ever reaches the dispatcher.
    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/probe").with(csrf())).andReturn();

    assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus(),
        "GET /api/** (other than /api/docs) must require authentication");
  }

  @Test
  void getApiDocs_unauthenticated_staysPublic() throws Exception {
    // Backs the deliberately-public Swagger UI (/swagger-ui/**); without it the UI has nothing to render.
    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/api/docs").with(csrf())).andReturn();

    assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus(),
        "/api/docs must remain publicly accessible and actually serve the OpenAPI document");
  }

  @Test
  void getActuatorRoot_unauthenticated_isRejected() throws Exception {
    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/actuator").with(csrf())).andReturn();

    assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus(),
        "GET /actuator must require authentication");
  }

  @Test
  void getActuatorHealth_unauthenticated_staysPublic() throws Exception {
    // Backs the container/orchestrator healthcheck and the BDD suite's "server is up" precondition.
    // management.endpoint.health.show-details=when_authorized means anonymous callers only ever see
    // the UP/DOWN summary, never component details, so leaving this open does not leak internals.
    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/health").with(csrf())).andReturn();

    assertEquals(HttpStatus.OK.value(), result.getResponse().getStatus(),
        "/actuator/health must remain publicly accessible and actually respond");
    assertFalse(result.getResponse().getContentAsString().contains("components"),
        "anonymous callers must only see the UP/DOWN summary, never component health details");
  }

  @Test
  void getActuatorInfo_unauthenticated_isRejected() throws Exception {
    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/info").with(csrf())).andReturn();

    assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus(),
        "GET /actuator/info must require authentication");
  }

  @Test
  void getVerification_unauthenticated_isRejected() throws Exception {
    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.get("/verification").with(csrf())).andReturn();

    assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus(),
        "GET /verification must require authentication");
  }

  @Test
  void postVerification_unauthenticated_isRejected() throws Exception {
    MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/verification")
            .contentType("application/json")
            .content("{}")
            .with(csrf()))
        .andReturn();

    assertEquals(HttpStatus.UNAUTHORIZED.value(), result.getResponse().getStatus(),
        "POST /verification must require authentication");
  }
}
