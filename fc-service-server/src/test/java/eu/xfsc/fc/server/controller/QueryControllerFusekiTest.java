package eu.xfsc.fc.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.Error;
import eu.xfsc.fc.api.generated.model.QueryInfo;
import eu.xfsc.fc.api.generated.model.Results;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * REST-level integration test for the /query endpoint with SPARQL (Fuseki) backend.
 * Verifies the full HTTP flow: upload RDF claims, run SPARQL query via REST, verify JSON response.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
public class QueryControllerFusekiTest {

  private static final String OPENCYPHER_CONTENT_TYPE = "application/opencypher-query";
  private static final String SPARQL_CONTENT_TYPE = "application/sparql-query";
  private static final String CYPHER_QUERY = "MATCH (n) RETURN n LIMIT 1";

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
        new SdClaim(
            "<http://example.org/service1>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://example.org/ServiceOffering>"
        ),
        new SdClaim(
            "<http://example.org/service1>",
            "<http://example.org/name>",
            "\"Test SPARQL Service\""
        )
    ), "http://example.org/credential-fuseki-test");
  }

  @Test
  void postQuery_withSparqlSelect_returnsUploadedClaimData() throws Exception {
    String sparqlQuery = "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> "
        + "<https://www.w3.org/2018/credentials#credentialSubject> ?cs }";

    String response = postSparqlQuery(sparqlQuery)
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    Results result = objectMapper.readValue(response, Results.class);
    List<Map<String, Object>> items = result.getItems();
    assertEquals(2, items.size(), "Should return 2 results for 2 claims");

    boolean foundType = items.stream().anyMatch(item ->
        "http://example.org/service1".equals(item.get("s")) &&
        "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(item.get("p")) &&
        "http://example.org/ServiceOffering".equals(item.get("o")));
    boolean foundName = items.stream().anyMatch(item ->
        "http://example.org/service1".equals(item.get("s")) &&
        "http://example.org/name".equals(item.get("p")) &&
        "Test SPARQL Service".equals(item.get("o")));
    assertTrue(foundType, "Results should contain the rdf:type triple with ServiceOffering URI");
    assertTrue(foundName, "Results should contain the name literal triple");
  }

  @Test
  void postQuery_withSparqlInsert_returnsServerError() throws Exception {
    String insertQuery = "INSERT DATA { <http://example.org/s> "
        + "<http://example.org/p> <http://example.org/o> }";

    postSparqlQuery(insertQuery)
        .andExpect(status().is5xxServerError());
  }

  @Test
  void postQuery_withInvalidSparqlSyntax_returnsServerError() throws Exception {
    String badQuery = "SELCT ?s WERE { ?s ?p ?o }";

    postSparqlQuery(badQuery)
        .andExpect(status().is5xxServerError());
  }

  private ResultActions postSparqlQuery(String queryText) throws Exception {
    return mockMvc.perform(MockMvcRequestBuilders.post("/query")
        .content(queryText)
        .with(csrf())
        .contentType(SPARQL_CONTENT_TYPE)
        .header("Accept", "application/json"));
  }

  @Test
  public void postQuery_withOpenCypherContentType_returnsUnsupportedLanguageError() throws Exception {
    String response = mockMvc.perform(MockMvcRequestBuilders.post("/query")
            .content(CYPHER_QUERY)
            .with(csrf())
            .contentType(OPENCYPHER_CONTENT_TYPE)
            .header("Accept", "application/json"))
        .andExpect(status().isUnsupportedMediaType())
        .andReturn()
        .getResponse()
        .getContentAsString();

    Error error = objectMapper.readValue(response, Error.class);
    assertEquals("unsupported_query_language", error.getCode());
    assertTrue(error.getMessage().contains("OPENCYPHER"));
    assertTrue(error.getMessage().contains("SPARQL"));
  }

  @Test
  public void postQuery_withSparqlAndNoLimit_returnsResults() throws Exception {
    String sparqlNoLimit = "SELECT ?s ?p ?o WHERE { ?s ?p ?o }";

    String response = mockMvc.perform(MockMvcRequestBuilders.post("/query")
            .content(sparqlNoLimit)
            .with(csrf())
            .contentType(SPARQL_CONTENT_TYPE)
            .header("Accept", "application/json"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    Results result = objectMapper.readValue(response, Results.class);
    assertFalse(result.getItems().isEmpty(),
        "SPARQL query without LIMIT should succeed and return results (no limit injection for SPARQL)");
  }

  @Test
  public void getQueryInfo_onFusekiBackend_returnsFusekiInfo() throws Exception {
    String response = mockMvc.perform(MockMvcRequestBuilders.get("/query/info")
            .with(csrf())
            .header("Accept", "application/json"))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getContentAsString();

    QueryInfo info = objectMapper.readValue(response, QueryInfo.class);
    assertEquals("FUSEKI", info.getBackend());
    assertEquals("SPARQL", info.getQueryLanguage());
    assertEquals(Boolean.TRUE, info.getEnabled());
    assertNotNull(info.getExampleQuery());
    assertNotNull(info.getDocumentation());
  }

}