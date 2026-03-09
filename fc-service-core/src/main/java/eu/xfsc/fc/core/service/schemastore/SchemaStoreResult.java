package eu.xfsc.fc.core.service.schemastore;

/**
 * Result of adding or updating a schema, carrying the identifier and an optional warning.
 *
 * @param id      the internal identifier of the schema
 * @param warning user-visible warning if protected namespace statements were filtered, or null
 */
public record SchemaStoreResult(String id, String warning) {}
