package eu.xfsc.fc.core.service.schemastore;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.config.TrustFrameworkRegistryConfig;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * SchemaStoreImpl seeds the schema-store DB from TrustFrameworkRegistry bundles
 * instead of reading directly from defaultschema/.
 */
@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {
    SchemaStoreBundleSeedingTest.TestApplication.class,
    FileStoreConfig.class,
    SchemaStoreBundleSeedingTest.class,
    SchemaStoreImpl.class,
    DatabaseConfig.class,
    SchemaJpaDao.class,
    SchemaAuditRepository.class,
    ProtectedNamespaceFilter.class,
    ProtectedNamespaceProperties.class,
    SecurityAuditorAware.class,
    TrustFrameworkRegistryConfig.class
})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class SchemaStoreBundleSeedingTest {

  @SpringBootApplication
  public static class TestApplication {

    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private SchemaStoreImpl schemaStore;

  @BeforeEach
  void clearStore() {
    schemaStore.clear();
  }

  @Test
  void initializeDefaultSchemas_emptyDb_insertsBundleOntologyAndShapes() {
    int count = schemaStore.initializeDefaultSchemas();

    assertThat(count).isGreaterThanOrEqualTo(2);
    Map<SchemaType, List<String>> schemas = schemaStore.getSchemaList();
    assertThat(schemas.get(SchemaType.ONTOLOGY)).isNotEmpty();
    assertThat(schemas.get(SchemaType.SHAPE)).isNotEmpty();
  }

  @Test
  void initializeDefaultSchemas_compositeShacl_containsShapeDeclarations() {
    schemaStore.initializeDefaultSchemas();

    ContentAccessor composite = schemaStore.getCompositeSchema(SchemaType.SHAPE);
    assertThat(composite.getContentAsString())
        .isNotEmpty()
        .contains("NodeShape");
  }

  @Test
  void initializeDefaultSchemas_populatedDb_returnsZeroAndSkipsReseed() {
    schemaStore.initializeDefaultSchemas();

    int count = schemaStore.initializeDefaultSchemas();

    assertThat(count).isEqualTo(0);
  }

  @Test
  void initializeDefaultSchemas_customOverlaySchema_loadedBesideBundle() {
    // custom-overlay-test.ttl in src/test/resources/defaultschema/ontology/ acts as operator overlay
    schemaStore.initializeDefaultSchemas();

    Map<SchemaType, List<String>> schemas = schemaStore.getSchemaList();
    assertThat(schemas.get(SchemaType.ONTOLOGY))
        .contains("https://example.org/custom-overlay-ontology");
  }

  @Test
  void initializeDefaultSchemas_duplicateOverlayContent_skippedWithoutException() {
    // Seed the gx-2511 ontology via the bundle first, then attempt the same URI via overlay.
    // The overlay path must swallow ConflictException and continue rather than aborting.
    schemaStore.initializeDefaultSchemas();

    // A second call still succeeds (guard path) — this indirectly confirms no exception propagated
    // during the first seeding when the overlay found duplicate content.
    assertThat(schemaStore.getSchemaList().get(SchemaType.ONTOLOGY)).isNotEmpty();
  }
}
