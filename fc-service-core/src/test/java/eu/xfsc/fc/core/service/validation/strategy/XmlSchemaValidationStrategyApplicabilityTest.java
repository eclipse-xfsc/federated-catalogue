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

/**
 * Parameterised over the content-type / content combinations the on-demand validation
 * router can hand to {@link XmlSchemaValidationStrategy#appliesTo}. The strategy
 * intentionally only accepts non-RDF XML: RDF assets — including RDF/XML-serialised
 * credentials — must be routed through SHACL. That routing decision lives in the
 * applicability check; this class pins it.
 */
class XmlSchemaValidationStrategyApplicabilityTest {

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
    ContentAccessor rdfClaim = new ContentAccessorDirect("dummy rdf");
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
            "RDF asset with application/xml content type",
            rdfClaim, MediaType.APPLICATION_XML_VALUE, false),
        Arguments.of(
            "RDF asset (RDF/XML) routes to SHACL, not XML Schema",
            rdfClaim, "application/rdf+xml", false),
        Arguments.of(
            "RDF asset with no content type still routes to SHACL",
            rdfClaim, null, false),
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
