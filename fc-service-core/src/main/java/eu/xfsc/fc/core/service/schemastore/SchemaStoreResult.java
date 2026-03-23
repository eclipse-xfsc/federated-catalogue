package eu.xfsc.fc.core.service.schemastore;

import java.time.Instant;

/**
 * Result of adding or updating a schema, carrying the identifier and an optional warning.
 *
 * @param id         the internal identifier of the schema
 * @param warning    user-visible warning if protected namespace statements were filtered, or null
 * @param uploadTime timestamp when the schema was uploaded
 */
public record SchemaStoreResult(String id, String warning, Instant uploadTime) {

  public SchemaStoreResult(String id, String warning) {
    this(id, warning, null);
  }
}
