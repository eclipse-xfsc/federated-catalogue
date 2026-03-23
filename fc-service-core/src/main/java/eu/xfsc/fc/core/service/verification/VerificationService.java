package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultParticipant;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultResource;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * RDF data validation and verification interface.
 * Supports W3C Verifiable Credentials (with signature verification) and
 * generic RDF data (parsing + claim extraction only).
 * 
 * <p>Current implementation handles JSON-LD W3C Verifiable Credentials/Presentations.
 * Future: Support for plain RDF formats (Turtle, N-Triples, RDF/XML) without credentials.</p>
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
   * @param payload
   * @param verifySemantics
   * @param verifySchema
   * @param verifyVPSignatures
   * @param verifyVCSignatures
   * @return
   * @throws VerificationException
   */
  CredentialVerificationResult verifyCredential(ContentAccessor payload, boolean verifySemantics, boolean verifySchema,
		  boolean verifyVPSignatures, boolean verifyVCSignatures) throws VerificationException;

  /**
   * Extract claims from the given RDF payload. This does not do any validation of the payload.
   *
   * @param payload The RDF payload to extract claims from.
   * @return The list of extracted claims.
   */
  List<CredentialClaim> extractClaims(ContentAccessor payload);

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
