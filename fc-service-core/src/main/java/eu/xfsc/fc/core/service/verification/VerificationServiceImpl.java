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
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import eu.xfsc.fc.core.pojo.VerificationResultParticipant;
import eu.xfsc.fc.core.pojo.VerificationResultResource;
import lombok.extern.slf4j.Slf4j;


/**
 * Implementation of the {@link VerificationService} interface.
 * Acts as the Context in the Strategy pattern, delegating verification
 * and data extraction logic to a {@link VerificationStrategy}.
 * Currently delegates to {@link CredentialVerificationStrategy}.
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
   * @param payload the Self-Description content used to determine which strategy to apply
   * @return the resolved strategy
   */
  private VerificationStrategy resolveStrategy(ContentAccessor payload) {
    return credentialStrategy;
  }

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Participant metadata validation result. If the validation fails, the reason explains the issue.
   */
  @Override
  public VerificationResultParticipant verifyParticipantSelfDescription(ContentAccessor payload) throws VerificationException {
    return (VerificationResultParticipant) resolveStrategy(payload).verifySelfDescription(payload, true, PARTICIPANT,
            verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  @Override
  public VerificationResultOffering verifyOfferingSelfDescription(ContentAccessor payload) throws VerificationException {
    return (VerificationResultOffering) resolveStrategy(payload).verifySelfDescription(payload, true, SERVICE_OFFERING,
            verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  @Override
  public VerificationResultResource verifyResourceSelfDescription(ContentAccessor payload) throws VerificationException {
    return (VerificationResultResource) resolveStrategy(payload).verifySelfDescription(payload, true, RESOURCE,
            verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Self-Description metadata validation result. If the validation fails, the reason explains the issue.
   */
  @Override
  public VerificationResult verifySelfDescription(ContentAccessor payload) throws VerificationException {
    return verifySelfDescription(payload, verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
  }

  @Override
  public VerificationResult verifySelfDescription(ContentAccessor payload, boolean verifySemantics, boolean verifySchema,
		  boolean verifyVPSignatures, boolean verifyVCSignatures) throws VerificationException {
    return resolveStrategy(payload).verifySelfDescription(payload, false, UNKNOWN,
            verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
  }

  /**
   * A method that returns a list of claims given a self-description's VerifiablePresentation
   *
   * @param payload a self-description as Verifiable Presentation for claims extraction
   * @return a list of claims.
   */
  @Override
  public List<SdClaim> extractClaims(ContentAccessor payload) {
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


  /* SD validation against SHACL Schemas — delegated to SchemaValidationService */

  /**
   * @deprecated Use {@link SchemaValidationService#validateAgainstCompositeSchema} directly.
   */
  @Deprecated
  @Override
  public SchemaValidationResult verifySelfDescriptionAgainstCompositeSchema(ContentAccessor payload) {
    return schemaValidationService.validateSelfDescriptionAgainstCompositeSchema(payload);
  }

  /**
   * @deprecated Use {@link SchemaValidationService#validateAgainstSchema} directly.
   */
  @Deprecated
  @Override
  public SchemaValidationResult verifySelfDescriptionAgainstSchema(ContentAccessor payload, ContentAccessor schema) {
    return schemaValidationService.validateSelfDescriptionAgainstSchema(payload, schema);
  }

}
