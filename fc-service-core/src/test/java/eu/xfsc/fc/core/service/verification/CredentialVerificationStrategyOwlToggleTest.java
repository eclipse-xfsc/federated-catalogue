package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import eu.xfsc.fc.core.config.VerificationStackTestConfig;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

/**
 * Pins the OWL module toggle. The toggle gates whether
 * {@link CredentialVerificationStrategy#resolveRole} consults the runtime composite
 * ontology built from {@link SchemaType#ONTOLOGY} rows.
 *
 * <p>Bundle-embedded ontologies are pre-walked into the registry tier-1 index at startup
 * (see {@code TrustFrameworkRegistry.indexBundle}), so credentials whose types are
 * subclasses of registered roots in the bundle ontology resolve via the registry regardless
 * of the OWL toggle. The toggle therefore only affects ontologies uploaded at runtime via
 * {@code schemaStore.addSchema(..., ONTOLOGY)} — those feed the runtime composite that
 * {@code resolveRole} fetches per request.
 *
 * <p>Test strategy: spy on {@link SchemaStoreImpl} and assert whether
 * {@code getCompositeSchema(ONTOLOGY)} is invoked, which is the precise observable effect
 * of the toggle gate. Outcome-level assertions (registry-direct types keep resolving) are
 * covered by {@link RoleResolutionCharacterisationTest}; null-tolerance of the downstream
 * {@code ClaimValidator.resolveSubjectRole} is covered by {@link LoireTypeResolutionTest}.
 *
 * <p><b>Fixture note:</b> the {@code participantCredential2.jsonld}, {@code
 * customExtParticipant.jsonld}, and {@code gx-2511-test-ontology.ttl} fixtures used below
 * are a snapshot of the current default bundle (Gaia-X 2511). The custom-subclass test
 * relies on {@code ext:CustomParticipant rdfs:subClassOf gx:LegalPerson} being a subclass
 * chain that ultimately lands under a registered role in the active bundle. If the
 * default bundle ever changes, swap the fixtures for ones rooted under whatever
 * registry-direct type the new bundle declares.
 */
@SpringBootTest(properties = {
    "federated-catalogue.verification.signature-verifier=uni-res",
    "federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
    "federated-catalogue.verification.vc-signature=false",
    "federated-catalogue.verification.vp-signature=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {CredentialVerificationStrategyOwlToggleTest.TestApplication.class,
    VerificationStackTestConfig.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
class CredentialVerificationStrategyOwlToggleTest {

  private static final String PARTICIPANT_FIXTURE =
      "VerificationService/syntax/participantCredential2.jsonld";

  // Credential typed `ext:CustomParticipant`, which is declared in
  // `custom-participant-extension.ttl` as `rdfs:subClassOf gx:LegalPerson` — i.e. the type
  // is only reachable via the runtime composite ontology, not via the bundle's tier-1 index.
  // With OWL on the credential resolves; with OWL off it does not.
  private static final String CUSTOM_PARTICIPANT_FIXTURE =
      "VerificationService/syntax/customExtParticipant.jsonld";
  private static final String CUSTOM_PARTICIPANT_ONTOLOGY =
      "Schema-Tests/custom-participant-extension.ttl";

  @SpringBootApplication
  public static class TestApplication {
    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private VerificationServiceImpl verificationService;

  @MockitoSpyBean
  private SchemaStoreImpl schemaStore;

  @MockitoBean
  private SchemaModuleConfigService schemaModuleConfigService;

  @MockitoBean
  private JwtSignatureVerifier jwtSignatureVerifier;

  @BeforeEach
  void seedRuntimeOntology() {
    // Default: every toggle is enabled. Each test overrides the OWL toggle locally.
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.SHACL)).thenReturn(true);
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(true);
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
  }

  @AfterEach
  void clear() {
    schemaStore.clear();
  }

  @Test
  void verifyCredential_owlEnabled_consultsCompositeOntology() {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(true);

    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor(PARTICIPANT_FIXTURE), false, false, false, false);

    assertNotNull(result);
    verify(schemaStore, atLeastOnce()).getCompositeSchema(SchemaType.ONTOLOGY);
  }

  @Test
  void verifyCredential_owlDisabled_skipsCompositeOntology() {
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(false);

    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor(PARTICIPANT_FIXTURE), false, false, false, false);

    assertNotNull(result);
    verify(schemaStore, never()).getCompositeSchema(SchemaType.ONTOLOGY);
  }

  @Test
  void verifyCredential_owlDisabled_verifySemanticsTrue_stillSkipsComposite() {
    // The toggle is independent of the caller's verifySemantics flag — type dispatch
    // is a configuration of the catalogue's type system, not a per-request check.
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(false);

    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor(PARTICIPANT_FIXTURE), true, false, false, false);

    assertNotNull(result);
    verify(schemaStore, never()).getCompositeSchema(SchemaType.ONTOLOGY);
  }

  @Test
  void verifyCredential_owlEnabled_customSubclassFromRuntimeOntology_resolvesRole() {
    schemaStore.addSchema(getAccessor(CUSTOM_PARTICIPANT_ONTOLOGY));
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(true);

    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor(CUSTOM_PARTICIPANT_FIXTURE), false, false, false, false);

    assertNotNull(result);
    assertEquals("Participant", result.getRole(),
        "ext:CustomParticipant rdfs:subClassOf gx:LegalPerson → resolves to Participant role");
  }

  @Test
  void verifyCredential_owlDisabled_customSubclassFromRuntimeOntology_rejectedWithClientException() {
    schemaStore.addSchema(getAccessor(CUSTOM_PARTICIPANT_ONTOLOGY));
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(false);

    // Custom subclass is only reachable through the runtime composite; with OWL off the
    // strategy returns role=null and VerificationServiceImpl rejects the credential with
    // ClientException (→ HTTP 400). Mirrors the user-visible effect of the OWL kill-switch.
    ClientException ex = assertThrows(ClientException.class,
        () -> verificationService.verifyCredential(
            getAccessor(CUSTOM_PARTICIPANT_FIXTURE), false, false, false, false));
    assertNotNull(ex.getMessage());
  }

  @Test
  void verifyCredential_owlDisabled_registryDirectType_stillResolves() {
    // Control case: framework-direct types (gx:Participant) keep resolving via the
    // tier-1 registry index regardless of the OWL toggle. Only runtime-uploaded
    // subclass-only types are rejected.
    when(schemaModuleConfigService.isModuleEnabled(SchemaModuleType.OWL)).thenReturn(false);

    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor(PARTICIPANT_FIXTURE), false, false, false, false);

    assertNotNull(result);
    assertNotNull(result.getRole(), "registry-direct type resolves regardless of OWL toggle");
  }
}
