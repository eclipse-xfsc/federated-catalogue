package eu.xfsc.fc.core.dao.assets;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.DefaultRevisionEntity;
import org.hibernate.envers.query.AuditEntity;
import org.springframework.stereotype.Repository;

import eu.xfsc.fc.core.pojo.PaginatedResults;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.hibernate.envers.query.AuditQuery;

/**
 * Encapsulates Hibernate Envers audit queries for {@link Asset}.
 * Keeps Envers-specific types out of {@link AssetJpaDao}.
 *
 * <p>Version numbers are 1-based ordinals computed from the Envers revision order.
 * The list is returned descending (newest first).
 * Status override at query time: non-current, non-REVOKED snapshots display as DEPRECATED.
 */
@Repository
@RequiredArgsConstructor
public class AssetAuditRepository {

  private final EntityManager entityManager;

  /**
   * Return all versions of an asset entity, ordered descending (newest first).
   *
   * <p>Status override: snapshots that are not the current (latest) version and are not REVOKED
   * are displayed with status DEPRECATED, reflecting that they have been superseded.
   *
   * @param entityId the surrogate PK of the Asset
   * @return list of asset records with 1-based version numbers, newest first
   */
  @SuppressWarnings("unchecked") // Envers API returns raw List<Object[]>; cast is safe per forRevisionsOfEntity contract
  List<AssetRecord> findAllVersions(Long entityId) {
    List<Object[]> revisions = baseRevisionsQuery(entityId).getResultList();

    int total = revisions.size();
    List<AssetRecord> result = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      int version = total - i;
      boolean isCurrent = (i == 0);
      result.add(toRecord(revisions.get(i), version, isCurrent));
    }
    return result;
  }

  @SuppressWarnings("unchecked") // Envers API returns raw List<Object[]>; cast is safe per forRevisionsOfEntity contract
  private List<AssetRecord> findVersionsPage(Long entityId, int page, int size, int total) {
    if (total == 0) {
      return List.of();
    }
    int offset = page * size;
    int maxResults = Math.min(size, total - offset);
    if (maxResults <= 0) {
      return List.of();
    }

    List<Object[]> revisions = baseRevisionsQuery(entityId)
        .setFirstResult(offset)
        .setMaxResults(maxResults)
        .getResultList();

    List<AssetRecord> result = new ArrayList<>(revisions.size());
    for (int i = 0; i < revisions.size(); i++) {
      int version = total - (offset + i);
      boolean isCurrent = (version == total);
      result.add(toRecord(revisions.get(i), version, isCurrent));
    }
    return result;
  }

  /**
   * Return a specific version of an asset entity.
   *
   * @param entityId the surrogate PK of the Asset
   * @param version  1-based version ordinal
   * @return the asset record at that version, or empty if out of range
   */
  @SuppressWarnings("unchecked") // Envers API returns raw List<Object[]>; cast is safe per forRevisionsOfEntity contract
  Optional<AssetRecord> findVersion(Long entityId, int version) {
    if (version < 1) {
      return Optional.empty();
    }
    // Pre-count is required to compute isCurrent and previousVersion/nextVersion navigation links.
    // Unlike SchemaAuditRepository, asset versions carry link metadata that needs the total.
    int total = countVersions(entityId);
    if (version > total) {
      return Optional.empty();
    }

    List<Object[]> revisions = baseRevisionsQuery(entityId)
        .setFirstResult(total - version)
        .setMaxResults(1)
        .getResultList();

    if (revisions.isEmpty()) {
      return Optional.empty();
    }
    boolean isCurrent = (version == total);
    return Optional.of(toRecord(revisions.getFirst(), version, isCurrent));
  }

  /**
   * Count the number of Envers revisions for an asset entity.
   *
   * @param entityId the surrogate PK of the Asset
   * @return revision count
   */
  int countVersions(Long entityId) {
    Long count = (Long) AuditReaderFactory.get(entityManager).createQuery()
        .forRevisionsOfEntity(Asset.class, false, true)
        .add(AuditEntity.id().eq(entityId))
        .addProjection(AuditEntity.revisionNumber().count())
        .getSingleResult();
    return count.intValue();
  }

  /**
   * Return a paginated page of versions together with the total revision count,
   * using a single {@link #countVersions} call and one page query.
   *
   * @param entityId the surrogate PK of the Asset
   * @param page     0-based page index
   * @param size     page size
   * @return paginated results; total is 0 if no revisions exist
   */
  PaginatedResults<AssetRecord> findVersionsPageWithTotal(Long entityId, int page, int size) {
    int total = countVersions(entityId);
    if (total == 0) {
      return new eu.xfsc.fc.core.pojo.PaginatedResults<>(0, List.of());
    }
    List<AssetRecord> items = findVersionsPage(entityId, page, size, total);
    return new eu.xfsc.fc.core.pojo.PaginatedResults<>(total, items);
  }

  @SuppressWarnings("unchecked") // Envers API returns raw AuditQuery; cast of getResultList() result is safe
  private AuditQuery baseRevisionsQuery(Long entityId) {
    return AuditReaderFactory.get(entityManager).createQuery()
        .forRevisionsOfEntity(Asset.class, false, true)
        .add(AuditEntity.id().eq(entityId))
        .addOrder(AuditEntity.revisionNumber().desc());
  }

  private AssetRecord toRecord(Object[] revision, int version, boolean isCurrent) {
    Asset snapshot = (Asset) revision[0];
    DefaultRevisionEntity revEntity = (DefaultRevisionEntity) revision[1];
    Instant revTimestamp = Instant.ofEpochMilli(revEntity.getTimestamp());

    // Envers records the state at time of mutation; in-place UPDATE leaves all historical snapshots
    // with ACTIVE status, but semantically older versions are superseded. Compute display status here.
    AssetStatus snapshotStatusEnum = AssetStatus.values()[snapshot.getStatus()];
    AssetStatus displayStatus = isCurrent ? snapshotStatusEnum : switch (snapshotStatusEnum) {
      case REVOKED -> AssetStatus.REVOKED;
      default -> AssetStatus.DEPRECATED;
    };

    AssetRecord record = AssetRecord.builder()
        .assetHash(snapshot.getAssetHash())
        .id(snapshot.getSubjectId())
        .issuer(snapshot.getIssuer())
        .uploadTime(revTimestamp)
        .statusTime(snapshot.getStatusTime())
        .expirationTime(snapshot.getExpirationTime())
        .status(displayStatus)
        .content(snapshot.getContent() == null ? null : new ContentAccessorDirect(snapshot.getContent()))
        .validatorDids(snapshot.getValidators() == null ? null : Arrays.asList(snapshot.getValidators()))
        .contentType(snapshot.getContentType())
        .fileSize(snapshot.getFileSize())
        .originalFilename(snapshot.getOriginalFilename())
        .changeComment(snapshot.getChangeComment())
        .build();
    record.setVersion(version);
    record.setIsCurrent(isCurrent);
    return record;
  }
}
