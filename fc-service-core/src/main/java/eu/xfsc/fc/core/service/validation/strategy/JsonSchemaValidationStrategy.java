package eu.xfsc.fc.core.service.validation.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaException;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.service.validation.report.ValidationReportFactory;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

/**
 * {@link ValidationStrategy} implementation for JSON Schema (Draft 2020-12) validation.
 *
 * <p>Applies to non-RDF JSON assets ({@code application/json}, {@code application/schema+json})
 * and to JSON-LD serialized RDF assets. Enforces exactly one asset and one schema per call.</p>
 *
 * <p>Uses the networknt json-schema-validator 2.x API with {@link SchemaRegistry}.
 * External {@code $ref} URIs using {@code file://}, {@code http://}, {@code gopher://}, or
 * {@code ftp://} are rejected to prevent SSRF attacks.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JsonSchemaValidationStrategy implements ValidationStrategy {

  @Qualifier("assetFileStore")
  private final FileStore fileStore;
  private final ObjectMapper objectMapper;

  @Override
  public ValidatorType type() {
    return ValidatorType.JSON_SCHEMA;
  }

  @Override
  public String moduleType() {
    return SchemaModuleType.JSON_SCHEMA;
  }

  /**
   * Returns {@code true} for non-RDF JSON assets only.
   * RDF assets (including JSON-LD) should use SHACL validation.
   */
  @Override
  public boolean appliesTo(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();

    // A non-null ContentAccessor marks an RDF asset (the content is held as a pre-parsed object).
    // RDF assets - including JSON-LD - must be validated via SHACL, not JSON Schema.
    // Non-RDF JSON assets have no ContentAccessor; their type is identified by content-type below.
    if (content != null) {
      return false;
    }
    String ct = asset.getContentType();
    return ct != null
        && (ct.contains(MediaType.APPLICATION_JSON_VALUE)
            || ct.contains(SchemaStore.MEDIA_TYPE_JSON_SCHEMA));
  }

  @Override
  public boolean acceptsSchema(SchemaRecord record) {
    return record.type() == SchemaType.JSON;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Requires exactly one asset and one schema. For non-RDF assets without a content accessor,
   * asset content is loaded from the file store using the asset hash.</p>
   *
   * @throws ClientException if the asset count or schema count is not exactly one,
   *     if JSON content is malformed, or if the schema contains a forbidden {@code $ref}
   */
  @Override
  public ValidationReport validate(List<AssetMetadata> assets, List<ContentAccessor> schemas) {
    if (assets.size() != 1) {
      throw new ClientException(
          "JSON Schema validation requires exactly one asset, but " + assets.size() + " were provided.");
    }
    if (schemas.size() != 1) {
      throw new ClientException(
          "JSON Schema validation requires exactly one schema, but " + schemas.size() + " were provided.");
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
      JsonNode schemaNode = objectMapper.readTree(schemaContent.getContentAsString());
      validateNoExternalRefs(schemaNode);
      JsonNode contentNode = objectMapper.readTree(assetContent.getContentAsStream());
      SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
      Schema schema = registry.getSchema(schemaNode);
      List<Error> errors = schema.validate(contentNode);
      return ValidationReportFactory.fromJsonErrors(errors);
    } catch (IOException e) {
      throw new ClientException("Invalid JSON schema or asset content: " + e.getMessage(), e);
    } catch (SchemaException e) {
      throw new ClientException("Schema could not be loaded: " + e.getMessage(), e);
    }
  }

  /**
   * Rejects schemas containing $ref URIs that enable SSRF — file://, http://, gopher://, ftp://.
   * https:// is permitted as it is standard JSON Schema practice for referencing public schemas.
   */
  private void validateNoExternalRefs(JsonNode node) {
    if (node.isObject()) {
      JsonNode ref = node.get("$ref");
      if (ref != null && ref.isTextual()) {
        String value = ref.asText();
        if (value.startsWith("file://") || value.startsWith("http://")
            || value.startsWith("gopher://") || value.startsWith("ftp://")) {
          throw new ClientException("Schema contains $ref '" + value + "' which is not permitted");
        }
      }
      node.fields().forEachRemaining(entry -> validateNoExternalRefs(entry.getValue()));
    } else if (node.isArray()) {
      node.elements().forEachRemaining(this::validateNoExternalRefs);
    }
  }

}
