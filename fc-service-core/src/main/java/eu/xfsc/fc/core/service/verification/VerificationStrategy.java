package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.VerificationResult;

import java.util.List;

/**
 * Strategy interface for Self-Description verification and data extraction.
 * Implementations encapsulate the specific parsing, semantic validation,
 * schema validation, signature verification, and claim extraction logic.
 */
public interface VerificationStrategy {

  /**
   * Verifies a Self-Description payload according to the implementation's logic.
   *
   * @param payload            the Self-Description content to verify
   * @param strict             whether strict mode is enabled (typed endpoints)
   * @param expectedClass      the expected Trust Framework base class, or UNKNOWN
   * @param verifySemantics    whether to perform semantic verification
   * @param verifySchema       whether to perform schema verification
   * @param verifyVPSignatures whether to verify VP signatures
   * @param verifyVCSignatures whether to verify VC signatures
   * @return the verification result
   * @throws VerificationException if verification fails
   */
  VerificationResult verifySelfDescription(ContentAccessor payload, boolean strict, TrustFrameworkBaseClass expectedClass,
      boolean verifySemantics, boolean verifySchema, boolean verifyVPSignatures,
      boolean verifyVCSignatures) throws VerificationException;

  /**
   * Extracts claims from the given payload without performing verification.
   *
   * @param payload the Self-Description content to extract claims from
   * @return the list of extracted claims
   */
  List<SdClaim> extractClaims(ContentAccessor payload);

  /**
   * Override URI for one of the Trust Framework base classes.
   *
   * @param baseClass the base class to override
   * @param uri       the new URI
   */
  void setBaseClassUri(TrustFrameworkBaseClass baseClass, String uri);

}
