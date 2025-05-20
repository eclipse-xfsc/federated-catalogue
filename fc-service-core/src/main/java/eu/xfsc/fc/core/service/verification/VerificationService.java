package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SemanticValidationResult;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import eu.xfsc.fc.core.pojo.VerificationResultParticipant;
import eu.xfsc.fc.core.pojo.VerificationResultResource;

import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Validation Self-Description interface.
 */
@Service
public interface VerificationService {

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Participant metadata validation result. If the validation fails, the reason explains the issue.
   */
  VerificationResultParticipant verifyParticipantSelfDescription(ContentAccessor payload) throws VerificationException;

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  VerificationResultOffering verifyOfferingSelfDescription(ContentAccessor payload) throws VerificationException;

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Verification result. If the verification fails, the reason explains the issue.
   */
  VerificationResultResource verifyResourceSelfDescription(ContentAccessor payload) throws VerificationException;
  
  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload ContentAccessor to SD which should be syntactically validated.
   * @return a Self-Description metadata validation result. If the validation fails, the reason explains the issue.
   */
  VerificationResult verifySelfDescription(ContentAccessor payload) throws VerificationException;

  /**
   * The function validates the Self-Description as JSON and tries to parse the json handed over.
   *
   * @param payload
   * @param verifySemantics
   * @param verifySchema
   * @param verifyVPSignatures
   * @param verifyVCSignatures
   * @return
   * @throws VerificationException
   */
  VerificationResult verifySelfDescription(ContentAccessor payload, boolean verifySemantics, boolean verifySchema, 
		  boolean verifyVPSignatures, boolean verifyVCSignatures) throws VerificationException;

  /**
   * Extract claims from the given payload. This does not do any validation of the payload.
   *
   * @param payload The payload to extract claims from.
   * @return The list of extracted claims.
   */
  List<SdClaim> extractClaims(ContentAccessor payload);

  /**
   * The function validates the Self-Description against the composite schema.
   *
   * @param payload ContentAccessor to SD which should be validated.
   * @param schema ContentAccessor - the schema to validate SDD against
   * @return the result of the semantic validation.
   */
  SemanticValidationResult verifySelfDescriptionAgainstSchema(ContentAccessor payload, ContentAccessor schema);

  /**
   * The function validates the Self-Description against the composite schema.
   *
   * @param payload ContentAccessor to SD which should be validated.
   * @return the result of the semantic validation.
   */
  SemanticValidationResult verifySelfDescriptionAgainstCompositeSchema(ContentAccessor payload);

}
