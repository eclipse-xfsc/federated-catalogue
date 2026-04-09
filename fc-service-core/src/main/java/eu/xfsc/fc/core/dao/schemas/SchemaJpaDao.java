package eu.xfsc.fc.core.dao.schemas;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class SchemaJpaDao implements SchemaDao {

  private final SchemaFileRepository repository;
  private final SchemaAuditRepository auditHelper;

  @Override
  public int getSchemaCount() {
    return (int) repository.count();
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SchemaRecord> select(String schemaId) {
    return repository.findBySchemaId(schemaId)
        .map(SchemaFileMapper::toRecord);
  }

  @Override
  public Map<String, Collection<String>> selectSchemas() {
    return aggregateToMap(repository.findAllTypeAndSchemaId());
  }

  @Override
  public Map<String, Collection<String>> selectSchemasByTerm(String term) {
    return aggregateToMap(repository.findTypeAndSchemaIdByTerm(term));
  }

  // Explicit duplicate checks are needed because JPA's save() uses merge() for entities
  // with non-null @Id, which silently upserts instead of throwing on conflicts.
  // SchemaStoreImpl relies on DuplicateKeyException to detect and report conflicts.
  // The message must contain the constraint name (e.g. "schemafiles_pkey", "schematerms_pkey")
  // because SchemaStoreImpl inspects it to determine the conflict type.
  @Override
  @Transactional
  public boolean insert(SchemaRecord sr) {
      SchemaFile entity = SchemaFileMapper.toEntity(sr);
    if (repository.existsBySchemaId(entity.getSchemaId())) {
      throw new DuplicateKeyException("uq_schemafiles_schemaid: " + entity.getSchemaId());
    }
    if (sr.terms() != null && !sr.terms().isEmpty()) {
      List<String> existing = repository.findExistingTerms(sr.terms());
      if (!existing.isEmpty()) {
        throw new DuplicateKeyException("schematerms_pkey: " + existing.getFirst());
      }
    }
    repository.saveAndFlush(entity);
    return true;
  }

  @Override
  @Transactional
  public void update(String id, String content, Collection<String> terms) {
      SchemaFile entity = repository.findBySchemaId(id)
        .orElseThrow(() -> new NotFoundException("Schema with id " + id + " was not found"));
    entity.setModifiedAt(Instant.now());
    entity.setContent(content);
    entity.getTerms().clear();
    if (terms != null) {
      for (String term : terms) {
        SchemaTerm te = new SchemaTerm();
        te.setTerm(term);
        te.setSchemaFile(entity);
        entity.getTerms().add(te);
      }
    }
    // Explicit flush needed so DuplicateKeyException surfaces here
    // rather than at transaction commit — the caller catches it.
    repository.saveAndFlush(entity);
  }

  @Override
  @Transactional
  public String delete(String schemaId) {
    return repository.findBySchemaId(schemaId).map(entity -> {
      String type = entity.getType().name();
      repository.delete(entity);
      return type;
    }).orElse(null);
  }

  @Override
  @Transactional
  public int deleteAll() {
    return repository.deleteAllReturningCount();
  }

  @Override
  public Optional<String> selectLatestContentByType(String typeName) {
    return repository.findLatestContentByType(SchemaType.valueOf(typeName));
  }

  @Override
  @Transactional(readOnly = true)
  public List<SchemaRecord> selectVersions(String schemaId) {
    return repository.findBySchemaId(schemaId)
        .map(entity -> auditHelper.findAllVersions(entity.getId()))
        .orElse(List.of());
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<SchemaRecord> selectVersion(String schemaId, int version) {
    return repository.findBySchemaId(schemaId)
        .flatMap(entity -> auditHelper.findVersion(entity.getId(), version));
  }

  @Override
  @Transactional(readOnly = true)
  public int getVersionCount(String schemaId) {
    return repository.findBySchemaId(schemaId)
        .map(entity -> auditHelper.countVersions(entity.getId()))
        .orElse(0);
  }

  private Map<String, Collection<String>> aggregateToMap(List<Object[]> rows) {
    Map<String, Collection<String>> result = new HashMap<>();
    for (Object[] row : rows) {
      String type = ((SchemaType) row[0]).name();
      String schemaId = (String) row[1];
      result.computeIfAbsent(type, t -> new HashSet<>()).add(schemaId);
    }
    return result;
  }
}
