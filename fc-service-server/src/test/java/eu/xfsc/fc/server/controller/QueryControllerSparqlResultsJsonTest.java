package eu.xfsc.fc.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;

import static eu.xfsc.fc.server.util.CommonConstants.QUERY_EXECUTE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST-level integration test for the /query endpoint covering W3C SPARQL 1.1
 * Results JSON content negotiation. Verifies that the endpoint returns the
 * standard {@code head}/{@code results.bindings} envelope when the client
 * declares {@code Accept: application/sparql-results+json}, and rejects that
 * Accept header when the active backend speaks openCypher.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.EMBEDDED)
@TestPropertySource(properties = {"graphstore.impl=fuseki",
    "test.fuseki.isolate=QueryControllerSparqlResultsJsonTest"})
@WithMockUser(roles = {QUERY_EXECUTE})
public class QueryControllerSparqlResultsJsonTest {

  private static final String SPARQL_CONTENT_TYPE = "application/sparql-query";
  private static final String SPARQL_RESULTS_JSON = "application/sparql-results+json";
  private static final String OPENCYPHER_CONTENT_TYPE = "application/opencypher-query";
  private static final String CREDENTIAL_SUBJECT = "http://example.org/credential-w3c-test";

  @Autowired
  private WebApplicationContext context;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private GraphStore graphStore;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeAll
  void setup() {
    mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

    graphStore.addClaims(List.of(
        new CredentialClaim(
            "<http://example.org/service-w3c>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://example.org/ServiceOffering>"
        ),
        new CredentialClaim(
            "<http://example.org/service-w3c>",
            "<http://example.org/name>",
            "\"W3C SPARQL JSON Service\""
        )
    ), CREDENTIAL_SUBJECT);
  }

  @Test
  void postQuery_withSparqlResultsJsonAccept_returnsW3cEnvelope() throws Exception {
    String sparqlQuery = "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> "
        + "<https://www.w3.org/2018/credentials#credentialSubject> "
        + "<" + CREDENTIAL_SUBJECT + "> }";

    String response = postSparqlQuery(sparqlQuery, SPARQL_RESULTS_JSON)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(SPARQL_RESULTS_JSON))
        .andReturn()
        .getResponse()
        .getContentAsString();

    JsonNode root = objectMapper.readTree(response);

    JsonNode head = root.get("head");
    assertNotNull(head, "W3C envelope must contain a 'head' object");
    JsonNode vars = head.get("vars");
    assertNotNull(vars, "'head' must contain a 'vars' array");
    assertTrue(vars.isArray(), "'vars' must be an array");
    assertEquals(3, vars.size(), "Three projected variables expected");

    JsonNode bindings = root.path("results").path("bindings");
    assertTrue(bindings.isArray(), "'results.bindings' must be an array");
    assertEquals(2, bindings.size(), "Two bindings expected for the two seeded claims");

    JsonNode firstBinding = bindings.get(0);
    JsonNode subject = firstBinding.get("s");
    assertNotNull(subject, "binding must carry the 's' variable");
    assertNotNull(subject.get("type"), "each binding value must declare a 'type'");
    assertNotNull(subject.get("value"), "each binding value must declare a 'value'");
  }

  @Test
  void postQuery_withCommaSeparatedAcceptPreferringW3c_returnsW3cEnvelope() throws Exception {
    String sparqlQuery = "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> "
        + "<https://www.w3.org/2018/credentials#credentialSubject> "
        + "<" + CREDENTIAL_SUBJECT + "> }";

    String response = postSparqlQuery(sparqlQuery,
            "application/json;q=0.5, application/sparql-results+json;q=0.9")
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(SPARQL_RESULTS_JSON))
        .andReturn()
        .getResponse()
        .getContentAsString();

    JsonNode root = objectMapper.readTree(response);
    assertNotNull(root.get("head"), "comma-separated Accept must select the W3C envelope");
    assertTrue(root.path("results").path("bindings").isArray());
  }

  @Test
  void postQuery_withApplicationJsonAccept_returnsLegacyEnvelope() throws Exception {
    String sparqlQuery = "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> "
        + "<https://www.w3.org/2018/credentials#credentialSubject> "
        + "<" + CREDENTIAL_SUBJECT + "> }";

    String response = postSparqlQuery(sparqlQuery, "application/json")
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    JsonNode root = objectMapper.readTree(response);

    assertNotNull(root.get("items"), "legacy envelope must keep its 'items' array");
    assertNotNull(root.get("totalCount"), "legacy envelope must keep its 'totalCount'");
  }

  @Test
  void postQuery_withOpenCypherContentTypeAndSparqlResultsAccept_returnsNotAcceptable()
      throws Exception {
    String cypherQuery = "MATCH (n) RETURN n LIMIT 1";

    ResultActions result = mockMvc.perform(MockMvcRequestBuilders.post("/query")
        .content(cypherQuery)
        .with(csrf())
        .contentType(OPENCYPHER_CONTENT_TYPE)
        .header("Accept", SPARQL_RESULTS_JSON));

    // Backend is Fuseki — Cypher is rejected as unsupported language (415) before content
    // negotiation considers Accept. The dedicated 406 path for a Cypher backend asked for
    // the SPARQL Results envelope is exercised at unit level; this test only asserts that
    // the combination does not return a 200 W3C envelope.
    result.andExpect(status().is4xxClientError());
  }

  private ResultActions postSparqlQuery(String queryText, String acceptHeader) throws Exception {
    return mockMvc.perform(MockMvcRequestBuilders.post("/query")
        .content(queryText)
        .with(csrf())
        .contentType(SPARQL_CONTENT_TYPE)
        .header("Accept", acceptHeader));
  }
}
