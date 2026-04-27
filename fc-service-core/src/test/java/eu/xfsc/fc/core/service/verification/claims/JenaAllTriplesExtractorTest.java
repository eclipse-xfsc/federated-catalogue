package eu.xfsc.fc.core.service.verification.claims;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.RdfClaim;

import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Unit tests for {@link JenaAllTriplesExtractor}.
 */
class JenaAllTriplesExtractorTest {

  private JenaAllTriplesExtractor extractor;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    extractor = new JenaAllTriplesExtractor(objectMapper);
  }

  // --- nodeToString: output must match RdfNodeMapper.rdf2String ---

  @Test
  void nodeToString_iriNode_matchesRdfClaimFormat() {
    Model model = ModelFactory.createDefaultModel();
    Resource s = model.createResource("http://example.org/s");
    Property p = model.createProperty("http://example.org/p");
    Resource o = model.createResource("http://example.org/o");
    Statement stmt = model.createStatement(s, p, o);
    RdfClaim claim = new RdfClaim(stmt, objectMapper);

    assertEquals(claim.getSubjectString(), extractor.nodeToString(stmt.getSubject()));
    assertEquals(claim.getPredicateString(), extractor.nodeToString(stmt.getPredicate()));
    assertEquals(claim.getObjectString(), extractor.nodeToString(stmt.getObject()));
  }

  @Test
  void nodeToString_literalNode_matchesRdfClaimFormat() {
    Model model = ModelFactory.createDefaultModel();
    Resource s = model.createResource("http://example.org/s");
    Property p = model.createProperty("http://example.org/p");
    RDFNode literal = model.createLiteral("hello world");
    Statement stmt = model.createStatement(s, p, literal);
    RdfClaim claim = new RdfClaim(stmt, objectMapper);

    assertEquals(claim.getObjectString(), extractor.nodeToString(stmt.getObject()));
    assertEquals("\"hello world\"", extractor.nodeToString(stmt.getObject()));
  }

  @Test
  void nodeToString_blankNode_matchesRdfClaimFormat() {
    Model model = ModelFactory.createDefaultModel();
    Resource s = model.createResource("http://example.org/s");
    Property p = model.createProperty("http://example.org/p");
    Resource blank = model.createResource();
    Statement stmt = model.createStatement(s, p, blank);
    RdfClaim claim = new RdfClaim(stmt, objectMapper);

    String result = extractor.nodeToString(stmt.getObject());
    assertEquals(claim.getObjectString(), result);
    assertFalse(result.startsWith("<"), "Blank node must not be formatted as IRI");
    assertFalse(result.startsWith("\""), "Blank node must not be formatted as literal");
  }

  // --- detectLang ---

  @ParameterizedTest(name = "{index} => contentType: {0}, expected: {1}")
  @MethodSource("detectLangTestCases")
  void detectLang_returnsCorrectLanguage(String contentType, Lang expected) {
    assertEquals(expected, JenaAllTriplesExtractor.detectLang(contentType));
  }

  private static Stream<Arguments> detectLangTestCases() {
    return Stream.of(
        Arguments.of(null, Lang.JSONLD),
        Arguments.of("text/turtle", Lang.TURTLE),
        Arguments.of("application/n-triples", Lang.NTRIPLES),
        Arguments.of("application/rdf+xml", Lang.RDFXML),
        Arguments.of("application/ld+json", Lang.JSONLD),
        Arguments.of("application/json", Lang.JSONLD),
        Arguments.of("unknown/type", Lang.JSONLD)
    );
  }

  // --- extractClaims: parse formats ---

  @Test
  void extractClaims_inlineJsonLd_returnsTriples() {
    String jsonLd = """
        {
          "@context": {"ex": "http://example.org/"},
          "@id": "http://example.org/item1",
          "ex:name": "Test"
        }
        """;

    List<RdfClaim> claims = extractor.extractClaims(new ContentAccessorDirect(jsonLd));

    assertNotNull(claims);
    assertFalse(claims.isEmpty());
    assertEquals(1, claims.size());
    assertEquals("<http://example.org/item1>", claims.getFirst().getSubjectString());
  }

  @Test
  void extractClaims_turtle_returnsTriples() {
    String turtle = """
        @prefix ex: <http://example.org/> .
        <http://example.org/item1> ex:name "Test" .
        """;

    List<RdfClaim> claims = extractor.extractClaims(
        new ContentAccessorDirect(turtle, "text/turtle"));

    assertNotNull(claims);
    assertEquals(1, claims.size());
    assertEquals("<http://example.org/item1>", claims.getFirst().getSubjectString());
  }
}
