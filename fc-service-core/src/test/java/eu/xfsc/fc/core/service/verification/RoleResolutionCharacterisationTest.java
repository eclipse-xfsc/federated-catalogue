package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

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

import eu.xfsc.fc.core.config.VerificationStackTestConfig;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultParticipant;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultResource;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

/**
 * Characterisation tests pinning role-resolution behaviour of {@link VerificationServiceImpl}.
 *
 * <p>Each test asserts the observable outcome (result type and non-empty claims) when a VP is submitted
 * to the verification service.
 *
 * <p>Signature verification is mocked; trust framework verification is disabled. Scope: role resolution only.
 */
@Slf4j
@SpringBootTest(properties = {
    "federated-catalogue.verification.signature-verifier=uni-res",
    "federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
    "federated-catalogue.verification.trust-framework.gaiax.enabled=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {RoleResolutionCharacterisationTest.TestApplication.class,
    VerificationStackTestConfig.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public class RoleResolutionCharacterisationTest {

    private static final boolean SKIP_SEMANTICS = false;
    private static final boolean SKIP_SCHEMA = false;
    private static final boolean SKIP_VP_SIGNATURES = false;
    private static final boolean SKIP_VC_SIGNATURES = false;

    @SpringBootApplication
    public static class TestApplication {

        public static void main(final String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @Autowired
    private VerificationServiceImpl verificationService;

    @Autowired
    private SchemaStoreImpl schemaStore;

    @MockitoBean
    private JwtSignatureVerifier jwtSignatureVerifier;

    @BeforeEach
    void setUp() {
        schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    }

    @AfterEach
    void tearDown() {
        schemaStore.clear();
    }

    @Test
    void verifyParticipant_legalPersonVp_returnsParticipantResultWithClaims() {
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor("VerificationService/syntax/participantCredential2.jsonld"),
            SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
        assertInstanceOf(CredentialVerificationResultParticipant.class, result,
            "gx:LegalPerson (subclass of gx:Participant) must resolve to PARTICIPANT result type");
        assertNotNull(result.getClaims());
        assertFalse(result.getClaims().isEmpty(), "Participant credential must produce non-empty claims");
    }

    @Test
    void verifyOffering_serviceOfferingVp_returnsOfferingResultWithClaims() {
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor("VerificationService/syntax/serviceOffering1.jsonld"),
            SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
        assertInstanceOf(CredentialVerificationResultOffering.class, result,
            "gx:ServiceOffering must resolve to SERVICE_OFFERING result type");
        assertNotNull(result.getClaims());
        assertFalse(result.getClaims().isEmpty(), "ServiceOffering credential must produce non-empty claims");
    }

    @Test
    void verifyOffering_digitalServiceOfferingVp_returnsOfferingResultWithClaims() {
      // gx:DigitalServiceOffering is not an OWL subclass of gx:ServiceOffering in gx-2511.
      // It is covered via additionalRoots in framework.yaml — the correct long-term mechanism.
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor("CharacterisationTests/digitalServiceOffering.jsonld"),
            SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
        assertInstanceOf(CredentialVerificationResultOffering.class, result,
            "gx:DigitalServiceOffering must resolve to SERVICE_OFFERING result type (gx-2511 edge case)");
        assertNotNull(result.getClaims());
        assertFalse(result.getClaims().isEmpty(), "DigitalServiceOffering credential must produce non-empty claims");
    }

    @Test
    void verifyResource_resourceVp_returnsResourceResultWithClaims() {
        // gx:VirtualResource rdfs:subClassOf gx:Resource — exercises the OWL subclass walk for RESOURCE role
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor("CharacterisationTests/resourceCredential.jsonld"),
            SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
        assertInstanceOf(CredentialVerificationResultResource.class, result,
            "gx:Resource must resolve to RESOURCE result type");
        assertNotNull(result.getClaims());
        assertFalse(result.getClaims().isEmpty(), "Resource credential must produce non-empty claims");
    }

    @Test
    void verifyParticipant_serviceOfferingVp_throwsVerificationException() {
        assertThrowsExactly(VerificationException.class,
            () -> verificationService.verifyParticipantCredential(
                getAccessor("VerificationService/syntax/serviceOffering1.jsonld")),
            "Expected type mismatch rejection when SERVICE_OFFERING VP is submitted as PARTICIPANT");
    }

    @Test
    void verifyCredential_unknownTypePresentInVp_returnsBaseResult() {
        // Inline VP: credentialSubject type is ex:CustomEntity — not in any Gaia-X hierarchy.
        ContentAccessorDirect vp = new ContentAccessorDirect("""
            {
              "@context": ["https://www.w3.org/ns/credentials/v2"],
              "type": ["VerifiablePresentation"],
              "verifiableCredential": {
                "@context": [
                  "https://www.w3.org/ns/credentials/v2",
                  {"ex": "https://example.org/custom#", "xsd": "http://www.w3.org/2001/XMLSchema#"}
                ],
                "@type": ["VerifiableCredential"],
                "issuer": "did:web:example.com",
                "validFrom": "2024-01-01T00:00:00Z",
                "credentialSubject": {
                  "@id": "https://example.org/subject1",
                  "@type": "ex:CustomEntity"
                }
              }
            }
            """);

        CredentialVerificationResult result = verificationService.verifyCredential(
            vp, SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
        assertEquals(CredentialVerificationResult.class, result.getClass(),
            "Non-Gaia-X credentialSubject type must resolve to base CredentialVerificationResult, not a typed subclass");
    }
}
