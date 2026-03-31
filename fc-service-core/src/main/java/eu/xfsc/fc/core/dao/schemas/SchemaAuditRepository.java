package eu.xfsc.fc.core.dao.schemas;

import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Encapsulates Hibernate Envers audit queries for {@link SchemaFile}.
 * Keeps Envers-specific types out of {@link SchemaJpaDao}.
 */
@Repository
@RequiredArgsConstructor
public class SchemaAuditRepository {

  private final EntityManager entityManager;

  /**
   * Return all versions of a schema entity in a single query, ordered ascending.
   *
   * @param entityId the surrogate PK of the SchemaFile
   * @return list of schema records with version numbers (1-based)
   */
  @SuppressWarnings("unchecked") // Envers API returns raw List<Object[]>; cast is safe per forRevisionsOfEntity contract
  List<SchemaRecord> findAllVersions(Long entityId) {
    List<Object[]> revisions = AuditReaderFactory.get(entityManager).createQuery()
        .forRevisionsOfEntity(SchemaFile.class, false, true)
        .add(AuditEntity.id().eq(entityId))
        .addOrder(AuditEntity.revisionNumber().asc())
        .getResultList();

    List<SchemaRecord> result = new ArrayList<>(revisions.size());
    for (int i = 0; i < revisions.size(); i++) {
      result.add(toRecord(revisions.get(i), i + 1));
    }
    return result;
  }

  /**
   * Return a specific version of a schema entity.
   *
   * @param entityId the surrogate PK of the SchemaFile
   * @param version  1-based version ordinal
   * @return the schema record at that version, or empty if out of range
   */
  @SuppressWarnings("unchecked") // Envers API returns raw List<Object[]>; cast is safe per forRevisionsOfEntity contract
  Optional<SchemaRecord> findVersion(Long entityId, int version) {
    if (version < 1) {
      return Optional.empty();
    }
    List<Object[]> revisions = AuditReaderFactory.get(entityManager).createQuery()
        .forRevisionsOfEntity(SchemaFile.class, false, true)
        .add(AuditEntity.id().eq(entityId))
        .addOrder(AuditEntity.revisionNumber().asc())
        .setFirstResult(version - 1)
        .setMaxResults(1)
        .getResultList();

    if (revisions.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(toRecord(revisions.getFirst(), version));
  }

  /**
   * Count the number of Envers revisions for a schema entity.
   *
   * @param entityId the surrogate PK of the SchemaFile
   * @return revision count
   */
  int countVersions(Long entityId) {
    Long count = (Long) AuditReaderFactory.get(entityManager).createQuery()
        .forRevisionsOfEntity(SchemaFile.class, false, true)
        .add(AuditEntity.id().eq(entityId))
        .addProjection(AuditEntity.revisionNumber().count())
        .getSingleResult();
    return count.intValue();
  }

  private SchemaRecord toRecord(Object[] revision, int version) {
    SchemaFile snapshot = (SchemaFile) revision[0];
    DefaultRevisionEntity revEntity = (DefaultRevisionEntity) revision[1];
    Instant revTimestamp = Instant.ofEpochMilli(revEntity.getTimestamp());
    Set<String> terms = snapshot.getTerms() == null ? new HashSet<>()
        : snapshot.getTerms().stream().map(SchemaTerm::getTerm).collect(Collectors.toSet());
    return new SchemaRecord(
        snapshot.getSchemaId(), snapshot.getNameHash(), snapshot.getType(),
        revTimestamp, snapshot.getModifiedAt(), snapshot.getContent(), terms, version);
  }
}
