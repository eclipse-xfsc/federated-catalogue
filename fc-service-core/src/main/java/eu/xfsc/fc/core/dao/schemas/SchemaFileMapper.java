package eu.xfsc.fc.core.dao.schemas;

import java.util.Set;
import java.util.stream.Collectors;

import eu.xfsc.fc.core.service.schemastore.SchemaRecord;

public final class SchemaFileMapper {

  private SchemaFileMapper() {
  }

  public static SchemaRecord toRecord(SchemaFile entity) {
    if (entity == null) {
      return null;
    }
    Set<String> terms = entity.getTerms() == null ? null
        : entity.getTerms().stream()
            .map(SchemaTerm::getTerm)
            .collect(Collectors.toSet());
    return new SchemaRecord(
        entity.getSchemaId(),
        entity.getNameHash(),
        entity.getType(),
        entity.getCreatedAt(),
        entity.getModifiedAt(),
        entity.getContent(),
        terms);
  }

  public static SchemaFile toEntity(SchemaRecord record) {
    if (record == null) {
      return null;
    }
    SchemaFile entity = new SchemaFile();
    entity.setSchemaId(record.getId());
    entity.setNameHash(record.nameHash());
    entity.setType(record.type());
    entity.setCreatedAt(record.createdAt());
    entity.setModifiedAt(record.modifiedAt());
    entity.setContent(record.content());
    if (record.terms() != null) {
      Set<SchemaTerm> termEntities = record.terms().stream().map(term -> {
        SchemaTerm te = new SchemaTerm();
        te.setTerm(term);
        te.setSchemaFile(entity);
        return te;
      }).collect(Collectors.toSet());
      entity.setTerms(termEntities);
    }
    return entity;
  }
}
