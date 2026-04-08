package eu.xfsc.fc.core.dao.assets;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository
    extends JpaRepository<Asset, Long>, AssetRepositoryCustom {

  Optional<Asset> findByAssetHash(String assetHash);

  boolean existsByAssetHash(String assetHash);

  Optional<Asset> findBySubjectIdAndStatus(String subjectId, short status);

  @Query("""
    SELECT a.assetHash
    FROM Asset a
    WHERE a.status = :status
        AND a.expirationTime < CURRENT_TIMESTAMP
  """)
  List<String> findExpiredHashes(@Param("status") int activeStatus);

  @Query(value = """
    SELECT asset_hash 
    FROM assets 
    WHERE status = :status
        AND abs(hashtext(asset_hash) % :chunks) = :chunkid
    ORDER BY asset_hash ASC LIMIT :limit
  """, nativeQuery = true)
  List<String> findHashesFirstPage(
          @Param("status") int status,
          @Param("chunks") int chunks,
          @Param("chunkid") int chunkId,
          @Param("limit") int limit);

  @Modifying
  @Query("DELETE FROM Asset")
  int deleteAllReturningCount();

  @Query(value = """
    SELECT asset_hash
    FROM assets
    WHERE asset_hash > :startHash
        AND status = :status
        AND abs(hashtext(asset_hash) % :chunks) = :chunkid
    ORDER BY asset_hash ASC LIMIT :limit
  """, nativeQuery = true)
  List<String> findHashesAfter(
          @Param("startHash") String startHash,
          @Param("status") int status,
          @Param("chunks") int chunks,
          @Param("chunkid") int chunkId,
          @Param("limit") int limit);
}
