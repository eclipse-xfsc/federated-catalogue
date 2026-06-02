package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.api.generated.model.Results;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.server.config.QueryProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the response-shape negotiation logic on {@link QueryService}. The default
 * envelope (custom {@code Results} POJO under {@code application/json}) is preserved unless the
 * client explicitly lists {@code application/sparql-results+json} on the {@code Accept} header,
 * in which case the W3C SPARQL 1.1 Results JSON document is returned verbatim. Errors from the
 * graph store opting out of the W3C path surface as {@code 406 Not Acceptable}.
 */
@ExtendWith(MockitoExtension.class)
class QueryServiceContentNegotiationTest {

  private static final String SPARQL_BODY = "SELECT ?s WHERE { ?s ?p ?o } LIMIT 1";
  private static final String W3C_JSON = "{\"head\":{\"vars\":[\"s\"]},\"results\":{\"bindings\":[]}}";

  @Mock private GraphStore graphStore;
  @Mock private QueryLanguageValidator queryLanguageValidator;
  @Mock private HttpServletRequest httpServletRequest;
  @Mock private QueryProperties queryProperties;

  @InjectMocks private QueryService queryService;

  @BeforeEach
  void primeRequestContentType() {
    when(httpServletRequest.getContentType()).thenReturn("application/sparql-query");
  }

  @Test
  void query_acceptHeaderListsW3cFormat_returnsW3cBodyAndContentType() {
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT)).thenReturn("application/sparql-results+json");
    when(graphStore.queryDataAsSparqlResultsJson(any(GraphQuery.class))).thenReturn(Optional.of(W3C_JSON));

    ResponseEntity<?> response = queryService.query(SPARQL_BODY, 5, true);

    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/sparql-results+json"));
    assertThat(response.getBody()).isEqualTo(W3C_JSON);
    verify(graphStore, never()).queryData(any(GraphQuery.class));
  }

  @Test
  void query_acceptHeaderListsW3cFormatWithQWeights_stillSelectsW3c() {
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT))
        .thenReturn("application/json;q=0.5, application/sparql-results+json;q=0.9");
    when(graphStore.queryDataAsSparqlResultsJson(any(GraphQuery.class))).thenReturn(Optional.of(W3C_JSON));

    ResponseEntity<?> response = queryService.query(SPARQL_BODY, 5, true);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/sparql-results+json"));
    assertThat(response.getBody()).isEqualTo(W3C_JSON);
  }

  @Test
  void query_acceptHeaderListsGenericJsonOnly_returnsLegacyEnvelope() {
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT)).thenReturn("application/json");
    when(graphStore.queryData(any(GraphQuery.class)))
        .thenReturn(new PaginatedResults<>(1, List.of(Map.of("s", "uri-value"))));

    ResponseEntity<?> response = queryService.query(SPARQL_BODY, 5, true);

    assertThat(response.getBody()).isInstanceOf(Results.class);
    Results body = (Results) response.getBody();
    assertThat(body.getTotalCount()).isEqualTo(1);
    assertThat(body.getItems()).hasSize(1);
    verify(graphStore, never()).queryDataAsSparqlResultsJson(any(GraphQuery.class));
  }

  @Test
  void query_acceptHeaderMissing_returnsLegacyEnvelope() {
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT)).thenReturn(null);
    when(graphStore.queryData(any(GraphQuery.class)))
        .thenReturn(new PaginatedResults<>(0, List.of()));

    ResponseEntity<?> response = queryService.query(SPARQL_BODY, 5, true);

    assertThat(response.getBody()).isInstanceOf(Results.class);
    verify(graphStore, never()).queryDataAsSparqlResultsJson(any(GraphQuery.class));
  }

  @Test
  void query_acceptHeaderBlank_returnsLegacyEnvelope() {
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT)).thenReturn("   ");
    when(graphStore.queryData(any(GraphQuery.class)))
        .thenReturn(new PaginatedResults<>(0, List.of()));

    ResponseEntity<?> response = queryService.query(SPARQL_BODY, 5, true);

    assertThat(response.getBody()).isInstanceOf(Results.class);
  }

  @Test
  void query_acceptHeaderMalformed_returnsLegacyEnvelope() {
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT)).thenReturn("totally not a mime list");
    when(graphStore.queryData(any(GraphQuery.class)))
        .thenReturn(new PaginatedResults<>(0, List.of()));

    ResponseEntity<?> response = queryService.query(SPARQL_BODY, 5, true);

    assertThat(response.getBody()).isInstanceOf(Results.class);
  }

  @Test
  void query_w3cRequestedButStoreOptsOut_returns406() {
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT)).thenReturn("application/sparql-results+json");
    when(graphStore.queryDataAsSparqlResultsJson(any(GraphQuery.class))).thenReturn(Optional.empty());

    ResponseEntity<?> response = queryService.query(SPARQL_BODY, 5, true);

    assertThat(response.getStatusCode().value()).isEqualTo(406);
    assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
  }

  @Test
  void query_w3cRequestedAlongsideLegacy_explicitWinsOverGeneric() {
    // application/json alone keeps the legacy envelope (test above); when the W3C type is also
    // listed the explicit mention must win regardless of order, even without q-weights.
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT))
        .thenReturn("application/sparql-results+json, application/json");
    when(graphStore.queryDataAsSparqlResultsJson(any(GraphQuery.class))).thenReturn(Optional.of(W3C_JSON));

    ResponseEntity<?> response = queryService.query(SPARQL_BODY, 5, true);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.parseMediaType("application/sparql-results+json"));
  }

  @Test
  void query_underlyingGraphQueryParameters_passedThroughForBothPaths() {
    when(httpServletRequest.getHeader(HttpHeaders.ACCEPT)).thenReturn("application/json");
    when(graphStore.queryData(any(GraphQuery.class)))
        .thenReturn(new PaginatedResults<>(0, List.of()));

    queryService.query(SPARQL_BODY, 9, true);

    org.mockito.ArgumentCaptor<GraphQuery> captor = org.mockito.ArgumentCaptor.forClass(GraphQuery.class);
    verify(graphStore).queryData(captor.capture());
    GraphQuery sent = captor.getValue();
    assertThat(sent.getQueryLanguage()).isEqualTo(QueryLanguage.SPARQL);
    assertThat(sent.getTimeout()).isEqualTo(9);
    assertThat(sent.isWithTotalCount()).isTrue();
    assertThat(sent.getQuery()).contains(SPARQL_BODY);
  }
}
