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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;

/**
 * Parameterised over the content-type / content combinations the on-demand validation
 * router can hand to {@link JsonSchemaValidationStrategy#appliesTo}. The strategy
 * intentionally only accepts non-RDF JSON: RDF assets — including JSON-LD-serialised
 * credentials — must be routed through SHACL. That routing decision lives in the
 * applicability check; this class pins it.
 */
class JsonSchemaValidationStrategyApplicabilityTest {

  private final JsonSchemaValidationStrategy strategy =
      new JsonSchemaValidationStrategy(mock(FileStore.class), new ObjectMapper());

  static Stream<Arguments> applicabilityCases() {
    ContentAccessor rdfClaim = new ContentAccessorDirect("dummy rdf");
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
            "RDF asset with application/json content type",
            rdfClaim, MediaType.APPLICATION_JSON_VALUE, false),
        Arguments.of(
            "RDF asset (JSON-LD) routes to SHACL, not JSON Schema",
            rdfClaim, "application/ld+json", false),
        Arguments.of(
            "RDF asset with no content type still routes to SHACL",
            rdfClaim, null, false),
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
