package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;


/**
 * Strategy interface for credential verification.
 * Implementations encapsulate format-specific parsing, semantic validation,
 * schema validation, signature verification, and claim extraction logic.
 *
 * <p>Current implementations:
 * <ul>
 *   <li>{@link CredentialVerificationStrategy} - JSON-LD W3C VC/VP with signatures</li>
 * </ul>
 */
public interface VerificationStrategy {

  /**
   * Verifies a credential payload according to the implementation's logic.
   *
   * <p>All implementations of that method must apply namespace filtering according to
   * requirement CAT-FR-GD-09 as specified in
   * https://github.com/eclipse-xfsc/docs/blob/f3c6e6b6fbcc87732a1dfe83f060fa58a9a97873/federated-catalogue/src/docs/CAT%20Enhancement/CAT_Enhancement_Specifications%20v1.0.pdf
   * before returning claims.</p>
   *
   * @param payload            the credential content to verify
   * @param strict             whether strict mode is enabled (typed endpoints)
   * @param expectedRole       the expected trust-framework role name, or empty/null for no expectation
   * @param verifySemantics    whether to perform semantic verification
   * @param verifySchema       whether to perform schema verification
   * @param verifyVPSignatures whether to verify VP signatures
   * @param verifyVCSignatures whether to verify VC signatures
   * @return the verification result
   * @throws VerificationException if verification fails
   */
  CredentialVerificationResult verifyCredential(ContentAccessor payload, boolean strict, String expectedRole,
      boolean verifySemantics, boolean verifySchema, boolean verifyVPSignatures,
      boolean verifyVCSignatures) throws VerificationException;

}
