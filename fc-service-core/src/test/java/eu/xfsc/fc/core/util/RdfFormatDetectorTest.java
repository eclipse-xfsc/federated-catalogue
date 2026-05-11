package eu.xfsc.fc.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import eu.xfsc.fc.core.exception.ClientException;
import org.apache.jena.riot.Lang;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

/**
 * Unit tests for {@link RdfFormatDetector}.
 */
class RdfFormatDetectorTest {

  @ParameterizedTest(name = "{index} => contentType: {0}, rawContent: {1}, expected: {2}")
  @MethodSource("detectRdfLangTestCases")
  void detectRdfLang_returnsCorrectLanguage(String contentType, String rawContent, Lang expected) {
    assertEquals(expected, RdfFormatDetector.detect(contentType, rawContent));
  }

  private static Stream<Arguments> detectRdfLangTestCases() {
    return Stream.of(
        // content-type detection
        Arguments.of("text/turtle", null, Lang.TURTLE),
        Arguments.of("application/n-triples", null, Lang.NT),
        Arguments.of("application/rdf+xml", null, Lang.RDFXML),
        Arguments.of("application/ld+json", null, Lang.JSONLD11),
        Arguments.of("application/json", null, Lang.JSONLD11),
        Arguments.of("application/vc+ld+json", null, Lang.JSONLD11),
        // content-type with charset param
        Arguments.of("application/ld+json; charset=UTF-8", null, Lang.JSONLD11),
        // content inspection fallback
        Arguments.of(null, "{\"@context\": {}}", Lang.JSONLD11),
        Arguments.of(null, "@prefix ex: <http://example.org/> .", Lang.TURTLE),
        Arguments.of(null, "@base <http://example.org/> .", Lang.TURTLE),
        Arguments.of(null, "<?xml version=\"1.0\"?>", Lang.RDFXML),
        Arguments.of(null, "<rdf:RDF", Lang.RDFXML),
        // N-Triples heuristics (IRI subject, blank-node subject, comment line)
        Arguments.of(null, "<http://example.org/s> <http://example.org/p> <http://example.org/o> .", Lang.NT),
        Arguments.of(null, "_:b0 <http://example.org/p> \"value\" .", Lang.NT),
        Arguments.of(null, "# NT comment header", Lang.NT),
        Arguments.of("application/n-triples", null, Lang.NT)
    );
  }

  @Test
  void detect_unknownContentTypeAndContent_throwsClientException() {
    assertThrows(ClientException.class, () -> RdfFormatDetector.detect("unknown/type", null));
  }

  @Test
  void detect_nullContentTypeAndNullContent_throwsClientException() {
    assertThrows(ClientException.class, () -> RdfFormatDetector.detect(null, null));
  }

  @Test
  void detect_unknownContentTypeAndUnrecognisedContent_throwsClientException() {
    assertThrows(ClientException.class, () -> RdfFormatDetector.detect(null, "TOTALLY UNKNOWN FORMAT"));
  }
}
