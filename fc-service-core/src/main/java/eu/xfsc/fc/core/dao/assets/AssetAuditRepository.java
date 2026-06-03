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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
    List<Object[]> contentRevisions = dedupeByAssetHash(baseRevisionsQuery(entityId).getResultList());

    int total = contentRevisions.size();
    List<AssetRecord> result = new ArrayList<>(total);
    for (int i = 0; i < total; i++) {
      int version = total - i;
      boolean isCurrent = (i == 0);
      result.add(toRecord(contentRevisions.get(i), version, isCurrent));
    }
    return result;
  }

  /**
   * Collapse a newest-first revision list to one entry per distinct {@code asset_hash}.
   *
   * <p>Revisions that share a hash with a newer revision represent non-content changes (linkage,
   * lifecycle status, last-modified bookkeeping) and are folded away: the most recent revision
   * carrying each hash is retained so the displayed snapshot reflects the freshest state of that
   * content version.</p>
   */
  private List<Object[]> dedupeByAssetHash(List<Object[]> revisionsNewestFirst) {
    Map<String, Object[]> firstSeenPerHash = new LinkedHashMap<>();
    for (Object[] revision : revisionsNewestFirst) {
      String hash = ((Asset) revision[0]).getAssetHash();
      firstSeenPerHash.putIfAbsent(hash, revision);
    }
    return new ArrayList<>(firstSeenPerHash.values());
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

    List<Object[]> contentRevisions = dedupeByAssetHash(baseRevisionsQuery(entityId).getResultList());
    int end = Math.min(offset + maxResults, contentRevisions.size());
    List<AssetRecord> result = new ArrayList<>(end - offset);
    for (int i = offset; i < end; i++) {
      int version = total - i;
      boolean isCurrent = (version == total);
      result.add(toRecord(contentRevisions.get(i), version, isCurrent));
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

    List<Object[]> contentRevisions = dedupeByAssetHash(baseRevisionsQuery(entityId).getResultList());
    int total = contentRevisions.size();
    if (version > total) {
      return Optional.empty();
    }
    int index = total - version;
    boolean isCurrent = (version == total);
    return Optional.of(toRecord(contentRevisions.get(index), version, isCurrent));
  }

  /**
   * Count the number of content versions for an asset entity.
   *
   * <p>A content version corresponds to a distinct {@code asset_hash} observed in the audit history.
   * Revisions that only mutate non-content metadata (linkage to a human-readable companion,
   * lifecycle status, or last-modified bookkeeping) reuse an existing hash and therefore do not
   * advance the count. This matches the SRS rule that attaching sub-resources to an asset must not
   * produce a new version of the asset.</p>
   *
   * @param entityId the surrogate PK of the asset
   * @return number of distinct content hashes in the audit history
   */
  int countVersions(Long entityId) {
    return dedupeByAssetHash(baseRevisionsQuery(entityId).getResultList()).size();
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
        .contentKind(snapshot.getContentKind())
        .build();
    record.setVersion(version);
    record.setIsCurrent(isCurrent);
    return record;
  }
}
