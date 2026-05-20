package eu.xfsc.fc.core.service.trustframework.compliance;

import eu.xfsc.fc.core.dao.validation.ValidationResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Write and read boundary for compliance check results.
 *
 * <p>Persists each {@link ComplianceCheckOutcome} as a {@link ValidationResult} record
 * with {@code validatorType=TRUST_FRAMEWORK}, then exposes typed read-back by asset.
 * This is a thin wrapper over {@link eu.xfsc.fc.core.service.validation.ValidationResultStore}
 * and must never bypass it.</p>
 */
public interface ComplianceResultStore {

  /**
   * Persists the compliance outcome and returns the storage ID of the created record.
   *
   * @param assetId            IRI of the asset that was checked
   * @param frameworkProfileId profile that performed the check (stored as first validator ID)
   * @param familyId           trust-framework family (stored as second validator ID)
   * @param outcome            the compliance outcome to persist; must not be {@code null}
   * @return the ID of the stored record; never {@code null}
   */
  Long store(String assetId, String frameworkProfileId, String familyId, ComplianceCheckOutcome outcome);

  /**
   * Returns a paginated list of compliance results for the given asset ID.
   *
   * @param assetId  the asset IRI to query
   * @param pageable paging and sorting parameters
   * @return page of results; never {@code null}
   */
  Page<ValidationResult> findByAssetId(String assetId, Pageable pageable);
}
