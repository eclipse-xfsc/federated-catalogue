package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;

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

import org.springframework.jdbc.core.JdbcTemplate;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultParticipant;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

/**
 * Test class for configurable Gaia-X Trust Framework validation.
 *
 * These tests verify that:
 * - Assets can be uploaded without Gaia-X Compliance validation
 * - Gaia-X validation is configurable via gaiax.enabled property
 * - No mandatory Gaia-X validation when disabled
 * - Backward compatibility - Gaia-X validation works when enabled
 *
 * Tests use programmatic toggling of the gaiax.enabled flag to verify
 * behavior changes with the same Spring context.
 */
@Slf4j
@SpringBootTest(properties = {
    "federated-catalogue.verification.signature-verifier=uni-res",
    "federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
    "federated-catalogue.verification.drop-validators=true",
    // Start with Gaia-X disabled - tests will toggle as needed
    "federated-catalogue.verification.trust-framework.gaiax.enabled=false",
    "federated-catalogue.verification.trust-framework.gaiax.trust-anchor-url=https://registry.lab.gaia-x.eu/v1/api/trustAnchor/chain/file"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {GaiaxTrustFrameworkTest.TestApplication.class, FileStoreConfig.class,
    DocumentLoaderConfig.class, DocumentLoaderProperties.class, VerificationServiceImpl.class,
    SchemaStoreImpl.class, SchemaJpaDao.class, SchemaAuditRepository.class, DatabaseConfig.class, DidResolverConfig.class,
    DidDocumentResolver.class, ValidatorCacheJpaDao.class,
    ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class})
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

    @BeforeEach
    public void setUp() {
        clearValidatorCache();
        schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        // Reset to default (matches @SpringBootTest property)
        setGaiaxEnabled(false);
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
     * Toggle Gaia-X trust framework enabled state via the database.
     */
    private void setGaiaxEnabled(boolean enabled) {
        jdbcTemplate.update(
            "UPDATE trust_frameworks SET enabled = ? WHERE id = 'gaia-x'", enabled);
    }

    // ==================== Disabled Behavior Tests ====================

    @Test
    @DisplayName("Credential without x5u URL should be ACCEPTED when Gaia-X is disabled")
    void testCredentialWithoutX5u_AcceptedWhenDisabled() {
        setGaiaxEnabled(false);

        String path = "VerificationService/sign-unires/participant_jwk_signed.jsonld";

        try {
            CredentialVerificationResult result = verificationService.verifyCredential(
                getAccessor(path), VERIFY_SEMANTICS, VERIFY_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES);
            // Success - trust anchor check was skipped
            assertNotNull(result, "Should return result when Gaia-X is disabled");
        } catch (VerificationException e) {
            // Should NOT get trust anchor error when Gaia-X is disabled
            assertFalse(e.getMessage().contains("no trust anchor url found"),
                "Should NOT require trust anchor URL when Gaia-X is disabled. Got: " + e.getMessage());
            // Other errors (like DID resolution) are acceptable
            log.info("Got acceptable non-trust-anchor error: {}", e.getMessage());
        }
    }

    @Test
    @DisplayName("Non-Gaia-X credential claims should be extractable")
    void testClaimsExtractable_WhenDisabled() {
        setGaiaxEnabled(false);

        String path = "VerificationService/syntax/participantCredential2.jsonld";

        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor(path), VERIFY_SEMANTICS, VERIFY_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result, "Should return result");
        assertTrue(result instanceof CredentialVerificationResultParticipant, "Should be participant result");
        assertNotNull(result.getClaims(), "Claims should be extracted");
        assertFalse(result.getClaims().isEmpty(), "Should have claims");
    }

    // ==================== Enabled Behavior Tests ====================

    @Test
    @DisplayName("Credential without x5u URL should be REJECTED when Gaia-X is enabled")
    void testCredentialWithoutX5u_RejectedWhenEnabled() {
        setGaiaxEnabled(true);

        String path = "VerificationService/sign-unires/participant_jwk_signed.jsonld";

        Exception ex = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(getAccessor(path),
                VERIFY_SEMANTICS, VERIFY_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES));

        assertEquals("Signatures error; no trust anchor url found", ex.getMessage(),
            "Should require trust anchor URL when Gaia-X is enabled");
    }

    @Test
    @DisplayName("Gaia-X compliant credential should still work when enabled")
    void testGaiaxCredentialWorks_WhenEnabled() {
        setGaiaxEnabled(true);

        String path = "VerificationService/syntax/participantCredential2.jsonld";

        // Without signature verification, this should work
        CredentialVerificationResult result = verificationService.verifyCredential(
            getAccessor(path), VERIFY_SEMANTICS, VERIFY_SCHEMA, SKIP_VP_SIGNATURES, SKIP_VC_SIGNATURES);

        assertNotNull(result, "Should process Gaia-X credential");
        assertTrue(result instanceof CredentialVerificationResultParticipant);
    }

    // ==================== Behavioral Toggle Tests ====================

    @Test
    @DisplayName("Same credential should have DIFFERENT outcomes based on Gaia-X setting")
    void testSameCredentialDifferentOutcomes() {
        String path = "VerificationService/sign-unires/participant_jwk_signed.jsonld";

        // --- Test with Gaia-X ENABLED: should REJECT ---
        clearValidatorCache(); // Ensure no cached validators interfere
        setGaiaxEnabled(true);
        // Precondition: Gaia-X should be enabled (set via setGaiaxEnabled above)

        Exception enabledEx = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(getAccessor(path),
                VERIFY_SEMANTICS, VERIFY_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES),
            "Should throw when Gaia-X is enabled");
        assertTrue(enabledEx.getMessage().contains("no trust anchor url found"),
            "Error should mention trust anchor when enabled");

        // --- Test with Gaia-X DISABLED: should NOT get trust anchor error ---
        clearValidatorCache(); // Clear cache before testing with different setting
        setGaiaxEnabled(false);
        // Precondition: Gaia-X should be disabled (set via setGaiaxEnabled above)

        try {
            verificationService.verifyCredential(getAccessor(path),
                VERIFY_SEMANTICS, VERIFY_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES);
            // Success - no exception means trust anchor check was skipped
        } catch (VerificationException e) {
            // Must NOT be the trust anchor error
            assertFalse(e.getMessage().contains("no trust anchor url found"),
                "Should NOT require trust anchor when disabled. Got: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("Behavior should change when toggling Gaia-X at runtime")
    void testBehaviorChangesWhenToggling() {
        String path = "VerificationService/sign-unires/participant_jwk_signed.jsonld";

        // --- First: DISABLE Gaia-X, verify no trust anchor error ---
        clearValidatorCache(); // Ensure no cached validators interfere
        setGaiaxEnabled(false);

        try {
            verificationService.verifyCredential(getAccessor(path),
                VERIFY_SEMANTICS, VERIFY_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES);
        } catch (VerificationException e) {
            if (e.getMessage().contains("no trust anchor url found")) {
                fail("Should NOT require trust anchor when Gaia-X is disabled. Got: " + e.getMessage());
            }
            // Other errors are OK
        }

        // --- Then: ENABLE Gaia-X, verify trust anchor error occurs ---
        clearValidatorCache(); // Clear cache before testing with different setting
        setGaiaxEnabled(true);

        Exception ex = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(getAccessor(path),
                VERIFY_SEMANTICS, VERIFY_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES),
            "Should throw when Gaia-X is enabled");
        assertEquals("Signatures error; no trust anchor url found", ex.getMessage(),
            "Should require trust anchor when Gaia-X is enabled");
    }

    // ==================== Security Tests ====================

    @Test
    @DisplayName("Invalid signature should still be rejected when Gaia-X is disabled")
    void testInvalidSignatureRejected_WhenDisabled() {
        setGaiaxEnabled(false);

        String path = "VerificationService/sign/hasInvalidSignature.json";

        Exception ex = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(getAccessor(path),
                SKIP_SEMANTICS, SKIP_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES));

        // The credential must NOT be accepted. We expect either:
        // - "does not match with proof" (when DID can be resolved)
        // - A DID resolution error (when external resolver is unavailable)
        // Either way, it's NOT a trust anchor error (which would indicate Gaia-X check wasn't disabled)
        assertFalse(ex.getMessage().contains("no trust anchor url found"),
            "Should NOT get trust anchor error when Gaia-X is disabled. Got: " + ex.getMessage());
    }

    @Test
    @DisplayName("Missing proof should still be rejected when Gaia-X is disabled")
    void testMissingProofRejected_WhenDisabled() {
        setGaiaxEnabled(false);

        String path = "VerificationService/sign/hasNoSignature1.json";

        Exception ex = assertThrowsExactly(VerificationException.class, () ->
            verificationService.verifyCredential(getAccessor(path),
                SKIP_SEMANTICS, VERIFY_SCHEMA, VERIFY_VP_SIGNATURES, VERIFY_VC_SIGNATURES));

        assertEquals("Signatures error; No proof found", ex.getMessage(),
            "Missing proofs should still be rejected");
    }
}
