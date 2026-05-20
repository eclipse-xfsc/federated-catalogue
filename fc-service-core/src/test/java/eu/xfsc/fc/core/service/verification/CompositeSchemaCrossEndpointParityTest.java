package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import eu.xfsc.fc.core.config.VerificationStackTestConfig;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

/**
 * Pins the cross-endpoint conformance-verdict parity for the composite SHACL graph.
 *
 * <p>The same RDF credential is submitted through two converging code paths:
 * <ul>
 *   <li><b>On-demand validation path</b> — {@code SchemaValidationService.validateCredentialAgainstCompositeSchema}
 *       (this is what {@code POST /assets/validate} ultimately calls for SHACL via the
 *       {@code ShaclValidationStrategy} multi-asset entry).</li>
 *   <li><b>Credential verification path</b> — {@code VerificationService.verifyCredential(..., verifySchema=true, ...)}
 *       (this is what {@code POST /verification?verifySchema=true} calls; it extracts and
 *       filters claims before running the SHACL check, then throws
 *       {@code VerificationException} on conformance failure).</li>
 * </ul>
 *
 * <p>The two API shapes differ on failure — the validation path returns a structured
 * report with {@code conforms=false}, the verification path throws — but the underlying
 * conformance verdict must agree for the same input + same composite SHACL graph.
 *
 * <p>The protected-namespace claim filter on the verification path
 * ({@code CredentialVerificationStrategy.extractAndValidateClaims}) is a documented
 * asymmetry: it can drop claims before SHACL runs, which would let an otherwise
 * non-conforming credential pass through verification if the violation lived entirely in
 * a filtered claim. The fixtures here avoid that condition deliberately so the parity
 * check is meaningful.
 */
@SpringBootTest(properties = {
    "federated-catalogue.verification.signature-verifier=uni-res",
    "federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
    "federated-catalogue.verification.vc-signature=false",
    "federated-catalogue.verification.vp-signature=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {CompositeSchemaCrossEndpointParityTest.TestApplication.class,
    VerificationStackTestConfig.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
class CompositeSchemaCrossEndpointParityTest {

  private static final String ONTOLOGY = "Schema-Tests/gx-2511-test-ontology.ttl";
  private static final String SHACL_SHAPE = "Schema-Tests/gx-2511-test-shapes.ttl";
  private static final String CONFORMING_CREDENTIAL = "Validation-Tests/loire_legalPerson_valid.jsonld";
  private static final String NON_CONFORMING_CREDENTIAL =
      "Validation-Tests/loire_legalPerson_missing_required.jsonld";

  @SpringBootApplication
  public static class TestApplication {
    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private VerificationServiceImpl verificationService;

  @Autowired
  private SchemaValidationService schemaValidationService;

  @Autowired
  private SchemaStoreImpl schemaStore;

  @MockitoBean
  private JwtSignatureVerifier jwtSignatureVerifier;

  @BeforeEach
  void seedCompositeSchemas() {
    schemaStore.addSchema(getAccessor(ONTOLOGY));
    schemaStore.addSchema(getAccessor(SHACL_SHAPE));
  }

  @AfterEach
  void clearStore() {
    schemaStore.clear();
  }

  @Test
  @DisplayName("Conforming credential: both paths return success (verifies SHACL composite parity)")
  void conformingCredential_bothPathsAgreeOnSuccess() {
    ContentAccessor content = getAccessor(CONFORMING_CREDENTIAL);

    SchemaValidationResult validationPath =
        schemaValidationService.validateCredentialAgainstCompositeSchema(content);

    assertNotNull(validationPath);
    assertTrue(validationPath.isConforming(),
        "Conforming credential must pass the composite SHACL check on the validation path. "
            + "Report: " + validationPath.getValidationReport());

    CredentialVerificationResult verificationPath = verificationService.verifyCredential(
        content, true, true, false, false);

    assertNotNull(verificationPath,
        "Conforming credential must produce a result on the verification path with verifySchema=true");
  }

  @Test
  @DisplayName("Non-conforming credential: validation reports conforms=false; verification throws — verdicts agree")
  void nonConformingCredential_bothPathsAgreeOnFailure() {
    ContentAccessor content = getAccessor(NON_CONFORMING_CREDENTIAL);

    SchemaValidationResult validationPath =
        schemaValidationService.validateCredentialAgainstCompositeSchema(content);

    assertNotNull(validationPath);
    assertFalse(validationPath.isConforming(),
        "Non-conforming credential must fail the composite SHACL check on the validation path");

    VerificationException ex = assertThrows(VerificationException.class,
        () -> verificationService.verifyCredential(content, true, true, false, false),
        "Non-conforming credential must throw on the verification path with verifySchema=true");
    assertTrue(ex.getMessage().startsWith("Schema error:"),
        "Verification path failure must surface as a Schema error. Actual: " + ex.getMessage());
  }

  @Test
  @DisplayName("Same credential through both paths returns the same yes/no SHACL verdict")
  void parityHoldsAcrossBothFixtures() {
    // Sanity check: the two fixtures used above intentionally cover both verdicts so
    // a regression that quietly inverts conformance on one path (e.g. by changing claim
    // extraction or the composite-build path) gets caught.
    SchemaValidationResult conformingResult = schemaValidationService
        .validateCredentialAgainstCompositeSchema(getAccessor(CONFORMING_CREDENTIAL));
    SchemaValidationResult nonConformingResult = schemaValidationService
        .validateCredentialAgainstCompositeSchema(getAccessor(NON_CONFORMING_CREDENTIAL));

    assertEquals(true, conformingResult.isConforming());
    assertEquals(false, nonConformingResult.isConforming());
  }
}
