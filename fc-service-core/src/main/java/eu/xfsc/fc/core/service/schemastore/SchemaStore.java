package eu.xfsc.fc.core.service.schemastore;

import java.util.List;
import java.util.Map;

import eu.xfsc.fc.core.pojo.ContentAccessor;

public interface SchemaStore {

  /**
   * The different types of schema.
   *
   * TODO: Remove once available as generated from the OpenAPI document.
   */
  public enum SchemaType {
    ONTOLOGY,
    SHAPE,
    VOCABULARY
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
   * @return The internal identifier of the Schema.
   */
  String addSchema(ContentAccessor schema);

  /**
   * Update the schema with the given identifier.
   *
   * @param identifier The identifier of the schema to update.
   * @param schema The content to replace the schema with.
   */
  void updateSchema(String identifier, ContentAccessor schema);

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
   * Remove all Schemas from the SchemaStore.
   */
  void clear();

}
