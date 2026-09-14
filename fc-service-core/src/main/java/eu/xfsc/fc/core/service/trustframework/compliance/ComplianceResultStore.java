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

  /**
   * Persists an audit record for a compliance-check attempt that never produced an outcome because
   * the trust service could not be reached or did not respond in time. The record is written with
   * {@code conforms=false} — an unreachable service is an infrastructure failure, not a verdict that
   * the asset is non-compliant.
   *
   * <p>Writes the audit row only; it neither throws a replacement exception on success nor
   * suppresses the originating transport failure — that failure is the caller's concern.
   *
   * @param assetId            IRI of the asset the attempt targeted
   * @param frameworkProfileId profile the attempt used
   * @param familyId           trust-framework family the profile belongs to
   * @param category           {@link FailureCategory#SERVICE_UNREACHABLE} or
   *                           {@link FailureCategory#SERVICE_TIMEOUT}; never
   *                           {@link FailureCategory#UNVERIFIABLE_ATTESTATION}
   * @param failureDetail      human-readable detail (e.g. the originating exception message);
   *                           may be {@code null}
   * @return the ID of the stored record; never {@code null}
   */
  Long storeFailedAttempt(String assetId, String frameworkProfileId, String familyId,
                          FailureCategory category, String failureDetail);
}
