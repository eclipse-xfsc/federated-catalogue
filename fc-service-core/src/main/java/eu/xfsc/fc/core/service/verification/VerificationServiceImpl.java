package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.PARTICIPANT;
import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.RESOURCE;
import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.SERVICE_OFFERING;
import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.UNKNOWN;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultParticipant;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultResource;
import lombok.extern.slf4j.Slf4j;


/**
 * Implementation of the {@link VerificationService} interface.
 * Acts as the Context in the Strategy pattern, delegating RDF verification
 * and data extraction logic to format-specific {@link VerificationStrategy} implementations.
 * Routes by RDF serialization format (JSON-LD, Turtle, N-Triples, RDF/XML).
 * 
 * <p>Currently delegates all RDF credentials to {@link CredentialVerificationStrategy} (JSON-LD only).
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
  @Qualifier("credentialVerificationStrategy")
  private VerificationStrategy credentialStrategy;

  @Autowired
  private SchemaValidationService schemaValidationService;

  /** Package-private for testing: allows overriding the schema verification toggle. */
  void setVerifySchema(boolean verifySchema) {
    this.verifySchema = verifySchema;
  }

  /**
   * Resolves the appropriate {@link VerificationStrategy} for the given payload.
   * Currently always returns the credential strategy. When future asset types require
   * different verification logic (e.g., non-RDF, non-credential), this method is the
   * extension point for payload-based routing.
   *
   * @param payload the RDF content used to determine which strategy to apply (currently always JSON-LD)
   * @return the resolved strategy
   */
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
    return (CredentialVerificationResultParticipant) resolveStrategy(payload).verifyCredential(payload, true, PARTICIPANT,
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
    return (CredentialVerificationResultOffering) resolveStrategy(payload).verifyCredential(payload, true, SERVICE_OFFERING,
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
    return (CredentialVerificationResultResource) resolveStrategy(payload).verifyCredential(payload, true, RESOURCE,
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
    return resolveStrategy(payload).verifyCredential(payload, false, UNKNOWN,
            verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
  }

  /**
   * A method that returns a list of claims from the given RDF credential payload without performing verification.
   *
   * @param payload the RDF credential content to extract claims from
   * @return a list of claims.
   */
  @Override
  public List<CredentialClaim> extractClaims(ContentAccessor payload) {
    return resolveStrategy(payload).extractClaims(payload);
  }

  /**
   * Override URI set for one of the Trust Framework base classes.
   *
   * @param baseClass The base class for which the URI is to be overwritten
   * @param uri New URI
   */
  public void setBaseClassUri(TrustFrameworkBaseClass baseClass, String uri) {
    credentialStrategy.setBaseClassUri(baseClass, uri);
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
