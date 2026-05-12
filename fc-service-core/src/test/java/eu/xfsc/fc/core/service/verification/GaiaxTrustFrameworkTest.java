package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;

import java.io.IOException;

import eu.xfsc.fc.core.util.TestUtil;
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

import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.core.config.VerificationStackTestConfig;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

/**
 * Test class for configurable Gaia-X Trust Framework validation.*
 * These tests verify that:
 * - Assets can be uploaded without Gaia-X Compliance validation
 * - Gaia-X validation is configurable via gaiax.enabled property
 * - No mandatory Gaia-X validation when disabled
 * - Backward compatibility - Gaia-X validation works when enabled
 * Tests use programmatic toggling of the gaiax.enabled flag to verify
 * behavior changes with the same Spring context.
 */
@Slf4j
@SpringBootTest(properties = {
    "federated-catalogue.verification.signature-verifier=uni-res",
    "federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0"
    // Framework defaults: DB seed has gaia-x enabled=false; tests toggle via setFrameworkEnabled().
    // Trust anchor URL is sourced from the bundle's framework.yaml properties map.
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {GaiaxTrustFrameworkTest.TestApplication.class, VerificationStackTestConfig.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public class GaiaxTrustFrameworkTest {

    // Verification flags for readability
    private static final boolean VERIFY_SEMANTICS = true;
    private static final boolean VERIFY_SCHEMA = true;
    private static final boolean VERIFY_VP_SIGNATURES = true;
    private static final boolean VERIFY_VC_SIGNATURES = true;
    private static final boolean SKIP_SEMANTICS = false;
    private static final boolean SKIP_SCHEMA = false;
    private static final boolean SKIP_VP_SIGNATURES = false;
    private static final boolean SKIP_VC_SIGNATURES = false;

    private static final String DID_WEB_ISSUER = "did:web:example.com";
    private static final String VALIDATOR_KID = DID_WEB_ISSUER + "#key-1";
    /** JWK without x5c/x5u — enforceLoireTrustChain rejects when Gaia-X enabled. */
    private static final String JWK_NO_TRUST_CHAIN = "{\"kty\":\"EC\",\"crv\":\"P-256\"}";

    @SpringBootApplication
    public static class TestApplication {
        public static void main(final String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @Autowired
    private SchemaStoreImpl schemaStore;

    @Autowired
    private VerificationServiceImpl verificationService;

    @Autowired
    private CredentialVerificationStrategy credentialStrategy;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JwtSignatureVerifier jwtVerifierMock;

    @BeforeEach
    public void setUp() {
        clearValidatorCache();
        schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Reset to default (matches @SpringBootTest property)
      setFrameworkEnabled(GAIA_X_FAMILY, false);
        clearValidatorCache();
        schemaStore.clear();
    }

    /**
     * Clears the validator cache database table.
     * This is necessary because cached validators bypass the trust anchor check,
     * which would cause tests to pass/fail incorrectly when toggling gaiax.enabled.
     */
    private void clearValidatorCache() {
        jdbcTemplate.update("DELETE FROM validatorcache");
    }

    /**
     * Family identifier of the trust framework these tests exercise.
     * Matches the {@code family} field in {@code trustframeworks/gaia-x-2511/framework.yaml}.
     */
    private static final String GAIA_X_FAMILY = "gaia-x";

  /**
   * Toggles a trust framework's enabled state in the DB by family ID.
   */
  private void setFrameworkEnabled(String family, boolean enabled) {
        jdbcTemplate.update(
            "UPDATE trust_frameworks SET enabled = ? WHERE id = ?", enabled, family);
  }

    // ==================== Disabled Behavior Tests ====================

    @Test
    @DisplayName("Loire JWT without x5u URL should be ACCEPTED when Gaia-X is disabled")
    void testLoireJwtWithoutX5u_AcceptedWhenDisabled() {
      setFrameworkEnabled(GAIA_X_FAMILY, false);
      ContentAccessor content = loireJwtWithoutTrustChain();

        try {
            CredentialVerificationResult result = verificationService.verifyCredential(
                content, SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, VERIFY_VC_SIGNATURES);
            // Success - trust anchor check was skipped
            assertNotNull(result, "Should return result when Gaia-X is disabled");
        } catch (ClientException e) {
          assertTrue(e.getMessage().contains("not resolvable"),
              "ClientException must be unresolvable-type rejection, got: " + e.getMessage());
        } catch (VerificationException e) {
            // Should NOT get trust anchor error when Gaia-X is disabled
            assertFalse(e.getMessage().contains("must contain x5c or x5u"),
                "Should NOT require trust chain when Gaia-X is disabled. Got: " + e.getMessage());
            // Other errors (like DID resolution) are acceptable
            log.info("Got acceptable non-trust-anchor error: {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("Non-Gaia-X credential claims should be extractable")
    void testClaimsExtractable_WhenDisabled() {
      setFrameworkEnabled(GAIA_X_FAMILY, false);

      String path = "VerificationService/syntax/participantCredential2.jsonld";

        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor(path), VERIFY_SEMANTICS, VERIFY_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result, "Should return result");
      assertEquals("Participant", result.getRole(), "Should have Participant role");
      assertNotNull(result.getGraphClaims(), "Claims should be extracted");
      assertFalse(result.getGraphClaims().isEmpty(), "Should have claims");
    }

    // ==================== Enabled Behavior Tests ====================

    @Test
    @DisplayName("Loire credential without x5u URL should be REJECTED when Gaia-X is enabled")
    void testCredentialWithoutX5u_RejectedWhenEnabled() {
      setFrameworkEnabled(GAIA_X_FAMILY, true);
      ContentAccessor content = loireJwtWithoutTrustChain();

        Exception ex = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(content,
                SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, VERIFY_VC_SIGNATURES));

        assertTrue(ex.getMessage().contains("must contain x5c or x5u"),
            "Should require trust chain when Gaia-X is enabled. Got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Gaia-X compliant credential should still work when enabled")
    void testGaiaxCredentialWorks_WhenEnabled() {
      setFrameworkEnabled(GAIA_X_FAMILY, true);

      String path = "VerificationService/syntax/participantCredential2.jsonld";

        // Without signature verification, this should work
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor(path), VERIFY_SEMANTICS, VERIFY_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result, "Should process Gaia-X credential");
      assertEquals("Participant", result.getRole());
    }

    // ==================== Behavioral Toggle Tests ====================

    @Test
    @DisplayName("Same Loire credential should have DIFFERENT outcomes based on Gaia-X setting")
    void testSameCredentialDifferentOutcomes() {
        ContentAccessor content = loireJwtWithoutTrustChain();

        // --- Test with Gaia-X ENABLED: should REJECT (missing trust chain) ---
        clearValidatorCache();
      setFrameworkEnabled(GAIA_X_FAMILY, true);

      Exception enabledEx = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(content,
                SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, VERIFY_VC_SIGNATURES),
            "Should throw when Gaia-X is enabled");
        assertTrue(enabledEx.getMessage().contains("must contain x5c or x5u"),
            "Error should mention trust chain when enabled. Got: " + enabledEx.getMessage());

        // --- Test with Gaia-X DISABLED: should NOT get trust chain error ---
        clearValidatorCache();
      setFrameworkEnabled(GAIA_X_FAMILY, false);

      try {
            verificationService.verifyCredential(content,
                SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, VERIFY_VC_SIGNATURES);
        } catch (ClientException e) {
          assertTrue(e.getMessage().contains("not resolvable"),
              "ClientException must be unresolvable-type rejection, got: " + e.getMessage());
        } catch (VerificationException e) {
            assertFalse(e.getMessage().contains("must contain x5c or x5u"),
                "Should NOT require trust chain when disabled. Got: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Behavior should change when toggling Gaia-X at runtime")
    void testBehaviorChangesWhenToggling() {
        ContentAccessor content = loireJwtWithoutTrustChain();

        // --- First: DISABLE Gaia-X, verify no trust chain error ---
        clearValidatorCache();
      setFrameworkEnabled(GAIA_X_FAMILY, false);

      try {
            verificationService.verifyCredential(content,
                SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, VERIFY_VC_SIGNATURES);
        } catch (ClientException e) {
          assertTrue(e.getMessage().contains("not resolvable"),
              "ClientException must be unresolvable-type rejection, got: " + e.getMessage());
        } catch (VerificationException e) {
            if (e.getMessage().contains("must contain x5c or x5u")) {
                fail("Should NOT require trust chain when Gaia-X is disabled. Got: " + e.getMessage());
            }
            // Other errors are OK
        }

        // --- Then: ENABLE Gaia-X, verify trust chain error occurs ---
        clearValidatorCache();
      setFrameworkEnabled(GAIA_X_FAMILY, true);

      Exception ex = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(content,
                SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, VERIFY_VC_SIGNATURES),
            "Should throw when Gaia-X is enabled");
        assertTrue(ex.getMessage().contains("must contain x5c or x5u"),
            "Should require trust chain when Gaia-X is enabled. Got: " + ex.getMessage());
    }

    // ==================== Security Tests ====================

    @Test
    @DisplayName("Loire JWT without trust chain should still be accepted when Gaia-X is disabled")
    void testLoireJwtWithoutTrustChain_AcceptedWhenDisabled() {
      setFrameworkEnabled(GAIA_X_FAMILY, false);
      ContentAccessor content = loireJwtWithoutTrustChain();

        try {
            verificationService.verifyCredential(content,
                SKIP_SEMANTICS, SKIP_SCHEMA, SKIP_VP_SIGNATURES, VERIFY_VC_SIGNATURES);
        } catch (ClientException e) {
          assertTrue(e.getMessage().contains("not resolvable"),
              "ClientException must be unresolvable-type rejection, got: " + e.getMessage());
        } catch (VerificationException e) {
            assertFalse(e.getMessage().contains("must contain x5c or x5u"),
                "Should NOT require trust chain when Gaia-X is disabled. Got: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Non-JWT credential with signature verification should be rejected")
    void testLdCredentialWithSigVerification_Rejected() {
      setFrameworkEnabled(GAIA_X_FAMILY, false);
      String path = "VerificationService/sign/hasNoSignature1.json";

        Exception ex = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(getAccessor(path),
                SKIP_SEMANTICS, SKIP_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES));

        assertTrue(ex.getMessage().contains("Linked Data proof verification is not supported"),
            "Should reject LD credential. Got: " + ex.getMessage());
    }

    // ==================== Loire JWT Helpers ====================

    /**
     * Creates a Loire JWT fixture with a JWK that lacks x5c/x5u trust chain.
     * Configures the mock JWT verifier to return a validator with the bare JWK.
     */
    private ContentAccessor loireJwtWithoutTrustChain() {
        when(jwtVerifierMock.verify(any()))
            .thenReturn(new Validator(VALIDATOR_KID, JWK_NO_TRUST_CHAIN, null));
        return new ContentAccessorDirect(TestUtil.fakeLoireJwt(DID_WEB_ISSUER));
    }
}
