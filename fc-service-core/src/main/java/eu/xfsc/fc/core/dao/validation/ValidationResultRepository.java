package eu.xfsc.fc.core.dao.validation;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
/** Spring Data JPA repository for {@link ValidationResult} entities. */
public interface ValidationResultRepository extends JpaRepository<ValidationResult, Long> {

  /**
   * Find all validation results where the given asset ID appears in the asset_ids array.
   *
   * <p>Uses the GIN index  (Generalized Inverted Index - designed for composite values) on {@code asset_ids} 
   * for efficient lookup. The {@code = ANY()} operator requires a GIN index on the array column</p>
   */
  @Query(value = "SELECT * FROM validation_result WHERE :assetId = ANY(asset_ids)",
      countQuery = "SELECT COUNT(*) FROM validation_result WHERE :assetId = ANY(asset_ids)",
      nativeQuery = true)
  Page<ValidationResult> findByAssetId(@Param("assetId") String assetId, Pageable pageable);

  /** Returns all validation results referencing {@code assetId}, without pagination. */
  @Query(value = "SELECT * FROM validation_result WHERE :assetId = ANY(asset_ids)", nativeQuery = true)
  List<ValidationResult> findAllByAssetId(@Param("assetId") String assetId);

  /** Deletes all validation results that reference {@code assetId} in their {@code asset_ids} array. */
  @Modifying
  @Query(value = "DELETE FROM validation_result WHERE :assetId = ANY(asset_ids)", nativeQuery = true)
  void deleteAllByAssetId(@Param("assetId") String assetId);

  /**
   * Marks all validation results that reference the given asset ID as outdated with the supplied reason.
   */
  @Modifying(clearAutomatically = true)
  @Query(value = "UPDATE validation_result SET outdated = true, outdated_reason = :reason "
      + "WHERE :assetId = ANY(asset_ids)", nativeQuery = true)
  void markOutdatedByAssetId(@Param("assetId") String assetId, @Param("reason") String reason);

}
