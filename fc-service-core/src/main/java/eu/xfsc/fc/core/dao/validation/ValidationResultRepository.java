package eu.xfsc.fc.core.dao.validation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

}
