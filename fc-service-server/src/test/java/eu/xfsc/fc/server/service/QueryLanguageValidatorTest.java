package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.GraphStoreDisabledException;
import eu.xfsc.fc.core.exception.UnsupportedQueryLanguageException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.graphdb.GraphStore;

/**
 * Unit tests for {@link QueryLanguageValidator} with mocked {@link GraphStore}.
 */
@ExtendWith(MockitoExtension.class)
class QueryLanguageValidatorTest {

  @Mock
  private GraphStore graphStore;

  @InjectMocks
  private QueryLanguageValidator validator;

  @Test
  void validateLanguageSupport_correctLanguage_doesNotThrow() {
    when(graphStore.getSupportedQueryLanguage()).thenReturn(Optional.of(QueryLanguage.OPENCYPHER));

    assertDoesNotThrow(() -> validator.validateLanguageSupport(QueryLanguage.OPENCYPHER));
  }

  @Test
  void validateLanguageSupport_wrongLanguage_throwsUnsupportedQueryLanguageException() {
    when(graphStore.getSupportedQueryLanguage()).thenReturn(Optional.of(QueryLanguage.OPENCYPHER));
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.NEO4J);

    UnsupportedQueryLanguageException ex = assertThrows(
        UnsupportedQueryLanguageException.class,
        () -> validator.validateLanguageSupport(QueryLanguage.SPARQL));

    assertEquals("NEO4J", ex.getActiveBackend());
    assertEquals("OPENCYPHER", ex.getSupportedLanguage());
    assertEquals("SPARQL", ex.getRequestedLanguage());
    assertEquals("application/opencypher-query", ex.getExpectedContentType());
    assertTrue(ex.getHint().contains("openCypher"));
    assertTrue(ex.getMessage().contains("SPARQL"));
    assertTrue(ex.getMessage().contains("NEO4J"));
  }

  @Test
  void validateLanguageSupport_disabledGraphStore_throwsGraphStoreDisabledException() {
    when(graphStore.getSupportedQueryLanguage()).thenReturn(Optional.empty());

    GraphStoreDisabledException ex = assertThrows(
        GraphStoreDisabledException.class,
        () -> validator.validateLanguageSupport(QueryLanguage.OPENCYPHER));

    assertTrue(ex.getMessage().contains("disabled"));
  }

  @Test
  void validateLanguageSupport_sparqlOnFuseki_doesNotThrow() {
    when(graphStore.getSupportedQueryLanguage()).thenReturn(Optional.of(QueryLanguage.SPARQL));

    assertDoesNotThrow(() -> validator.validateLanguageSupport(QueryLanguage.SPARQL));
  }

  @Test
  void validateLanguageSupport_openCypherOnFuseki_throwsException() {
    when(graphStore.getSupportedQueryLanguage()).thenReturn(Optional.of(QueryLanguage.SPARQL));
    when(graphStore.getBackendType()).thenReturn(GraphBackendType.FUSEKI);

    UnsupportedQueryLanguageException ex = assertThrows(
        UnsupportedQueryLanguageException.class,
        () -> validator.validateLanguageSupport(QueryLanguage.OPENCYPHER));

    assertEquals("FUSEKI", ex.getActiveBackend());
    assertEquals("SPARQL", ex.getSupportedLanguage());
    assertEquals("OPENCYPHER", ex.getRequestedLanguage());
    assertEquals("application/sparql-query", ex.getExpectedContentType());
    assertTrue(ex.getHint().contains("SPARQL"));
  }

  @Test
  void queryLanguageProperties_unmappedLanguage_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class,
        () -> QueryLanguageProperties.of(QueryLanguage.GRAPHQL));
  }

  @Test
  void fromContentType_openCypherContentType_returnsOpenCypher() {
    assertEquals(QueryLanguage.OPENCYPHER,
        QueryLanguageProperties.fromContentType("application/opencypher-query"));
  }

  @Test
  void fromContentType_sparqlContentType_returnsSparql() {
    assertEquals(QueryLanguage.SPARQL,
        QueryLanguageProperties.fromContentType("application/sparql-query"));
  }

  @Test
  void fromContentType_withCharsetParameter_returnsCorrectLanguage() {
    assertEquals(QueryLanguage.SPARQL,
        QueryLanguageProperties.fromContentType("application/sparql-query; charset=utf-8"));
  }

  @Test
  void fromContentType_unknownContentType_throwsIllegalArgumentException() {
    assertThrows(IllegalArgumentException.class,
        () -> QueryLanguageProperties.fromContentType("application/json"));
  }
}