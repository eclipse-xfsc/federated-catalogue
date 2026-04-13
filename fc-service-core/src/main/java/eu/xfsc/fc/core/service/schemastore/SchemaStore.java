package eu.xfsc.fc.core.service.schemastore;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import lombok.Getter;
import org.springframework.http.MediaType;

public interface SchemaStore {

  String MEDIA_TYPE_TEXT_TURTLE = "text/turtle";
  String MEDIA_TYPE_JSON_SCHEMA = "application/schema+json";
  String MEDIA_TYPE_RDF_XML = "application/rdf+xml";
  String MEDIA_TYPE_LD_JSON = "application/ld+json";

  /**
   * The different types of schema.
   *
   */
  @Getter
  enum SchemaType {
    ONTOLOGY(MEDIA_TYPE_TEXT_TURTLE, MEDIA_TYPE_RDF_XML, MEDIA_TYPE_LD_JSON),
    SHAPE(MEDIA_TYPE_TEXT_TURTLE, MEDIA_TYPE_RDF_XML, MEDIA_TYPE_LD_JSON),
    VOCABULARY(MEDIA_TYPE_TEXT_TURTLE, MEDIA_TYPE_RDF_XML, MEDIA_TYPE_LD_JSON),
    JSON(MediaType.APPLICATION_JSON_VALUE, MEDIA_TYPE_JSON_SCHEMA),
    XML(MediaType.APPLICATION_XML_VALUE);

    private final List<String> compatibleAssetContentTypes;

    SchemaType(String... contentTypes) {
      this.compatibleAssetContentTypes = List.of(contentTypes);
    }

    /**
     * Resolves a SchemaType from an HTTP Content-Type header value.
     * Returns empty for RDF content types (handled by the existing RDF analysis path).
     */
    public static Optional<SchemaType> fromContentType(String contentType) {
      if (contentType == null) {
        return Optional.empty();
      }
      if (contentType.contains(MEDIA_TYPE_JSON_SCHEMA)) {
        return Optional.of(JSON);
      }
      if (contentType.contains(MediaType.APPLICATION_XML_VALUE)) {
        return Optional.of(XML);
      }
      return Optional.empty();
    }

    private static final Set<String> RDF_CONTENT_TYPES = Set.of(
            MEDIA_TYPE_TEXT_TURTLE, MEDIA_TYPE_RDF_XML, MEDIA_TYPE_LD_JSON);

    private static final Set<String> NON_RDF_CONTENT_TYPES = Set.of(
            MEDIA_TYPE_JSON_SCHEMA, MediaType.APPLICATION_XML_VALUE);

    public static boolean isRdfContentType(String contentType) {
      if (contentType == null) {
        return false;
      }
      return RDF_CONTENT_TYPES.stream().anyMatch(contentType::contains);
    }

    public static String getSupportedContentTypes() {
      return "%s, %s".formatted(String.join(", ", RDF_CONTENT_TYPES), String.join(", ", NON_RDF_CONTENT_TYPES));
    }

  }

  /**
   * Initialise the default Gaia-X schemas, if the schema store is still empty.
   * If there are already schemas in the store, calling this method will do
   * nothing.
   * @return number of schemas added to Schema DB.
   */
  public int initializeDefaultSchemas();

  /**
   * Verify if a given schema is syntactically correct.
   *
   * @param schema The schema data to verify. The content can be shacl (ttl),
   * vocabulary (SKOS) or ontology (owl).
   * @return TRUE if the schema is syntactically valid.
   */
  boolean verifySchema(ContentAccessor schema);

  /**
   * Store a schema after has been successfully verified for its type and
   * syntax.
   *
   * @param schema The schema content to be stored.
   * @return The result containing the internal identifier and any warnings.
   */
  SchemaStoreResult addSchema(ContentAccessor schema);

  /**
   * Store a non-RDF schema with a known type.
   *
   * @param schema The schema content to be stored.
   * @param type The schema type (JSON or XML).
   * @return The result containing the internal identifier and any warnings.
   */
  SchemaStoreResult addSchema(ContentAccessor schema, SchemaType type);

  /**
   * Update the schema with the given identifier.
   *
   * @param identifier The identifier of the schema to update.
   * @param schema The content to replace the schema with.
   * @return The result containing the identifier and any warnings.
   */
  SchemaStoreResult updateSchema(String identifier, ContentAccessor schema);

  /**
   * Delete the schema with the given identifier.
   *
   * @param identifier The identifier of the schema to delete.
   */
  void deleteSchema(String identifier);

  /**
   * Get the identifiers of all schemas, sorted by schema type.
   *
   * @return the identifiers of all schemas, sorted by schema type.
   */
  Map<SchemaType, List<String>> getSchemaList();

  /**
   * Get the content of the schema with the given identifier.
   *
   * @param identifier The identifier of the schema to return.
   * @return The schema content.
   */
  ContentAccessor getSchema(String identifier);

  /**
   * Get the full schema record for the given identifier.
   *
   * @param identifier The identifier of the schema.
   * @return The schema record including type information.
   */
  SchemaRecord getSchemaRecord(String identifier);

  /**
   * Get the schemas that defines the given term, grouped by schema type.
   *
   * @param termURI The term to get the defining schemas for.
   * @return the identifiers of the defining schemas, sorted by schema type.
   */
  Map<SchemaType, List<String>> getSchemasForTerm(String termURI);

  /**
   * Get the union schema.
   *
   * @param schemaType The schema type, for which the composite schema should be
   * returned.
   * @return The union RDF graph.
   */
  ContentAccessor getCompositeSchema(SchemaType schemaType);

  /**
   * Get the latest schema for a non-RDF type.
   *
   * @param schemaType The schema type (JSON or XML).
   * @return The content of the latest schema of the given type.
   */
  ContentAccessor getLatestSchemaByType(SchemaType schemaType);

  /**
   * Get a specific version of the schema.
   *
   * @param identifier The identifier of the schema.
   * @param version    The 1-based version ordinal.
   * @return The schema record at that version.
   * @throws eu.xfsc.fc.core.exception.NotFoundException if schema or version not found
   */
  SchemaRecord getSchemaVersion(String identifier, int version);

  /**
   * Get all versions of the schema, ordered ascending by version number.
   *
   * @param identifier The identifier of the schema.
   * @return List of schema records with version metadata.
   * @throws eu.xfsc.fc.core.exception.NotFoundException if schema not found
   */
  List<SchemaRecord> getSchemaVersions(String identifier);

  /**
   * Remove all Schemas from the SchemaStore.
   */
  void clear();

}
