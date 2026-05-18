package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.ROLE_PARTICIPANT;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.ROLE_RESOURCE;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.ROLE_SERVICE_OFFERING;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultParticipant;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultResource;
import lombok.extern.slf4j.Slf4j;


/**
 * Implementation of the {@link VerificationService} interface.
 * Thin delegate over a single {@link VerificationStrategy} implementation
 * ({@link CredentialVerificationStrategy} for W3C VC/VP credentials in JSON-LD form).
 *
 * <p>Credential-format dispatch (Loire-JWT vs. danubetech VC2) is handled inside the
 * verifier via {@link CredentialFormatDetector}; it is not a sibling-strategy axis at
 * this level. Structural validation of stored assets against stored schemas is the
 * responsibility of {@code AssetValidationService}, not this service.
 */
@Slf4j
@Component
public class VerificationServiceImpl implements VerificationService {

  @Value("${federated-catalogue.verification.semantics:true}")
  private boolean verifySemantics;
  @Value("${federated-catalogue.verification.schema:false}")
  private boolean verifySchema;
  @Value("${federated-catalogue.verification.vp-signature:true}")
  private boolean verifyVPSignature;
  @Value("${federated-catalogue.verification.vc-signature:true}")
  private boolean verifyVCSignature;

  @Autowired
  private VerificationStrategy credentialStrategy;

  @Autowired
  private SchemaValidationService schemaValidationService;

  /** Package-private for testing: allows overriding the schema verification toggle. */
  void setVerifySchema(boolean verifySchema) {
    this.verifySchema = verifySchema;
  }

  private VerificationStrategy resolveStrategy(ContentAccessor payload) {
    return credentialStrategy;
  }

  /**
   * Validates the credential payload (JSON-LD format) and extracts typed Participant metadata.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return a Participant metadata validation result. If the validation fails, the reason explains the issue.
   */
  @Override
  public CredentialVerificationResultParticipant verifyParticipantCredential(ContentAccessor payload) throws VerificationException {
    return (CredentialVerificationResultParticipant) resolveStrategy(payload).verifyCredential(payload, true,
        ROLE_PARTICIPANT,
            verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  /**
   * Validates the credential payload (JSON-LD format) and extracts typed Offering metadata.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  @Override
  public CredentialVerificationResultOffering verifyOfferingCredential(ContentAccessor payload) throws VerificationException {
    return (CredentialVerificationResultOffering) resolveStrategy(payload).verifyCredential(payload, true,
        ROLE_SERVICE_OFFERING,
            verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  /**
   * Validates the credential payload (JSON-LD format) and extracts typed Resource metadata.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  @Override
  public CredentialVerificationResultResource verifyResourceCredential(ContentAccessor payload) throws VerificationException {
    return (CredentialVerificationResultResource) resolveStrategy(payload).verifyCredential(payload, true,
        ROLE_RESOURCE,
            verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  /**
   * Validates the credential payload (JSON-LD format) and extracts generic credential metadata.
   *
   * @param payload ContentAccessor to credential which should be validated.
   * @return a credential metadata validation result. If the validation fails, the reason explains the issue.
   */
  @Override
  public CredentialVerificationResult verifyCredential(ContentAccessor payload) throws VerificationException {
    return verifyCredential(payload, verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  @Override
  public CredentialVerificationResult verifyCredential(ContentAccessor payload, boolean verifySemantics, boolean verifySchema,
		  boolean verifyVPSignatures, boolean verifyVCSignatures) throws VerificationException {
    return resolveStrategy(payload).verifyCredential(payload, false, "",
            verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
  }

  /* Credential validation against SHACL Schemas — delegated to SchemaValidationService */

  /**
   * @deprecated Use {@link SchemaValidationService#validateCredentialAgainstCompositeSchema} directly.
   */
  @Deprecated
  @Override
  public SchemaValidationResult verifyCredentialAgainstCompositeSchema(ContentAccessor payload) {
    return schemaValidationService.validateCredentialAgainstCompositeSchema(payload);
  }

  /**
   * @deprecated Use {@link SchemaValidationService#validateCredentialAgainstSchema} directly.
   */
  @Deprecated
  @Override
  public SchemaValidationResult verifyCredentialAgainstSchema(ContentAccessor payload, ContentAccessor schema) {
    return schemaValidationService.validateCredentialAgainstSchema(payload, schema);
  }

}
