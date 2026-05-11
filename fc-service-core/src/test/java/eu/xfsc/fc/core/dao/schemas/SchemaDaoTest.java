package eu.xfsc.fc.core.dao.schemas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {SchemaDaoTest.TestConfig.class, SchemaJpaDao.class, SchemaAuditRepository.class,
    DatabaseConfig.class, SecurityAuditorAware.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class SchemaDaoTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private SchemaDao schemaDao;

  @Autowired
  private SchemaFileRepository schemaFileRepository;

  @AfterEach
  void cleanUp() {
    SecurityContextHolder.clearContext();
    schemaDao.deleteAll();
  }


  private static SchemaRecord buildRecord(String schemaId, String nameHash, SchemaType type,
      Instant uploadTime, Instant updateTime, String content, Set<String> terms) {
    return new SchemaRecord(schemaId, nameHash, type, uploadTime, updateTime, content, terms);
  }

  private static SchemaRecord buildSimpleRecord(String schemaId, SchemaType type,
      String content, Set<String> terms) {
    return new SchemaRecord(schemaId, schemaId, type, content, terms);
  }

  // ===== getSchemaCount =====

  @Test
  void getSchemaCount_emptyTable_returnsZero() {
    int count = schemaDao.getSchemaCount();

    assertEquals(0, count);
  }

  @Test
  void getSchemaCount_afterInserts_returnsCorrectCount() {
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content1", null));
    schemaDao.insert(buildSimpleRecord("schema-2", SchemaType.SHAPE, "content2", null));
    schemaDao.insert(buildSimpleRecord("schema-3", SchemaType.VOCABULARY, "content3", null));

    int count = schemaDao.getSchemaCount();

    assertEquals(3, count);
  }

  // ===== select =====

  @Test
  void select_existingSchema_returnsRecordWithAllFields() {
    Instant now = Instant.now();
    SchemaRecord record = buildRecord("schema-1", "hash-1", SchemaType.ONTOLOGY,
        now, now, "ontology content", Set.of("term1", "term2"));
    schemaDao.insert(record);

    Optional<SchemaRecord> result = schemaDao.select("schema-1");

    assertTrue(result.isPresent());
    SchemaRecord sr = result.get();
    assertEquals("schema-1", sr.schemaId());
    assertEquals("hash-1", sr.nameHash());
    assertEquals(SchemaType.ONTOLOGY, sr.type());
    assertEquals("ontology content", sr.content());
    assertNotNull(sr.createdAt());
    assertNotNull(sr.modifiedAt());
    // JPA select() returns terms (JDBC impl returned null)
    assertEquals(Set.of("term1", "term2"), sr.terms());
  }

  @Test
  void select_nonExistentSchema_returnsEmpty() {
    Optional<SchemaRecord> result = schemaDao.select("nonexistent");

    assertTrue(result.isEmpty());
  }

  // ===== selectSchemas =====

  @Test
  void selectSchemas_multipleTypes_returnsGroupedByType() {
    schemaDao.insert(buildSimpleRecord("ont-1", SchemaType.ONTOLOGY, "c1", null));
    schemaDao.insert(buildSimpleRecord("ont-2", SchemaType.ONTOLOGY, "c2", null));
    schemaDao.insert(buildSimpleRecord("shape-1", SchemaType.SHAPE, "c3", null));

    Map<String, Collection<String>> result = schemaDao.selectSchemas();

    assertEquals(2, result.size());
    assertTrue(result.containsKey("ONTOLOGY"));
    assertTrue(result.containsKey("SHAPE"));
    assertEquals(Set.of("ont-1", "ont-2"), Set.copyOf(result.get("ONTOLOGY")));
    assertEquals(Set.of("shape-1"), Set.copyOf(result.get("SHAPE")));
  }

  @Test
  void selectSchemas_emptyTable_returnsEmptyMap() {
    Map<String, Collection<String>> result = schemaDao.selectSchemas();

    assertTrue(result.isEmpty());
  }

  // ===== selectSchemasByTerm =====

  @Test
  void selectSchemasByTerm_matchingTerm_returnsGroupedByType() {
    // PK on schematerms is (term) alone — each term belongs to exactly one schema
    schemaDao.insert(buildSimpleRecord("ont-1", SchemaType.ONTOLOGY, "c1", Set.of("termA", "termB")));
    schemaDao.insert(buildSimpleRecord("shape-1", SchemaType.SHAPE, "c2", Set.of("termC")));

    Map<String, Collection<String>> result = schemaDao.selectSchemasByTerm("termA");

    assertEquals(1, result.size());
    assertTrue(result.get("ONTOLOGY").contains("ont-1"));
  }

  @Test
  void selectSchemasByTerm_nonExistentTerm_returnsEmptyMap() {
    schemaDao.insert(buildSimpleRecord("ont-1", SchemaType.ONTOLOGY, "c1", Set.of("termA")));

    Map<String, Collection<String>> result = schemaDao.selectSchemasByTerm("nonexistent");

    assertTrue(result.isEmpty());
  }

  @Test
  void selectSchemasByTerm_multipleSchemasDifferentTerms_returnsCorrectSchema() {
    // PK on schematerms is (term) alone — terms are globally unique
    schemaDao.insert(buildSimpleRecord("ont-1", SchemaType.ONTOLOGY, "c1", Set.of("termX")));
    schemaDao.insert(buildSimpleRecord("ont-2", SchemaType.ONTOLOGY, "c2", Set.of("termY")));
    schemaDao.insert(buildSimpleRecord("shape-1", SchemaType.SHAPE, "c3", Set.of("termZ")));

    Map<String, Collection<String>> resultX = schemaDao.selectSchemasByTerm("termX");
    Map<String, Collection<String>> resultY = schemaDao.selectSchemasByTerm("termY");

    assertEquals(Set.of("ont-1"), Set.copyOf(resultX.get("ONTOLOGY")));
    assertEquals(Set.of("ont-2"), Set.copyOf(resultY.get("ONTOLOGY")));
  }

  // ===== insert =====

  @Test
  void insert_newSchema_returnsTrue() {
    boolean inserted = schemaDao.insert(
        buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content", Set.of("t1", "t2")));

    assertTrue(inserted);

    Optional<SchemaRecord> result = schemaDao.select("schema-1");
    assertTrue(result.isPresent());
    assertEquals("content", result.get().content());
  }

  @Test
  void insert_withoutTerms_returnsTrue() {
    boolean inserted = schemaDao.insert(
        buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content", null));

    assertTrue(inserted);
  }

  @Test
  void insert_emptyTerms_returnsTrue() {
    boolean inserted = schemaDao.insert(
        buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content", Set.of()));

    assertTrue(inserted);
  }

  @Test
  void insert_duplicateSchemaId_throwsDuplicateKeyException() {
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "c1", null));

    assertThrows(Exception.class,
        () -> schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.SHAPE, "c2", null)));
  }

  @Test
  void insert_verifyTermsPersisted() {
    Set<String> terms = Set.of("term1", "term2", "term3");
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content", terms));

    // Verify terms via selectSchemasByTerm
    for (String term : terms) {
      Map<String, Collection<String>> result = schemaDao.selectSchemasByTerm(term);
      assertFalse(result.isEmpty(), "Term '" + term + "' should be persisted");
      assertTrue(result.get("ONTOLOGY").contains("schema-1"));
    }
  }

  // ===== update =====

  @Test
  void update_existingSchema_updatesContentAndTerms() {
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "old content",
        Set.of("oldTerm")));

    schemaDao.update("schema-1", "new content", Set.of("newTerm"));

    Optional<SchemaRecord> result = schemaDao.select("schema-1");
    assertTrue(result.isPresent());
    assertEquals("new content", result.get().content());
  }

  @Test
  void update_changeTerms_replacesAllTerms() {
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content",
        Set.of("oldA", "oldB")));

    schemaDao.update("schema-1", "content", Set.of("newX", "newY"));

    // Old terms should be gone
    assertTrue(schemaDao.selectSchemasByTerm("oldA").isEmpty());
    assertTrue(schemaDao.selectSchemasByTerm("oldB").isEmpty());
    // New terms should exist
    assertFalse(schemaDao.selectSchemasByTerm("newX").isEmpty());
    assertFalse(schemaDao.selectSchemasByTerm("newY").isEmpty());
  }

  @Test
  void update_verifyUpdateTimeChanges() {
    Instant originalTime = Instant.parse("2024-01-01T00:00:00Z");
    SchemaRecord record = buildRecord("schema-1", "hash-1", SchemaType.ONTOLOGY,
        originalTime, originalTime, "content", null);
    schemaDao.insert(record);

    schemaDao.update("schema-1", "updated", null);

    Optional<SchemaRecord> result = schemaDao.select("schema-1");
    assertTrue(result.isPresent());
    assertTrue(result.get().modifiedAt().isAfter(originalTime),
        "modifiedAt should be after original time");
  }

  @Test
  void update_nonExistentSchema_throwsNotFoundException() {
    assertThrows(NotFoundException.class,
        () -> schemaDao.update("nonexistent", "content", null));
  }

  // ===== delete =====

  @Test
  void delete_existingSchema_returnsTypeName() {
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content", null));

    String type = schemaDao.delete("schema-1");

    assertEquals("ONTOLOGY", type);
    assertTrue(schemaDao.select("schema-1").isEmpty());
  }

  @Test
  void delete_nonExistentSchema_returnsNull() {
    String type = schemaDao.delete("nonexistent");

    assertNull(type);
  }

  @Test
  void delete_verifyTermsCascadeDeleted() {
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content",
        Set.of("term1", "term2")));
    // Precondition: terms exist
    assertFalse(schemaDao.selectSchemasByTerm("term1").isEmpty());

    schemaDao.delete("schema-1");

    assertTrue(schemaDao.selectSchemasByTerm("term1").isEmpty());
    assertTrue(schemaDao.selectSchemasByTerm("term2").isEmpty());
  }

  // ===== deleteAll =====

  @Test
  void deleteAll_withData_returnsCount() {
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "c1", null));
    schemaDao.insert(buildSimpleRecord("schema-2", SchemaType.SHAPE, "c2", null));

    int count = schemaDao.deleteAll();

    assertEquals(2, count);
  }

  @Test
  void deleteAll_emptyTable_returnsZero() {
    int count = schemaDao.deleteAll();

    assertEquals(0, count);
  }

  // ===== selectLatestContentByType =====

  @Test
  void selectLatestContentByType_multipleOfSameType_returnsLatestByUploadTime() {
    Instant earlier = Instant.parse("2024-01-01T00:00:00Z");
    Instant later = Instant.parse("2024-06-01T00:00:00Z");
    schemaDao.insert(buildRecord("schema-old", "hash-old", SchemaType.ONTOLOGY,
        earlier, earlier, "old content", null));
    schemaDao.insert(buildRecord("schema-new", "hash-new", SchemaType.ONTOLOGY,
        later, later, "latest content", null));

    Optional<String> result = schemaDao.selectLatestContentByType("ONTOLOGY");

    assertTrue(result.isPresent());
    assertEquals("latest content", result.get());
  }

  @Test
  void selectLatestContentByType_nonExistentType_returnsEmpty() {
    schemaDao.insert(buildSimpleRecord("schema-1", SchemaType.ONTOLOGY, "content", null));

    Optional<String> result = schemaDao.selectLatestContentByType("SHAPE");

    assertTrue(result.isEmpty());
  }

  // ===== JPA auditing =====

  @Test
  void insert_withJwtContext_populatesCreatedByAndModifiedBy() {
    Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("schema-user").build();
    SecurityContextHolder.setContext(new SecurityContextImpl(
        new JwtAuthenticationToken(jwt, Collections.emptyList())));

    schemaDao.insert(buildSimpleRecord("schema-audit", SchemaType.ONTOLOGY, "content", null));

    SchemaFile entity = schemaFileRepository.findBySchemaId("schema-audit").orElseThrow();
    assertEquals("schema-user", entity.getCreatedBy());
    assertEquals("schema-user", entity.getModifiedBy());
    assertNotNull(entity.getCreatedAt());
    assertNotNull(entity.getModifiedAt());
  }

  @Test
  void insert_withoutJwtContext_timestampsSetCreatedByNull() {
    schemaDao.insert(buildSimpleRecord("schema-noauth", SchemaType.ONTOLOGY, "content", null));

    SchemaFile entity = schemaFileRepository.findBySchemaId("schema-noauth").orElseThrow();
    assertNull(entity.getCreatedBy());
    assertNull(entity.getModifiedBy());
    assertNotNull(entity.getCreatedAt());
    assertNotNull(entity.getModifiedAt());
  }

  @Test
  void update_withDifferentJwtContext_updatesModifiedByNotCreatedBy() {
    Jwt jwtA = Jwt.withTokenValue("tokenA").header("alg", "none").subject("user-a").build();
    SecurityContextHolder.setContext(new SecurityContextImpl(
        new JwtAuthenticationToken(jwtA, Collections.emptyList())));
    schemaDao.insert(buildSimpleRecord("schema-upd", SchemaType.ONTOLOGY, "original", null));

    Jwt jwtB = Jwt.withTokenValue("tokenB").header("alg", "none").subject("user-b").build();
    SecurityContextHolder.setContext(new SecurityContextImpl(
        new JwtAuthenticationToken(jwtB, Collections.emptyList())));
    schemaDao.update("schema-upd", "updated", null);

    SchemaFile entity = schemaFileRepository.findBySchemaId("schema-upd").orElseThrow();
    assertEquals("user-a", entity.getCreatedBy());
    assertEquals("user-b", entity.getModifiedBy());
  }
}
