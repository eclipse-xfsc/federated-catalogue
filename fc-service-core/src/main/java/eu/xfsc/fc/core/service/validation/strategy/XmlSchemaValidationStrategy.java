package eu.xfsc.fc.core.service.validation.strategy;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.validation.report.ValidationReportFactory;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * {@link ValidationStrategy} implementation for XML Schema (XSD 1.0) validation.
 *
 * <p>Applies to non-RDF XML assets ({@code application/xml}, {@code text/xml}) and to
 * RDF/XML serialized RDF assets. Enforces exactly one asset and one schema per call.</p>
 *
 * <p>Uses {@code javax.xml.validation}. The {@link SchemaFactory} and
 * {@link Validator} are hardened against XXE attacks by disabling external DTD and
 * schema access ({@link XMLConstants#ACCESS_EXTERNAL_DTD},
 * {@link XMLConstants#ACCESS_EXTERNAL_SCHEMA}).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class XmlSchemaValidationStrategy implements ValidationStrategy {

  @Qualifier("assetFileStore")
  private final FileStore fileStore;
  private final ObjectProvider<DocumentBuilderFactory> secureDocumentBuilderFactoryProvider;
  private final ObjectProvider<SchemaFactory> secureXmlSchemaFactoryProvider;

  @Override
  public ValidatorType type() {
    return ValidatorType.XML_SCHEMA;
  }

  @Override
  public String moduleType() {
    return SchemaModuleType.XML_SCHEMA;
  }

  /**
   * Returns {@code true} for non-RDF XML assets only.
   * RDF assets (including RDF/XML) should use SHACL validation.
   */
  @Override
  public boolean appliesTo(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();

    // A non-null ContentAccessor marks an RDF asset (the content is held as a pre-parsed object).
    // RDF assets - including RDF/XML - must be validated via SHACL, not XML Schema.
    // Non-RDF XML assets have no ContentAccessor; their type is identified by content-type below.
    if (content != null) {
      return false;
    }
    String ct = asset.getContentType();
    return ct != null
        && (ct.contains(MediaType.APPLICATION_XML_VALUE)
        || ct.contains(MediaType.TEXT_XML_VALUE));
  }

  @Override
  public boolean acceptsSchema(SchemaRecord record) {
    return record.type() == SchemaType.XML;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Requires exactly one asset and one schema. For non-RDF assets without a content accessor,
   * asset content is loaded from the file store using the asset hash.</p>
   *
   * @throws ClientException if the asset count or schema count is not exactly one,
   *     if XML content is malformed, or if the schema is invalid
   */
  @Override
  public ValidationReport validate(List<AssetMetadata> assets, List<ContentAccessor> schemas) {
    if (assets.size() != 1) {
      throw new ClientException(
          "XML Schema validation requires exactly one asset, but " + assets.size() + " were provided.");
    }
    if (schemas.size() != 1) {
      throw new ClientException(
          "XML Schema validation requires exactly one schema, but " + schemas.size() + " were provided.");
    }
    ContentAccessor assetContent = resolveContent(assets.get(0));
    return validateContent(assetContent, schemas.get(0));
  }

  private ContentAccessor resolveContent(AssetMetadata asset) {
    if (asset.getContentAccessor() != null) {
      return asset.getContentAccessor();
    }
    try {
      return fileStore.readFile(asset.getAssetHash());
    } catch (IOException e) {
      throw new ClientException(
          "Cannot load asset content for " + asset.getId() + ": " + e.getMessage(), e);
    }
  }

  private ValidationReport validateContent(ContentAccessor assetContent, ContentAccessor schemaContent) {
    try {
      Schema xsdSchema = loadXsdSchema(schemaContent);
      // A new Validator instance per call: Validator is not thread-safe
      Validator validator = xsdSchema.newValidator();
      // XXE prevention on the content validator
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

      try (InputStream contentStream = assetContent.getContentAsStream()) {
        validator.validate(new StreamSource(contentStream));
        return ValidationReportFactory.conforming();
      } catch (SAXException e) {
        return ValidationReportFactory.fromSaxException(e);
      }
    } catch (ParserConfigurationException e) {
      throw new ServerException("XML parser configuration error: " + e.getMessage(), e);
    } catch (SAXException | IOException e) {
      throw new ClientException("Invalid XML schema or asset content: " + e.getMessage(), e);
    }
  }

  private Schema loadXsdSchema(ContentAccessor schemaContent)
      throws ParserConfigurationException, SAXException, IOException {
    DocumentBuilderFactory dbf = secureDocumentBuilderFactoryProvider.getObject();
    Document schemaDoc;
    try (InputStream schemaStream = schemaContent.getContentAsStream()) {
      schemaDoc = dbf.newDocumentBuilder().parse(schemaStream);
    }
    SchemaFactory factory = secureXmlSchemaFactoryProvider.getObject();
    return factory.newSchema(new DOMSource(schemaDoc));
  }
}
