package eu.xfsc.fc.core.service.validation.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

/**
 * Parameterised over the content-type / content combinations the on-demand validation
 * router can hand to {@link JsonSchemaValidationStrategy#appliesTo}.
 *
 * <p>Per SRS §3.1.6, a JSON Schema is applicable to a non-RDF JSON asset AND to an
 * RDF asset that is serialized in JSON-LD. Every other RDF serialisation (Turtle,
 * RDF/XML, ...) must remain inapplicable — SHACL is the only validator for those.
 * This class pins both halves of that boundary.</p>
 */
class JsonSchemaValidationStrategyApplicabilityTest {

  // A JSON-LD-serialised RDF asset: parses as RDF (has @context) and is valid JSON.
  private static final String JSON_LD_CONTENT = """
      {"@context":"https://www.w3.org/ns/credentials/v2",\
      "type":["VerifiableCredential"],"issuer":"did:web:example.org"}""";

  // A Turtle-serialised RDF asset — not JSON-LD, must never be routed to JSON Schema.
  private static final String TURTLE_CONTENT =
      "@prefix ex: <https://example.org/> . ex:Alice ex:name \"Alice\" .";

  // An RDF/XML-serialised RDF asset — not JSON-LD, must never be routed to JSON Schema.
  private static final String RDF_XML_CONTENT =
      "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"></rdf:RDF>";

  // Non-JSON-LD-shaped RDF content with no content type at all.
  private static final String OPAQUE_RDF_CONTENT = "dummy rdf";

  // Brace-prefixed but malformed/incomplete content — isJsonLd() is a naive startsWith("{")
  // check, so this is classified applicable even though it is not valid JSON-LD. Documents a
  // known limitation of the heuristic rather than a behavior this test is asserting is correct.
  private static final String MALFORMED_BRACE_PREFIXED_CONTENT = "{this is not valid json-ld at all";

  private final JsonSchemaValidationStrategy strategy =
      new JsonSchemaValidationStrategy(mock(FileStore.class), new ObjectMapper());

  static Stream<Arguments> applicabilityCases() {
    return Stream.of(
        // (label, contentAccessor, contentType, expected)
        Arguments.of(
            "non-RDF asset with application/json content type",
            null, MediaType.APPLICATION_JSON_VALUE, true),
        Arguments.of(
            "non-RDF asset with application/json + charset",
            null, "application/json; charset=utf-8", true),
        Arguments.of(
            "non-RDF asset with JSON Schema media type",
            null, SchemaStore.MEDIA_TYPE_JSON_SCHEMA, true),
        Arguments.of(
            "RDF asset serialised as JSON-LD applies to JSON Schema (SRS 3.1.6)",
            new ContentAccessorDirect(JSON_LD_CONTENT), VerificationConstants.MEDIA_TYPE_LD_JSON, true),
        Arguments.of(
            "RDF asset with brace-prefixed but malformed content applies (naive prefix heuristic, not a JSON-LD validity check)",
            new ContentAccessorDirect(MALFORMED_BRACE_PREFIXED_CONTENT), null, true),
        Arguments.of(
            "RDF asset serialised as Turtle does not apply to JSON Schema",
            new ContentAccessorDirect(TURTLE_CONTENT), VerificationConstants.MEDIA_TYPE_TURTLE, false),
        Arguments.of(
            "RDF asset serialised as RDF/XML does not apply to JSON Schema",
            new ContentAccessorDirect(RDF_XML_CONTENT), VerificationConstants.MEDIA_TYPE_RDF_XML, false),
        Arguments.of(
            "RDF asset with no content type and non-JSON-LD-shaped content does not apply",
            new ContentAccessorDirect(OPAQUE_RDF_CONTENT), null, false),
        Arguments.of(
            "non-RDF asset with XML content type does not apply",
            null, MediaType.APPLICATION_XML_VALUE, false),
        Arguments.of(
            "non-RDF asset with plain text content type does not apply",
            null, MediaType.TEXT_PLAIN_VALUE, false),
        Arguments.of(
            "non-RDF asset with no content type does not apply",
            null, null, false)
    );
  }

  @ParameterizedTest(name = "[{index}] {0} → applies = {3}")
  @MethodSource("applicabilityCases")
  void appliesTo_returnsExpected(
      String description, ContentAccessor content, String contentType, boolean expected) {
    AssetMetadata asset = new AssetMetadata();
    asset.setId("http://example.org/asset/1");
    asset.setContentAccessor(content);
    asset.setContentType(contentType);

    boolean actual = strategy.appliesTo(asset);

    assertEquals(expected, actual, description);
  }
}
