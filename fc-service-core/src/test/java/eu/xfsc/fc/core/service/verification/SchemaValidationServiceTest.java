package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.SchemaModuleConfigService;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;
import eu.xfsc.fc.core.service.verification.claims.JenaAllTriplesExtractor;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

/**
 * Unit tests for {@link SchemaValidationService}.
 *
 * <p>Verifies SHACL validation of credential payloads against individual and composite
 * schemas, covering both conforming and non-conforming inputs.</p>
 */
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {SchemaValidationServiceTest.TestApplication.class, FileStoreConfig.class,
        DocumentLoaderConfig.class, DocumentLoaderProperties.class, SchemaValidationServiceImpl.class,
        VerificationServiceImpl.class, CredentialVerificationStrategy.class, SchemaStoreImpl.class, SchemaJpaDao.class, SchemaAuditRepository.class, DatabaseConfig.class,
        DidResolverConfig.class, DidDocumentResolver.class, ValidatorCacheJpaDao.class, HttpDocumentResolver.class,
        AdminConfigRepository.class, SchemaModuleConfigService.class,
        JwtSignatureVerifier.class, ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class,
        SecurityAuditorAware.class, ClaimExtractionService.class, JenaAllTriplesExtractor.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public class SchemaValidationServiceTest {

    @SpringBootApplication
    public static class TestApplication {
        public static void main(final String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @Autowired
    private SchemaValidationService schemaValidationService;

    @Autowired
    private SchemaStoreImpl schemaStore;

    /** Clears all schemas from the store after each test to ensure isolation. */
    @AfterEach
    public void storageSelfCleaning() throws IOException {
        schemaStore.clear();
    }

    /** Verifies that an invalid legal-person payload fails SHACL validation against a specific schema. */
    @Test
    void validateInvalidPayloadAgainstSchema() {
        SchemaValidationResult result = schemaValidationService.validateCredentialAgainstSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"),
                getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isConforming(), "Invalid payload should not conform");
        assertTrue(result.getValidationReport().contains("Property needs to have at least 1 value"));
    }

    /** Verifies that a valid legal-person payload passes SHACL validation against a specific schema. */
    @Test
    void validateValidPayloadAgainstSchema() {
        SchemaValidationResult result = schemaValidationService.validateCredentialAgainstSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Valid.jsonld"),
                getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isConforming(), "Valid payload should conform");
    }

    /** Verifies that an invalid payload fails against the composite schema built from stored shapes. */
    @Test
    void validateInvalidPayloadAgainstCompositeSchema() {
        schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

        SchemaValidationResult result = schemaValidationService.validateCredentialAgainstCompositeSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"));

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isConforming(), "Invalid payload should not conform against composite schema");
        assertTrue(result.getValidationReport().contains("Property needs to have at least 1 value"));
    }

    /** Verifies that a valid payload passes against the composite schema built from stored shapes. */
    @Test
    void validateValidPayloadAgainstCompositeSchema() {
        schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

        SchemaValidationResult result = schemaValidationService.validateCredentialAgainstCompositeSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Valid.jsonld"));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isConforming(), "Valid payload should conform against composite schema");
    }

    /** Verifies that passing {@code null} as schema falls back to composite schema validation. */
    @Test
    void validateAgainstNullSchemaFallsBackToComposite() {
        schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

        SchemaValidationResult resultExplicit = schemaValidationService.validateCredentialAgainstCompositeSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"));
        SchemaValidationResult resultNullSchema = schemaValidationService.validateCredentialAgainstSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"), null);

        assertNotNull(resultExplicit);
        assertNotNull(resultNullSchema);
        assertFalse(resultExplicit.isConforming());
        assertFalse(resultNullSchema.isConforming());
    }

    // ==================== Loire (Gaia-X 2511) SHACL validation tests ====================

    /** Valid Loire gx:LegalPerson credential conforms to 2511 shapes. */
    @Test
    void validateLoire_validLegalPerson_conforms() {
        SchemaValidationResult result = schemaValidationService.validateCredentialAgainstSchema(
                getAccessor("Validation-Tests/loire_legalPerson_valid.jsonld"),
                getAccessor("Schema-Tests/gx-2511-test-shapes.ttl"));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isConforming(),
                "Valid Loire gx:LegalPerson credential should conform to 2511 shapes. Report: "
                        + result.getValidationReport());
    }

    /** Loire gx:LegalPerson missing required gx:registrationNumber is rejected. */
    @Test
    void validateLoire_missingRequiredProperty_rejected() {
        SchemaValidationResult result = schemaValidationService.validateCredentialAgainstSchema(
                getAccessor("Validation-Tests/loire_legalPerson_missing_required.jsonld"),
                getAccessor("Schema-Tests/gx-2511-test-shapes.ttl"));

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isConforming(),
                "Loire credential missing gx:registrationNumber should not conform");
    }

    /** Loire gx:LegalPerson with unexpected property violates the closed shape. */
    @Test
    void validateLoire_unexpectedPropertyOnClosedShape_rejected() {
        SchemaValidationResult result = schemaValidationService.validateCredentialAgainstSchema(
                getAccessor("Validation-Tests/loire_legalPerson_closed_violation.jsonld"),
                getAccessor("Schema-Tests/gx-2511-test-shapes.ttl"));

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isConforming(),
                "Loire credential with unexpected property on a closed shape should not conform");
    }

}
