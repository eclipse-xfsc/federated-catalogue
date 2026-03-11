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
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.dao.impl.SchemaDaoImpl;
import eu.xfsc.fc.core.dao.impl.ValidatorCacheDaoImpl;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

/**
 * Unit tests for {@link SchemaValidationService}.
 *
 * <p>Verifies SHACL validation of self-description payloads against individual and composite
 * schemas, covering both conforming and non-conforming inputs.</p>
 */
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {SchemaValidationServiceTest.TestApplication.class, FileStoreConfig.class,
        DocumentLoaderConfig.class, DocumentLoaderProperties.class, SchemaValidationServiceImpl.class,
        VerificationServiceImpl.class, CredentialVerificationStrategy.class, SchemaStoreImpl.class, SchemaDaoImpl.class, DatabaseConfig.class,
        DidResolverConfig.class, ValidatorCacheDaoImpl.class, HttpDocumentResolver.class})
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
        SchemaValidationResult result = schemaValidationService.validateSelfDescriptionAgainstSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"),
                getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isConforming(), "Invalid payload should not conform");
        assertTrue(result.getValidationReport().contains("Property needs to have at least 1 value"));
    }

    /** Verifies that a valid legal-person payload passes SHACL validation against a specific schema. */
    @Test
    void validateValidPayloadAgainstSchema() {
        SchemaValidationResult result = schemaValidationService.validateSelfDescriptionAgainstSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Valid.jsonld"),
                getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isConforming(), "Valid payload should conform");
    }

    /** Verifies that an invalid payload fails against the composite schema built from multiple stored shapes. */
    @Test
    void validateInvalidPayloadAgainstCompositeSchema() {
        schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));
        schemaStore.addSchema(getAccessor("Validation-Tests/legal-personShape.ttl"));

        SchemaValidationResult result = schemaValidationService.validateSelfDescriptionAgainstCompositeSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"));

        assertNotNull(result, "Result should not be null");
        assertFalse(result.isConforming(), "Invalid payload should not conform against composite schema");
        assertTrue(result.getValidationReport().contains("Property needs to have at least 1 value"));
    }

    /** Verifies that a valid payload passes against the composite schema built from multiple stored shapes. */
    @Test
    void validateValidPayloadAgainstCompositeSchema() {
        schemaStore.addSchema(getAccessor("Validation-Tests/legal-personShape.ttl"));
        schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

        SchemaValidationResult result = schemaValidationService.validateSelfDescriptionAgainstCompositeSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Valid.jsonld"));

        assertNotNull(result, "Result should not be null");
        assertTrue(result.isConforming(), "Valid payload should conform against composite schema");
    }

    /** Verifies that passing {@code null} as schema falls back to composite schema validation. */
    @Test
    void validateAgainstNullSchemaFallsBackToComposite() {
        schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));
        schemaStore.addSchema(getAccessor("Validation-Tests/legal-personShape.ttl"));

        SchemaValidationResult resultExplicit = schemaValidationService.validateSelfDescriptionAgainstCompositeSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"));
        SchemaValidationResult resultNullSchema = schemaValidationService.validateSelfDescriptionAgainstSchema(
                getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"), null);

        assertNotNull(resultExplicit);
        assertNotNull(resultNullSchema);
        assertFalse(resultExplicit.isConforming());
        assertFalse(resultNullSchema.isConforming());
    }

}
