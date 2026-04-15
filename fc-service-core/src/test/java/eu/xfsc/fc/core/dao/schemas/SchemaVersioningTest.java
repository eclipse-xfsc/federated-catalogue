package eu.xfsc.fc.core.dao.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

/**
 * Tests for schema versioning via Envers audit infrastructure.
 *
 * <p>Uses {@link TransactionTemplate} with explicit commits so Envers
 * audit entries are written (class-level @Transactional would roll back
 * and produce zero revisions).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {SchemaVersioningTest.TestConfig.class, SchemaJpaDao.class, SchemaAuditRepository.class,
    DatabaseConfig.class, SecurityAuditorAware.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class SchemaVersioningTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  private static final String SCHEMA_ID = "s-1";
  private static final String SCHEMA_HASH = "h-1";
  private static final String INITIAL_CONTENT = "original";
  private static final String UPDATED_CONTENT = "updated";
  private static final String TERM_A = "termA";
  private static final String TERM_B = "termB";
  private static final String TERM_C = "termC";

  @Autowired
  private SchemaDao schemaDao;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void cleanUp() {
    transactionTemplate.executeWithoutResult(status -> schemaDao.deleteAll());
  }

  // ===== selectVersions =====

  @Test
  void selectVersions_afterInsert_returnsVersion1() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
            INITIAL_CONTENT, Set.of(TERM_A))));

    List<SchemaRecord> versions = schemaDao.selectVersions(SCHEMA_ID);

    assertEquals(1, versions.size());
    SchemaRecord v1 = versions.getFirst();
    assertEquals(1, v1.version());
    assertEquals(INITIAL_CONTENT, v1.content());
    assertNotNull(v1.createdAt());
  }

  @Test
  void selectVersions_afterUpdate_returnsVersions1And2() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
            INITIAL_CONTENT, Set.of(TERM_A))));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update(SCHEMA_ID, UPDATED_CONTENT, Set.of(TERM_B)));

    List<SchemaRecord> versions = schemaDao.selectVersions(SCHEMA_ID);

    assertEquals(2, versions.size());
    assertEquals(1, versions.getFirst().version());
    assertEquals(INITIAL_CONTENT, versions.getFirst().content());
    assertEquals(2, versions.get(1).version());
    assertEquals(UPDATED_CONTENT, versions.get(1).content());
  }

  @Test
  void selectVersions_termsPreservedPerVersion() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
            "content-v1", Set.of(TERM_A, TERM_B))));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update(SCHEMA_ID, "content-v2", Set.of(TERM_C)));

    List<SchemaRecord> versions = schemaDao.selectVersions(SCHEMA_ID);
    assertEquals(Set.of(TERM_A, TERM_B), versions.getFirst().terms());
    assertEquals(Set.of(TERM_C), versions.get(1).terms());
  }

  @Test
  void selectVersions_nonExistentSchema_returnsEmptyList() {
    List<SchemaRecord> versions = schemaDao.selectVersions("nonexistent");

    assertTrue(versions.isEmpty());
  }

  @Test
  void selectVersions_threeUpdates_producesVersions123() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
            "v1", null)));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update(SCHEMA_ID, "v2", null));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update(SCHEMA_ID, "v3", null));

    List<SchemaRecord> versions = schemaDao.selectVersions(SCHEMA_ID);

    assertEquals(3, versions.size());
    assertEquals(1, versions.getFirst().version());
    assertEquals("v1", versions.getFirst().content());
    assertEquals(2, versions.get(1).version());
    assertEquals("v2", versions.get(1).content());
    assertEquals(3, versions.get(2).version());
    assertEquals("v3", versions.get(2).content());
  }

  @Test
  void selectVersions_ascendingOrder() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
            "v1", null)));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update(SCHEMA_ID, "v2", null));

    List<SchemaRecord> versions = schemaDao.selectVersions(SCHEMA_ID);

    assertEquals(1, versions.getFirst().version());
    assertEquals(2, versions.get(1).version());
    assertTrue(versions.getFirst().version() < versions.get(1).version(),
        "Versions should be in ascending order");
  }

  // ===== selectVersion =====

  @Test
  void selectVersion_version1_returnsOriginalContent() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
            INITIAL_CONTENT, Set.of(TERM_A))));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update(SCHEMA_ID, UPDATED_CONTENT, Set.of(TERM_B)));

    Optional<SchemaRecord> v1 = schemaDao.selectVersion(SCHEMA_ID, 1);

    assertTrue(v1.isPresent());
    assertEquals(INITIAL_CONTENT, v1.get().content());
    assertEquals(1, v1.get().version());
    assertEquals(Set.of(TERM_A), v1.get().terms());
  }

  @Test
  void selectVersion_version2_returnsUpdatedContent() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
            INITIAL_CONTENT, Set.of(TERM_A))));

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update(SCHEMA_ID, UPDATED_CONTENT, Set.of(TERM_B)));

    Optional<SchemaRecord> v2 = schemaDao.selectVersion(SCHEMA_ID, 2);

    assertTrue(v2.isPresent());
    assertEquals(UPDATED_CONTENT, v2.get().content());
    assertEquals(2, v2.get().version());
    assertEquals(Set.of(TERM_B), v2.get().terms());
  }

  @Test
  void selectVersion_nonExistentVersion_returnsEmpty() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
                INITIAL_CONTENT, null)));

    Optional<SchemaRecord> result = schemaDao.selectVersion(SCHEMA_ID, 999);

    assertTrue(result.isEmpty());
  }

  @Test
  void selectVersion_versionZero_returnsEmpty() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
            INITIAL_CONTENT, null)));

    Optional<SchemaRecord> result = schemaDao.selectVersion(SCHEMA_ID, 0);

    assertTrue(result.isEmpty());
  }

  @Test
  void selectVersion_negativeVersion_returnsEmpty() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord(SCHEMA_ID, SCHEMA_HASH, SchemaType.ONTOLOGY,
                INITIAL_CONTENT, null)));

    Optional<SchemaRecord> result = schemaDao.selectVersion(SCHEMA_ID, -1);

    assertTrue(result.isEmpty());
  }
}
