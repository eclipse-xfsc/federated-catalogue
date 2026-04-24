package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Write and read boundary interface for validation results.
 *
 * <p>{@link ValidationResultStoreImpl} is the production implementation.</p>
 */
public interface ValidationResultStore {

  /**
   * Persists the validation result and returns its storage ID.
   *
   * @param result the validation result to store
   * @return the ID of the stored result; never null
   */
  Long store(ValidationResultRecord result);

  /**
   * Returns a paginated list of validation results for the given asset ID.
   */
  Page<ValidationResult> getByAssetId(String assetId, Pageable pageable);

  /**
   * Returns a single validation result by its primary key, or empty if not found.
   */
  Optional<ValidationResult> getById(Long id);

  /**
   * Returns all validation results in a paginated form. Used by graph rebuild to iterate
   * all records without depending on the repository layer directly.
   */
  Page<ValidationResult> findAll(Pageable pageable);

  /**
   * Writes {@code fcmeta:} triples for the given result to the graph store and updates
   * {@code graph_sync_status} to {@code SYNCED} on success or {@code FAILED} on error.
   * Used during graph rebuild to restore triples from PostgreSQL state.
   */
  void syncToGraph(ValidationResult result, GraphStore graphStore);
}
