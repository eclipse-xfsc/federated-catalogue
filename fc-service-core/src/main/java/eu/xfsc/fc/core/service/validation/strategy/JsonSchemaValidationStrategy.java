package eu.xfsc.fc.core.service.validation.strategy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.AbsoluteIri;
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
import eu.xfsc.fc.core.service.validation.rdf.RdfAssetParser;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import java.io.IOException;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;
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
 * <p>Uses the networknt json-schema-validator 2.x API with {@link SchemaRegistry}. An uploaded
 * schema is untrusted input, so no {@code $ref}, {@code $dynamicRef}, or {@code $schema} in it may
 * ever cause this service to fetch a resource outside the schema document — that would be a
 * server-side request forgery (SSRF) vector. Two independent layers enforce this:</p>
 * <ul>
 *   <li>The {@link SchemaRegistry} is built with a {@code schemaLoader} whose resolver
 *   unconditionally blocks every out-of-document resource IRI (see {@link #buildRegistry()}), so
 *   even a reference that bypasses the pre-check below (e.g. a relative {@code $ref} combined with
 *   an attacker-supplied {@code $id} base that resolves to an external IRI) fails without any
 *   network, file, or classpath access. This is the actual security boundary.</li>
 *   <li>{@link #validateNoExternalRefs(JsonNode)} pre-checks {@code $ref} and {@code $dynamicRef}
 *   values for a fast, precise client error before the schema is ever loaded. It rejects any value
 *   that carries a URI scheme (case-insensitively — {@code HTTP://}, {@code https://}, etc. are all
 *   rejected, not just a fixed lowercase list) or is protocol-relative ({@code //host/path}, which
 *   carries no scheme but still resolves to an external fetch), and permits only fragment-only
 *   ({@code "#/..."}) or relative references resolved within the document.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JsonSchemaValidationStrategy implements ValidationStrategy {

  private static final String REF_KEYWORD = "$ref";
  private static final String DYNAMIC_REF_KEYWORD = "$dynamicRef";

  // RFC 3986 scheme syntax: ALPHA *( ALPHA / DIGIT / "+" / "-" / "." ) ":" — matching this means
  // the value is an absolute IRI, not a same-document fragment or relative reference. The
  // character class already spans both cases, so this rejects "HTTP://" exactly like "http://".
  private static final Pattern HAS_URI_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:.*");

  // A protocol-relative reference ("//host/path", RFC 3986 §4.2) carries no URI scheme, so
  // HAS_URI_SCHEME does not match it, yet it still resolves against whatever scheme the schema
  // loader is running under — an external network fetch. The registry's block-all schemaLoader
  // (see buildRegistry()) already stops this from being followed; this constant only makes the
  // pre-check reject it too, for a precise client error instead of falling through to the
  // registry's opaque block.
  private static final String PROTOCOL_RELATIVE_PREFIX = "//";

  // Blocks every schema resource IRI the registry's loader is asked to resolve outside the
  // in-memory document (see the class Javadoc). This is the actual SSRF boundary; the predicate
  // never inspects the IRI because there is no IRI a validator-supplied schema is allowed to fetch.
  private static final Predicate<AbsoluteIri> BLOCK_ALL_EXTERNAL_SCHEMA_RESOURCES = iri -> true;

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
   * Returns {@code true} for non-RDF JSON assets, and for RDF assets serialised as JSON-LD
   * (SRS 3.1.6). Other RDF serialisations (Turtle, RDF/XML, ...) remain SHACL-only.
   */
  @Override
  public boolean appliesTo(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();

    // A non-null ContentAccessor marks an RDF asset (the content is held as a pre-parsed object).
    // Per SRS 3.1.6, a JSON Schema is applicable to an RDF asset serialised in JSON-LD;
    // every other RDF serialisation stays SHACL-only.
    // Non-RDF JSON assets have no ContentAccessor; their type is identified by content-type below.
    if (content != null) {
      return RdfAssetParser.isJsonLd(asset);
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
      Schema schema = buildRegistry().getSchema(schemaNode);
      List<Error> errors = schema.validate(contentNode);
      return ValidationReportFactory.fromJsonErrors(errors);
    } catch (IOException e) {
      throw new ClientException("Invalid JSON schema or asset content: " + e.getMessage(), e);
    } catch (SchemaException e) {
      throw new ClientException("Schema could not be loaded: " + e.getMessage(), e);
    }
  }

  /**
   * Builds a registry whose schema loader is configured to never resolve a resource outside the
   * in-memory schema document — no network fetch, no filesystem read, no classpath lookup of an
   * IRI taken from an uploaded (untrusted) schema. This is the actual defence against SSRF via
   * {@code $ref}, {@code $dynamicRef}, or a non-well-known {@code $schema}; see the class Javadoc.
   */
  private SchemaRegistry buildRegistry() {
    return SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12,
        builder -> builder.schemaLoader(loader -> loader.block(BLOCK_ALL_EXTERNAL_SCHEMA_RESOURCES)));
  }

  /**
   * Pre-checks {@code $ref} and {@code $dynamicRef} values for a fast, precise client error.
   * Rejects any value that carries a URI scheme (an absolute IRI, e.g. {@code https://...},
   * {@code HTTP://...}, {@code file://...} — case does not affect matching) or is a
   * protocol-relative reference ({@code //host/path}, which carries no scheme but still resolves
   * to an external fetch); permits fragment-only ({@code "#/..."}) and relative references, which
   * resolve within the document. This check is
   * defence in depth: the actual SSRF boundary is {@link #buildRegistry()}, which also blocks
   * references this check cannot see (for instance a relative {@code $ref} that only becomes an
   * external IRI once resolved against an attacker-supplied {@code $id} base elsewhere in the
   * document).
   */
  private void validateNoExternalRefs(JsonNode node) {
    if (node.isObject()) {
      rejectIfAbsolute(node, REF_KEYWORD);
      rejectIfAbsolute(node, DYNAMIC_REF_KEYWORD);
      node.fields().forEachRemaining(entry -> validateNoExternalRefs(entry.getValue()));
    } else if (node.isArray()) {
      node.elements().forEachRemaining(this::validateNoExternalRefs);
    }
  }

  private void rejectIfAbsolute(JsonNode node, String keyword) {
    JsonNode ref = node.get(keyword);
    if (ref == null || !ref.isTextual()) {
      return;
    }
    String value = ref.asText();
    if (!value.startsWith("#")
        && (value.startsWith(PROTOCOL_RELATIVE_PREFIX) || HAS_URI_SCHEME.matcher(value).matches())) {
      throw new ClientException(
          "Schema contains " + keyword + " '" + value + "' which resolves outside the schema "
              + "document; only fragment references (\"#/...\") and relative references within "
              + "the document are permitted.");
    }
  }

}
