package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

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
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.Validator;
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
    // Disable signature verification for role-resolution tests.
    // Tests that require signature verification pass the flag explicitly via the verifyCredential overload.
    "federated-catalogue.verification.vc-signature=false",
    "federated-catalogue.verification.vp-signature=false"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {RoleResolutionCharacterisationTest.TestApplication.class,
    VerificationStackTestConfig.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public class RoleResolutionCharacterisationTest {

  private static final boolean VERIFY_SEMANTICS = true;
  private static final boolean VERIFY_VC_SIGNATURES = true;
    private static final boolean SKIP_SEMANTICS = false;
    private static final boolean SKIP_SCHEMA = false;
    private static final boolean SKIP_VP_SIGNATURES = false;
    private static final boolean SKIP_VC_SIGNATURES = false;

  private static final String PARTICIPANT_CREDENTIAL_SUBJECT_ID = "http://example.org/test-issuer";
  private static final String PARTICIPANT_ISSUER = "https://www.handelsregister.de/";
  private static final String PARTICIPANT_VP_HOLDER = "did:example:holder";
  private static final String OFFERING_ISSUER = "http://gaiax.de";
  private static final String VALIDATOR_KID = "did:web:example.com#key-1";
  private static final String JWK_EC = "{\"kty\":\"EC\",\"crv\":\"P-256\"}";

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
    void verifyParticipant_legalPersonVp_resolvesToParticipantRole() {
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor("VerificationService/syntax/participantCredential2.jsonld"),
            SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
      assertEquals(CredentialVerificationResult.class, result.getClass(),
          "gx:LegalPerson must produce a plain CredentialVerificationResult — no role-specific subclass");
      assertEquals("Participant", result.getRole());
      assertNotNull(result.getGraphClaims());
      assertFalse(result.getGraphClaims().isEmpty(), "Participant credential must produce non-empty claims");
    }

    @Test
    void verifyOffering_serviceOfferingVp_resolvesToServiceOfferingRole() {
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor("VerificationService/syntax/serviceOffering1.jsonld"),
            SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
      assertEquals(CredentialVerificationResult.class, result.getClass(),
          "gx:ServiceOffering must produce a plain CredentialVerificationResult — no role-specific subclass");
      assertEquals("ServiceOffering", result.getRole());
      assertNotNull(result.getGraphClaims());
      assertFalse(result.getGraphClaims().isEmpty(), "ServiceOffering credential must produce non-empty claims");
    }

    @Test
    void verifyOffering_digitalServiceOfferingVp_resolvesToServiceOfferingRole() {
      // gx:DigitalServiceOffering is not an OWL subclass of gx:ServiceOffering in gx-2511.
      // It is covered via additionalRoots in framework.yaml — the correct long-term mechanism.
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor("CharacterisationTests/digitalServiceOffering.jsonld"),
            SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
      assertEquals(CredentialVerificationResult.class, result.getClass(),
          "gx:DigitalServiceOffering must produce a plain CredentialVerificationResult (gx-2511 additional_roots edge case)");
      assertEquals("ServiceOffering", result.getRole());
      assertNotNull(result.getGraphClaims());
      assertFalse(result.getGraphClaims().isEmpty(), "DigitalServiceOffering credential must produce non-empty claims");
    }

    @Test
    void verifyResource_resourceVp_resolvesToResourceRole() {
      // gx:VirtualResource rdfs:subClassOf gx:Resource — exercises the OWL subclass walk
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor("CharacterisationTests/resourceCredential.jsonld"),
            SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result);
      assertEquals(CredentialVerificationResult.class, result.getClass(),
          "gx:Resource must produce a plain CredentialVerificationResult — no role-specific subclass");
      assertEquals("Resource", result.getRole());
      assertNotNull(result.getGraphClaims());
      assertFalse(result.getGraphClaims().isEmpty(), "Resource credential must produce non-empty claims");
    }

  @Test
  void verifyParticipant_nameAndPublicKeyPopulatedInGenericResult() {
    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor("VerificationService/syntax/participantCredential2.jsonld"),
        SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

    assertNotNull(result);
    assertNotNull(result.getName(), "name must be populated in the generic result");
    // No validators in JSON-LD no-signature path → publicKey is null; JWT path covered by
    // verifyParticipant_jwtWithValidator_publicKeyEqualsFirstValidatorDid
    assertNull(result.getPublicKey(), "publicKey is null when no validators present");
  }

  @Test
  void verifyParticipant_idEqualsCredentialSubjectId_notIssuer() {
    CredentialVerificationResult result = verificationService.verifyCredential(
        getAccessor("VerificationService/syntax/participantCredential2.jsonld"),
        SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

    assertEquals(PARTICIPANT_CREDENTIAL_SUBJECT_ID, result.getId(),
        "id must be credentialSubjectId, not issuer");
    assertNotEquals(PARTICIPANT_ISSUER, result.getId(),
        "id must NOT be the issuer (AC-4: assembleResult uses credentialSubjectId for all roles)");
    assertEquals(PARTICIPANT_ISSUER, result.getIssuer(),
        "issuer field must still carry the credential issuer");
    assertEquals(PARTICIPANT_VP_HOLDER, result.getName(),
        "name must come from VP holder when present");
  }

  @Test
  void resolvePrimaryRole_vpWithParticipantAndResourceTypes_throwsVerificationExceptionWhenSemanticsEnabled() {
    // Verifies the widened semantics check: old code only threw for Participant+ServiceOffering;
    // new distinctRoles>1 check fires for any two distinct roles (e.g. Participant+Resource).
    ContentAccessorDirect vp = new ContentAccessorDirect("""
        {
          "@context": ["https://www.w3.org/ns/credentials/v2"],
          "type": ["VerifiablePresentation"],
          "verifiableCredential": [
            {
              "@context": ["https://www.w3.org/ns/credentials/v2"],
              "@id": "https://example.org/vc/1",
              "type": ["VerifiableCredential"],
              "issuer": "did:web:example.com",
              "validFrom": "2024-01-01T00:00:00Z",
              "credentialSubject": {
                "id": "https://example.org/participant1",
                "@type": "gx:LegalPerson",
                "@context": {"gx": "https://w3id.org/gaia-x/2511#"}
              }
            },
            {
              "@context": ["https://www.w3.org/ns/credentials/v2"],
              "@id": "https://example.org/vc/2",
              "type": ["VerifiableCredential"],
              "issuer": "did:web:example.com",
              "validFrom": "2024-01-01T00:00:00Z",
              "credentialSubject": {
                "id": "https://example.org/resource1",
                "@type": "gx:VirtualResource",
                "@context": {"gx": "https://w3id.org/gaia-x/2511#"}
              }
            }
          ]
        }
        """);

    Exception ex = assertThrowsExactly(VerificationException.class,
        () -> verificationService.verifyCredential(vp, VERIFY_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES,
            SKIP_VC_SIGNATURES),
        "VP with Participant+Resource types must be rejected when semantics verification is enabled");
    assertNotNull(ex.getMessage());
  }

  @Test
  void verifyCredential_serviceOffering_nameAndPublicKeyPropagatedToResult() {
    CredentialVerificationResult result =
        verificationService.verifyCredential(getAccessor("VerificationService/syntax/serviceOffering1.jsonld"));

    assertNotNull(result.getName(), "name must be propagated to the result");
    assertEquals(OFFERING_ISSUER, result.getName(),
        "name falls back to issuer when no VP holder is present");
    // Patch 4: effectiveIssuer applied for all roles; issuer is preserved when non-null
    assertEquals(OFFERING_ISSUER, result.getIssuer(),
        "issuer must be preserved (effectiveIssuer fallback does not override non-null issuer)");
    // No validators in JSON-LD no-signature path
    assertNull(result.getPublicKey(), "publicKey is null when no validators present");
  }

  @Test
  void verifyParticipant_jwtWithValidator_publicKeyEqualsFirstValidatorDid() {
    // Verifies the JWT path: when the mock verifier returns a Validator, assembleResult must
    // populate publicKey from the first validator's DID URI.
    when(jwtSignatureVerifier.verify(any()))
        .thenReturn(new Validator(VALIDATOR_KID, JWK_EC, null));

    ContentAccessorDirect jwt = new ContentAccessorDirect(fakeParticipantLoireJwt("did:web:example.com"));

    CredentialVerificationResult result = verificationService.verifyCredential(
        jwt, SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, VERIFY_VC_SIGNATURES);

    assertEquals("Participant", result.getRole(),
        "gx:LegalPerson in JWT credentialSubject must resolve to Participant role");
    assertEquals(VALIDATOR_KID, result.getPublicKey(),
        "publicKey must equal the DID URI of the first validator from the JWT verifier");
  }

  /**
   * Builds a fake Loire JWT (typ=vc+jwt) with a gx:LegalPerson credentialSubject
   * so that role resolution produces the Participant role.
   */
  private static String fakeParticipantLoireJwt(String iss) {
    var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString(
        "{\"alg\":\"RS256\",\"typ\":\"vc+jwt\",\"cty\":\"vc\"}".getBytes(StandardCharsets.UTF_8));
    String payloadJson = """
        {"iss":"%s","@context":["https://www.w3.org/ns/credentials/v2"],\
        "type":["VerifiableCredential"],\
        "issuer":"%s",\
        "credentialSubject":{"id":"%s","@type":"gx:LegalPerson",\
        "@context":{"gx":"https://w3id.org/gaia-x/2511#"}}}""".formatted(iss, iss, iss);
    String payload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".AAAA";
  }

    @Test
    void verifyCredential_unknownTypePresentInVp_throwsClientException() {
      // credentialSubject type is ex:CustomEntity — not in any active bundle hierarchy.
      // resolveSubjectRole returns UNKNOWN → ClientException (400).
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

      assertThrowsExactly(ClientException.class,
          () -> verificationService.verifyCredential(
              vp, SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES));
    }
}
