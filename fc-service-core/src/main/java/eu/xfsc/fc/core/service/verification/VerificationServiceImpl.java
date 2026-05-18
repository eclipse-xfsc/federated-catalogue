package eu.xfsc.fc.core.service.verification;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.NonCredentialVerificationResult;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
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

  @Autowired
  private TrustFrameworkRegistry trustFrameworkRegistry;

  /** Package-private for testing: allows overriding the schema verification toggle. */
  void setVerifySchema(boolean verifySchema) {
    this.verifySchema = verifySchema;
  }

  private VerificationStrategy resolveStrategy(ContentAccessor payload) {
    return credentialStrategy;
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
    CredentialVerificationResult result = resolveStrategy(payload).verifyCredential(payload,
        verifySemantics, verifySchema, verifyVPSignatures, verifyVCSignatures);
    if (!(result instanceof NonCredentialVerificationResult) && result.getRole() == null) {
      String bundleInfo = getActiveTrustFrameworkBundleInfos();
      throw new ClientException(
          "Credential type is not resolvable in any active trust-framework bundle."
              + " Active bundles: [" + bundleInfo + "]");
    }
    return result;
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

  private String getActiveTrustFrameworkBundleInfos() {
    return trustFrameworkRegistry.getActiveBundles().stream()
        .map(b -> b.config().id() + "=" + b.config().roles().keySet())
        .collect(Collectors.joining(", "));
  }
}
