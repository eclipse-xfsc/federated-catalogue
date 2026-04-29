package eu.xfsc.fc.core.dao.provenance;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link ProvenanceRecord} records.
 *
 * <p>All query methods are read-only by intention; writes go through the service layer
 * under {@code REQUIRES_NEW} transaction semantics to remain independent of Envers auditing.</p>
 */
public interface ProvenanceCredentialRepository extends JpaRepository<ProvenanceRecord, Long> {

  /**
   * Returns all provenance credentials for a given asset, ordered by {@code issuedAt} descending.
   *
   * @param assetId  logical asset identifier
   * @param pageable pagination and sorting parameters
   * @return a page of matching records
   */
  Page<ProvenanceRecord> findByAssetIdOrderByIssuedAtDesc(String assetId, Pageable pageable);

  /**
   * Returns all provenance credentials for a specific version of an asset.
   *
   * @param assetId      logical asset identifier
   * @param assetVersion 1-based Envers revision ordinal
   * @param pageable     pagination and sorting parameters
   * @return a page of matching records
   */
  Page<ProvenanceRecord> findByAssetIdAndAssetVersionOrderByIssuedAtDesc(
      String assetId, int assetVersion, Pageable pageable);

  /**
   * Looks up a single credential by its VC {@code id} field.
   *
   * @param credentialId the VC {@code id} URI
   * @return the record, if it exists
   */
  Optional<ProvenanceRecord> findByCredentialId(String credentialId);

  /**
   * Checks whether a credential with the given VC id already exists (duplicate guard).
   *
   * @param credentialId the VC {@code id} URI
   * @return {@code true} if the credential is already stored
   */
  boolean existsByCredentialId(String credentialId);

  /**
   * Deletes all provenance credentials for the given asset (cascade on asset deletion).
   *
   * @param assetId logical asset identifier
   */
  @Modifying
  @Query("DELETE FROM ProvenanceRecord p WHERE p.assetId = :assetId")
  void deleteByAssetId(@Param("assetId") String assetId);
}
