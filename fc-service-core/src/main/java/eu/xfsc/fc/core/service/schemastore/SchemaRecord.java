package eu.xfsc.fc.core.service.schemastore;

import java.time.Instant;
import java.util.Set;

import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;

public record SchemaRecord(String schemaId, String nameHash, SchemaType type, Instant uploadTime, Instant updateTime, String content, Set<String> terms) {

  public SchemaRecord(String schemaId, String nameHash, SchemaType type, String content, Set<String> terms) {
    this(schemaId, nameHash, type, Instant.now(), Instant.now(), content, terms);
  }

  public String getId() {
    return schemaId == null ? nameHash : schemaId;
  }
}
