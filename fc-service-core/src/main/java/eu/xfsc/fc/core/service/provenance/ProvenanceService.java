package eu.xfsc.fc.core.service.provenance;

import eu.xfsc.fc.api.generated.model.ProvenanceCredential;
import eu.xfsc.fc.api.generated.model.ProvenanceCredentials;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult;
import org.springframework.data.domain.Pageable;

/**
 * Business-logic interface for provenance credential operations.
 *
 * <p>Write operations that augment an existing asset ({@link #add}, {@link #verifyOne},
 * {@link #verifyAll}) run under {@code REQUIRES_NEW} transaction semantics so they remain
 * invisible to Hibernate Envers and do not produce a new asset revision. The cascade
 * deletion {@link #deleteByAssetId} joins the calling transaction (default propagation) so
 * that provenance cleanup is atomic with the asset deletion that triggered it.</p>
 *
 * <p>Error semantics:</p>
 * <ul>
 *   <li>{@link eu.xfsc.fc.core.exception.NotFoundException} — asset or credential not found</li>
 *   <li>{@link eu.xfsc.fc.core.exception.ClientException} — malformed input, VC version mismatch,
 *       wrong {@code credentialSubject.id}, or protected namespace usage</li>
 *   <li>{@link eu.xfsc.fc.core.exception.ConflictException} — duplicate {@code credentialId}</li>
 * </ul>
 */
public interface ProvenanceService {

  /**
   * Parses and stores a provenance credential against a specific asset version.
   *
   * <p>Validation rules:</p>
   * <ul>
   *   <li>VC 1.1 payloads are rejected; only VC 2.0 JSON-LD and VC-JWT are accepted.</li>
   *   <li>{@code credentialSubject.id} must equal {@code {assetId}:v{N}}.</li>
   *   <li>The VC payload must not use the catalogue's protected internal namespace.</li>
   *   <li>Duplicate {@code credentialId} (VC {@code id} field) throws {@link eu.xfsc.fc.core.exception.ConflictException}.</li>
   * </ul>
   *
   * <p>When the graph store is active, the corresponding PROV-O triple is written as a
   * side-effect under the versioned asset identifier {@code {assetId}:v{N}}.</p>
   *
   * @param assetId  logical asset IRI
   * @param version  1-based Envers revision ordinal, or {@code null} to target the current version
   * @param rawVc    raw VC content (JSON-LD string or JWT compact serialisation)
   * @param format   credential format hint (e.g. {@code "application/vc+ld+json"},
   *                 {@code "application/vc+jwt"})
   * @return the persisted provenance credential record
   */
  ProvenanceCredential add(String assetId, Integer version, String rawVc, String format);

  /**
   * Returns a paginated list of provenance credentials for an asset, ordered by {@code issuedAt}
   * descending.
   *
   * @param assetId  logical asset IRI
   * @param version  1-based Envers ordinal to filter by, or {@code null} for all versions
   * @param pageable pagination parameters
   * @return paginated credential list
   */
  ProvenanceCredentials list(String assetId, Integer version, Pageable pageable);

  /**
   * Returns a single provenance credential by its VC {@code id} field.
   *
   * @param assetId      logical asset IRI
   * @param credentialId VC {@code id} URI
   * @return the credential record
   */
  ProvenanceCredential get(String assetId, String credentialId);

  /**
   * Verifies a single provenance credential and persists the extended result.
   *
   * <p>Invokes {@link eu.xfsc.fc.core.service.verification.VerificationService} for signature and
   * DID checks. The {@code verified} flag and {@code verificationResult} columns are updated; no
   * new Envers revision is created.</p>
   *
   * @param assetId      logical asset IRI
   * @param credentialId VC {@code id} URI
   * @return the extended verification result
   */
  ProvenanceVerificationResult verifyOne(String assetId, String credentialId);

  /**
   * Verifies all provenance credentials for an asset (optionally scoped to a version) and
   * persists the result for each credential.
   *
   * <p>Each credential is verified individually; results are aggregated into a single
   * {@link ProvenanceVerificationResult}. The aggregated result is {@code isValid=true} only when
   * all credentials pass. Per-credential errors are collected in {@code errors}.</p>
   *
   * @param assetId logical asset IRI
   * @param version 1-based Envers ordinal to scope the batch, or {@code null} for all versions
   * @return aggregated verification result
   */
  ProvenanceVerificationResult verifyAll(String assetId, Integer version);

  /**
   * Deletes all provenance credentials associated with the given asset.
   *
   * <p>Intended for cascade deletion when an asset is removed.</p>
   *
   * @param assetId logical asset IRI
   */
  void deleteByAssetId(String assetId);
}
