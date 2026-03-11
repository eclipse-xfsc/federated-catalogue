package eu.xfsc.fc.server.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
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
import org.springframework.http.MediaType;
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
    String sparqlStatement = "{\"statement\": \"SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> "
        + "<https://www.w3.org/2018/credentials#credentialSubject> ?cs }\", \"parameters\": null}";

    String response = postSparqlQuery(sparqlStatement)
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
    String insertStatement = "{\"statement\": \"INSERT DATA { <http://example.org/s> "
        + "<http://example.org/p> <http://example.org/o> }\", \"parameters\": null}";

    postSparqlQuery(insertStatement)
        .andExpect(status().is5xxServerError());
  }

  @Test
  void postQuery_withInvalidSparqlSyntax_returnsServerError() throws Exception {
    String badStatement = "{\"statement\": \"SELCT ?s WERE { ?s ?p ?o }\", \"parameters\": null}";

    postSparqlQuery(badStatement)
        .andExpect(status().is5xxServerError());
  }

  private ResultActions postSparqlQuery(String jsonBody) throws Exception {
    return mockMvc.perform(MockMvcRequestBuilders.post("/query")
        .content(jsonBody)
        .with(csrf())
        .contentType(MediaType.APPLICATION_JSON)
        .queryParam("queryLanguage", QueryLanguage.SPARQL.getValue())
        .header("Accept", "application/json"));
  }
}