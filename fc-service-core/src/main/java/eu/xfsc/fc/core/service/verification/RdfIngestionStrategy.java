package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;


/**
 * Strategy interface for verifying ingested payloads. Implementations encapsulate the
 * end-to-end pipeline for a payload class — parsing, validation, claim extraction, and
 * result assembly.
 *
 * <p>Implementations:
 * <ul>
 *   <li>{@link CredentialIngestionStrategy} — W3C VC/VP (JSON-LD or JWT, incl. Loire
 *       and W3C VC 2.0 Enveloped wrappers) with full semantic/schema/signature checks.</li>
 *   <li>{@link NonCredentialIngestionStrategy} — non-credential RDF payloads ingested as raw
 *       triples (no VC pipeline).</li>
 * </ul>
 *
 * <p>{@link VerificationServiceImpl} picks the implementation per payload.
 */
public interface RdfIngestionStrategy {

  /**
   * Ingests an RDF asset according to the implementation's logic.
   *
   * <p>All implementations of that method must apply namespace filtering according to
   * requirement CAT-FR-GD-09 as specified in
   * {@link <a href=https://github.com/eclipse-xfsc/docs/blob/f3c6e6b6fbcc87732a1dfe83f060fa58a9a97873/federated-catalogue/src/docs/CAT%20Enhancement/CAT_Enhancement_Specifications%20v1.0.pdf>the FCE SRS</a>}
   * before returning claims.</p>
   *
   * @param payload            the credential content to verify
   * @param verifySemantics    whether to perform semantic verification
   * @param verifyVPSignatures whether to verify VP signatures
   * @param verifyVCSignatures whether to verify VC signatures
   * @param requireBaseClass   when {@code true}, reject a credential whose subject does not
   *                           resolve to a known trust-framework base class; when {@code false}
   *                           (the credential-upload default) the credential is accepted even if
   *                           no base class resolves
   * @return the verification result
   * @throws VerificationException if verification fails
   */
  CredentialVerificationResult ingest(ContentAccessor payload,
                                      boolean verifySemantics,
                                      boolean verifyVPSignatures,
                                      boolean verifyVCSignatures,
                                      boolean requireBaseClass) throws VerificationException;

}
