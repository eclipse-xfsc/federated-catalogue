package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;

import org.springframework.stereotype.Service;

/**
 * Ingest-time verification of W3C Verifiable Credentials and Presentations.
 * Parses JSON-LD VC/VP payloads, verifies signatures, applies the protected-namespace
 * filter, and extracts typed claims for the upload pipeline.
 *
 * <p>Structural validation of stored assets against stored schemas (SHACL, JSON Schema,
 * XML Schema) is the responsibility of
 * {@link eu.xfsc.fc.core.service.validation.AssetValidationService} and is not exposed
 * through this interface.</p>
 */
@Service
public interface VerificationService {

  /**
   * Validates the credential payload (JSON-LD format) and extracts typed metadata.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return a credential metadata validation result. If the validation fails, the reason explains the issue.
   */
  CredentialVerificationResult verifyCredential(ContentAccessor payload) throws VerificationException;

  /**
   * Validates the credential payload with an explicit trust-framework role requirement.
   *
   * <p>Use {@code requireRole = false} for credential families that intentionally do not
   * carry a trust-framework-recognised type — e.g. provenance credentials whose
   * {@code credentialSubject} only declares PROV-O predicates. With the default
   * {@code requireRole = true}, an unresolvable type yields a {@link eu.xfsc.fc.core.exception.ClientException}.</p>
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @param requireRole whether to reject the credential when its type cannot be resolved
   *     to a role in any active trust-framework bundle.
   * @return a credential metadata validation result.
   */
  CredentialVerificationResult verifyCredential(ContentAccessor payload, boolean requireRole)
      throws VerificationException;

  /**
   * Validates the credential payload with custom verification toggles (JSON-LD format).
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @param verifySemantics - whether to perform semantic validation (e.g. required properties, value types)
   * @param verifySchema - whether to perform schema validation (SHACL, JSON Schema, XML Schema)
   * @param verifyVPSignatures - whether to perform VP signature verification (if the credential is a VP)
   * @param verifyVCSignatures - whether to perform VC signature verification (if the credential is a VC)
   * @return a credential metadata validation result. If the validation fails, the reason explains the issue.
   * @throws VerificationException if the verification process encounters an error (e.g. invalid format, signature verification failure, schema validation failure).
   */
  CredentialVerificationResult verifyCredential(ContentAccessor payload, boolean verifySemantics, boolean verifySchema,
		  boolean verifyVPSignatures, boolean verifyVCSignatures) throws VerificationException;

}
