package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultParticipant;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultResource;

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
   * @return a Participant metadata validation result. If the validation fails, the reason explains the issue.
   */
  CredentialVerificationResultParticipant verifyParticipantCredential(ContentAccessor payload) throws VerificationException;

  /**
   * Validates the credential payload (JSON-LD format) and extracts typed metadata.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  CredentialVerificationResultOffering verifyOfferingCredential(ContentAccessor payload) throws VerificationException;

  /**
   * Validates the credential payload (JSON-LD format) and extracts typed metadata.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  CredentialVerificationResultResource verifyResourceCredential(ContentAccessor payload) throws VerificationException;

  /**
   * Validates the credential payload (JSON-LD format) and extracts typed metadata.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return a credential metadata validation result. If the validation fails, the reason explains the issue.
   */
  CredentialVerificationResult verifyCredential(ContentAccessor payload) throws VerificationException;

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

  /**
   * The function validates the credential against the given schema.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @param schema ContentAccessor - the schema to validate credential against (null = composite)
   * @return the result of the semantic validation.
   * @deprecated Use {@link SchemaValidationService#validateCredentialAgainstSchema} directly.
   */
  @Deprecated
  SchemaValidationResult verifyCredentialAgainstSchema(ContentAccessor payload, ContentAccessor schema);

  /**
   * The function validates the credential against the composite schema.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return the result of the semantic validation.
   * @deprecated Use {@link SchemaValidationService#validateCredentialAgainstCompositeSchema} directly.
   */
  @Deprecated
  SchemaValidationResult verifyCredentialAgainstCompositeSchema(ContentAccessor payload);

}
