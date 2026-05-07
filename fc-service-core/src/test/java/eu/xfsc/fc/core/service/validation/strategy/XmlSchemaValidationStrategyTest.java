package eu.xfsc.fc.core.service.validation.strategy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.filestore.FileStore;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.validation.SchemaFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.xml.sax.SAXException;

class XmlSchemaValidationStrategyTest {

  private static final String VALID_XSD =
      "<?xml version=\"1.0\"?>"
      + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
      + "  <xs:element name=\"root\">"
      + "    <xs:complexType>"
      + "      <xs:sequence>"
      + "        <xs:element name=\"name\" type=\"xs:string\"/>"
      + "      </xs:sequence>"
      + "    </xs:complexType>"
      + "  </xs:element>"
      + "</xs:schema>";

  private static final String CONFORMING_XML = "<root><name>test</name></root>";

  private static final String NON_CONFORMING_XML = "<root><unknown>x</unknown></root>";

  // FileStore is not called in these tests — assets have contentAccessor pre-loaded.
  private final ObjectProvider<DocumentBuilderFactory> documentBuilderFactoryProvider =
      createProviderFor(createSecureDocumentBuilderFactory());

  private final ObjectProvider<SchemaFactory> schemaFactoryProvider =
      createProviderFor(createSecureSchemaFactory());

  private final XmlSchemaValidationStrategy strategy =
      new XmlSchemaValidationStrategy(
          mock(FileStore.class), documentBuilderFactoryProvider, schemaFactoryProvider);

  @Test
  void validate_conformingXml_returnsConforming() {
    ValidationReport report = strategy.validate(
        List.of(buildAsset(CONFORMING_XML)),
        List.of(new ContentAccessorDirect(VALID_XSD)));

    assertTrue(report.getConforms());
    assertNotNull(report.getViolations());
    assertTrue(report.getViolations().isEmpty());
  }

  @Test
  void validate_nonConformingXml_returnsViolation() {
    ValidationReport report = strategy.validate(
        List.of(buildAsset(NON_CONFORMING_XML)),
        List.of(new ContentAccessorDirect(VALID_XSD)));

    assertFalse(report.getConforms());
    assertFalse(report.getViolations().isEmpty());
  }

  @Test
  void validate_malformedSchema_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_XML)),
        List.of(new ContentAccessorDirect("not valid xml schema <<<"))));
  }

  @Test
  void validate_malformedAsset_returnsViolation() {
    ValidationReport report = strategy.validate(
        List.of(buildAsset("not valid xml <<<")),
        List.of(new ContentAccessorDirect(VALID_XSD)));

    assertFalse(report.getConforms());
    assertFalse(report.getViolations().isEmpty());
  }

  @Test
  void validate_schemaWithDoctype_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_XML)),
        List.of(new ContentAccessorDirect(
            "<?xml version=\"1.0\"?><!DOCTYPE xs:schema SYSTEM \"http://evil.com/evil.dtd\">"
            + "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\">"
            + "<xs:element name=\"root\" type=\"xs:string\"/></xs:schema>"))));
  }

  @Test
  void validate_multipleAssets_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_XML), buildAsset(CONFORMING_XML)),
        List.of(new ContentAccessorDirect(VALID_XSD))));
  }

  @Test
  void validate_multipleSchemas_throwsClientException() {
    assertThrows(ClientException.class, () -> strategy.validate(
        List.of(buildAsset(CONFORMING_XML)),
        List.of(new ContentAccessorDirect(VALID_XSD),
            new ContentAccessorDirect(VALID_XSD))));
  }


  private static AssetMetadata buildAsset(String content) {
    AssetMetadata asset = new AssetMetadata();
    asset.setId("http://example.org/asset/1");
    asset.setContentAccessor(new ContentAccessorDirect(content));
    return asset;
  }

  private static DocumentBuilderFactory createSecureDocumentBuilderFactory() {
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
      dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      dbf.setExpandEntityReferences(false);
      return dbf;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create secure DocumentBuilderFactory", e);
    }
  }

  private static SchemaFactory createSecureSchemaFactory() {
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      return factory;
    } catch (SAXException e) {
      throw new IllegalStateException("Failed to create secure SchemaFactory", e);
    }
  }

  private static <T> ObjectProvider<T> createProviderFor(T value) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = (ObjectProvider<T>) mock(ObjectProvider.class);
    when(provider.getObject()).thenReturn(value);
    return provider;
  }
}
