package eu.xfsc.fc.core.service.verification;

import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.NonCredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import lombok.extern.slf4j.Slf4j;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.JWT_PREFIX;


/**
 * Implementation of the {@link VerificationService} interface.
 *
 * <p>Dispatches each incoming payload to one of two {@link RdfIngestionStrategy}
 * implementations based on a quick format peek:
 * <ul>
 *   <li>{@link CredentialVerificationStrategy} — for any recognised credential format
 *       (Loire JWT, danubetech VC 2.0 JSON-LD or JWT, W3C VC 2.0 Enveloped wrappers) and
 *       for ambiguous JWTs (which it rejects with a clear error).</li>
 *   <li>{@link NonCredentialIngestionStrategy} — for non-credential JSON-LD or other RDF
 *       payloads the catalogue ingests as raw triples.</li>
 * </ul>
 *
 * <p>Per-credential-family dispatch (Loire vs. VC 2.0 etc.) happens inside the credential
 * strategy via {@link CredentialFormatDetector} and the {@link CredentialFormatProcessor}
 * chain. Structural validation of stored assets against stored schemas is the
 * responsibility of {@code AssetValidationService}, not this service.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class VerificationServiceImpl implements VerificationService {

  @Value("${federated-catalogue.verification.semantics:true}")
  private boolean verifySemantics;
  @Value("${federated-catalogue.verification.schema:false}")
  private boolean verifySchema;
  @Value("${federated-catalogue.verification.vp-signature:true}")
  private boolean verifyVPSignature;
  @Value("${federated-catalogue.verification.vc-signature:true}")
  private boolean verifyVCSignature;

  private final CredentialVerificationStrategy credentialStrategy;
  private final NonCredentialIngestionStrategy nonCredentialStrategy;
  private final CredentialFormatDetector formatDetector;
  private final TrustFrameworkRegistry trustFrameworkRegistry;

  /** Package-private for testing: allows overriding the schema verification toggle. */
  void setVerifySchema(boolean verifySchema) {
    this.verifySchema = verifySchema;
  }

  /**
   * Picks the strategy for this payload. JWT bodies always go to the credential strategy
   * (it handles both recognised and ambiguous JWTs); JSON-LD/RDF bodies are detected once
   * and routed to the non-credential strategy when no credential format matches.
   */
  private RdfIngestionStrategy resolveStrategy(ContentAccessor payload) {
    String body = payload.getContentAsString().strip();
    if (body.startsWith(JWT_PREFIX)) {
      return credentialStrategy;
    }
    return formatDetector.detect(payload) == CredentialFormat.UNKNOWN
        ? nonCredentialStrategy : credentialStrategy;
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
    CredentialVerificationResult result = resolveStrategy(payload).ingest(payload,
        verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
    if (!(result instanceof NonCredentialVerificationResult) && result.getRole() == null) {
      String bundleInfo = getActiveTrustFrameworkBundleInfos();
      throw new ClientException(
          "Credential type is not resolvable in any active trust-framework bundle."
              + " Active bundles: [" + bundleInfo + "]");
    }
    return result;
  }

  private String getActiveTrustFrameworkBundleInfos() {
    return trustFrameworkRegistry.getActiveBundles().stream()
        .map(b -> b.config().id() + "=" + b.config().roles().keySet())
        .collect(Collectors.joining(", "));
  }
}
