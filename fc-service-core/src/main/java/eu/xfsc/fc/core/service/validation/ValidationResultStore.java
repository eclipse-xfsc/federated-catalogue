package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.core.dao.validation.OutdatedReason;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Write and read boundary interface for validation result records and their graph projection.
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
   * Persists the validation result to the relational store only, without attempting a graph
   * projection, and returns its storage ID.
   *
   * <p>The graph carries claims about assets; some records this store accepts are not claims
   * about an asset at all (e.g. a note that a check could not be attempted), and writing those
   * to the graph would misrepresent them as verdicts on the federated query surface. Use this
   * method for such records; use {@link #store} for anything that is itself an asset-level
   * result.</p>
   *
   * @param result the validation result to store
   * @return the ID of the stored result; never null
   */
  Long storeWithoutGraphSync(ValidationResultRecord result);

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
   */
  void syncToGraph(ValidationResult result, GraphStore graphStore);

  /**
   * Marks all validation results for the given asset ID as outdated.
   *
   * @param assetId the asset IRI whose results to mark outdated
   * @param reason  why the results are being invalidated
   */
  void markOutdatedByAssetId(String assetId, OutdatedReason reason);

  /**
   * Deletes all validation results that reference the given asset ID, from both the relational DB
   * and the graph store.
   *
   * <p>Must be called before the asset itself is deleted so that graph triples can still
   * be resolved by result ID. Deleting results for an asset that also appears in multi-asset
   * validation batches removes the entire result row, not just the reference.</p>
   *
   * @param assetId the asset IRI as stored in {@code validation_result.asset_ids}
   */
  void deleteByAssetId(String assetId);
}
