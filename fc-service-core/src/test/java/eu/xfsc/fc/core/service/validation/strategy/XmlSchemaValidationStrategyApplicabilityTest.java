package eu.xfsc.fc.core.service.validation.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.stream.Stream;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.SchemaFactory;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;

import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.verification.VerificationConstants;

/**
 * Parameterised over the content-type / content combinations the on-demand validation
 * router can hand to {@link XmlSchemaValidationStrategy#appliesTo}.
 *
 * <p>Per SRS §3.1.6, an XML Schema is applicable to a non-RDF XML asset AND to an
 * RDF asset that is serialized in RDF/XML — symmetric with the JSON Schema / JSON-LD rule.
 * Every other RDF serialisation (Turtle, JSON-LD, ...) must remain inapplicable — SHACL is
 * the only validator for those. This class pins both halves of that boundary.</p>
 */
class XmlSchemaValidationStrategyApplicabilityTest {

  // An RDF/XML-serialised RDF asset: parses as RDF and is valid RDF/XML.
  private static final String RDF_XML_CONTENT =
      "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"></rdf:RDF>";

  // A Turtle-serialised RDF asset — not RDF/XML, must never be routed to XML Schema.
  private static final String TURTLE_CONTENT =
      "@prefix ex: <https://example.org/> . ex:Alice ex:name \"Alice\" .";

  // A JSON-LD-serialised RDF asset — not RDF/XML, must never be routed to XML Schema.
  private static final String JSON_LD_CONTENT =
      "{\"@context\":\"https://www.w3.org/ns/credentials/v2\","
      + "\"type\":[\"VerifiableCredential\"],\"issuer\":\"did:web:example.org\"}";

  // Non-RDF/XML-shaped RDF content with no content type at all.
  private static final String OPAQUE_RDF_CONTENT = "dummy rdf";

  // Mockito's mock(Class) returns the raw type, so casting it back to the parameterised
  // ObjectProvider<T> is a checked-cast warning that cannot be avoided without changing
  // the constructor signature. The mocks are never invoked in these applicability tests.
  @SuppressWarnings("unchecked")
  private final XmlSchemaValidationStrategy strategy =
      new XmlSchemaValidationStrategy(
          mock(FileStore.class),
          (ObjectProvider<DocumentBuilderFactory>) mock(ObjectProvider.class),
          (ObjectProvider<SchemaFactory>) mock(ObjectProvider.class));

  static Stream<Arguments> applicabilityCases() {
    ContentAccessor opaqueRdf = new ContentAccessorDirect(OPAQUE_RDF_CONTENT);
    return Stream.of(
        // (label, contentAccessor, contentType, expected)
        Arguments.of(
            "non-RDF asset with application/xml content type",
            null, MediaType.APPLICATION_XML_VALUE, true),
        Arguments.of(
            "non-RDF asset with application/xml + charset",
            null, "application/xml; charset=utf-8", true),
        Arguments.of(
            "non-RDF asset with text/xml content type",
            null, MediaType.TEXT_XML_VALUE, true),
        Arguments.of(
            "RDF asset with application/xml content type but non-RDF/XML-shaped content does not apply",
            opaqueRdf, MediaType.APPLICATION_XML_VALUE, false),
        Arguments.of(
            "RDF asset serialised as RDF/XML applies to XML Schema (SRS 3.1.6)",
            new ContentAccessorDirect(RDF_XML_CONTENT), VerificationConstants.MEDIA_TYPE_RDF_XML, true),
        Arguments.of(
            "RDF asset serialised as Turtle does not apply to XML Schema",
            new ContentAccessorDirect(TURTLE_CONTENT), VerificationConstants.MEDIA_TYPE_TURTLE, false),
        Arguments.of(
            "RDF asset serialised as JSON-LD does not apply to XML Schema",
            new ContentAccessorDirect(JSON_LD_CONTENT), VerificationConstants.MEDIA_TYPE_LD_JSON, false),
        Arguments.of(
            "RDF asset with no content type still routes to SHACL",
            opaqueRdf, null, false),
        Arguments.of(
            "non-RDF asset with JSON content type does not apply",
            null, MediaType.APPLICATION_JSON_VALUE, false),
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
