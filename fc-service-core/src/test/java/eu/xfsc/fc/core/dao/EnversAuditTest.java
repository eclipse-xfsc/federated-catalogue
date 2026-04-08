package eu.xfsc.fc.core.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import eu.xfsc.fc.core.dao.assets.Asset;
import eu.xfsc.fc.core.dao.schemas.SchemaFile;
import eu.xfsc.fc.core.dao.schemas.SchemaTerm;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.RevisionType;
import org.hibernate.envers.query.AuditEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.transaction.support.TransactionTemplate;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.schemas.SchemaDao;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import jakarta.persistence.EntityManager;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {
    EnversAuditTest.TestConfig.class,
    AssetJpaDao.class, SchemaJpaDao.class,
    DatabaseConfig.class
})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class EnversAuditTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private AssetDao assetDao;

  @Autowired
  private SchemaDao schemaDao;

  @Autowired
  private EntityManager entityManager;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanUp() {
    transactionTemplate.executeWithoutResult(status ->
            jdbcTemplate.execute("TRUNCATE schematerms_aud, schemafiles_aud, assets_aud,"
                                 + " revinfo, schematerms, schemafiles, assets CASCADE"));
  }

  // --- Asset helpers ---

  private AssetRecord buildAssetRecord(String hash, String subjectId, String issuer,
      List<String> validators) {
    return AssetRecord.builder()
        .assetHash(hash)
        .id(subjectId)
        .issuer(issuer)
        .uploadTime(Instant.parse("2024-01-01T00:00:00Z"))
        .statusTime(Instant.parse("2024-01-01T00:00:00Z"))
        .expirationTime(null)
        .status(AssetStatus.ACTIVE)
        .content(new ContentAccessorDirect("content-" + hash))
        .validatorDids(validators)
        .contentType("application/ld+json")
        .fileSize(100L)
        .originalFilename("file.jsonld")
        .build();
  }

  // ===== Asset audit tests =====

  @Test
  void insertAsset_createsAuditEntry() {
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-1", "sub/1", "iss/1",
            List.of("did:val:1")))
    );

    List<?> revisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(Asset.class, false, true)
          .add(AuditEntity.property("assetHash").eq("hash-1"))
          .getResultList();
    });

    assertNotNull(revisions);
    assertEquals(1, revisions.size());
    Object[] row = (Object[]) revisions.getFirst();
    Asset audited = (Asset) row[0];
    RevisionType revType = (RevisionType) row[2];
    assertEquals(RevisionType.ADD, revType);
    assertEquals("hash-1", audited.getAssetHash());
    assertEquals("sub/1", audited.getSubjectId());
    assertEquals("iss/1", audited.getIssuer());
  }

  @Test
  void insertAsset_auditsValidatorsArray() {
    List<String> validators = List.of("did:val:A", "did:val:B");

    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-arr", "sub/arr", "iss/arr", validators))
    );

    Asset audited = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      List<?> revisions = reader.createQuery()
          .forRevisionsOfEntity(Asset.class, true, true)
          .add(AuditEntity.property("assetHash").eq("hash-arr"))
          .getResultList();
      return (Asset) revisions.getFirst();
    });

    assertNotNull(audited);
    assertNotNull(audited.getValidators());
    assertEquals(validators, Arrays.asList(audited.getValidators()));
  }

  @Test
  void updateAssetStatus_createsAuditEntry() {
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-upd", "sub/upd", "iss/upd",
            List.of("did:val:1")))
    );

    transactionTemplate.executeWithoutResult(status ->
        assetDao.update("hash-upd", AssetStatus.REVOKED.ordinal())
    );

    List<?> revisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(Asset.class, false, true)
          .add(AuditEntity.property("assetHash").eq("hash-upd"))
          .addOrder(AuditEntity.revisionNumber().asc())
          .getResultList();
    });

    assertNotNull(revisions);
    assertEquals(2, revisions.size());

    Object[] insertRow = (Object[]) revisions.get(0);
    assertEquals(RevisionType.ADD, insertRow[2]);

    Object[] updateRow = (Object[]) revisions.get(1);
    assertEquals(RevisionType.MOD, updateRow[2]);
    Asset updated = (Asset) updateRow[0];
    assertEquals((short) AssetStatus.REVOKED.ordinal(), updated.getStatus());
  }

  @Test
  void deleteAsset_createsAuditEntryWithFullState() {
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-del", "sub/del", "iss/del",
            List.of("did:val:1")))
    );

    transactionTemplate.executeWithoutResult(status ->
        assetDao.delete("hash-del")
    );

    List<?> revisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(Asset.class, false, true)
          .add(AuditEntity.property("assetHash").eq("hash-del"))
          .addOrder(AuditEntity.revisionNumber().asc())
          .getResultList();
    });

    assertNotNull(revisions);
    assertEquals(2, revisions.size());

    Object[] deleteRow = (Object[]) revisions.get(1);
    assertEquals(RevisionType.DEL, deleteRow[2]);
    // store_data_at_delete=true: full state captured
    Asset deleted = (Asset) deleteRow[0];
    assertEquals("hash-del", deleted.getAssetHash());
    assertEquals("sub/del", deleted.getSubjectId());
  }

  @Test
  void multipleTransactions_createSeparateRevisions() {
    // Insert first asset for sub/multi (ACTIVE)
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-multi-1", "sub/multi", "iss/multi",
            List.of("did:val:1")))
    );

    // Insert second asset for same subject — deprecates first, creates new
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-multi-2", "sub/multi", "iss/multi",
            List.of("did:val:1")))
    );

    // First asset should have 2 revisions: ADD + MOD (deprecated)
    List<?> revisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(Asset.class, false, true)
          .add(AuditEntity.property("assetHash").eq("hash-multi-1"))
          .getResultList();
    });

    assertNotNull(revisions);
    assertEquals(2, revisions.size());
    assertEquals(RevisionType.ADD, ((Object[]) revisions.get(0))[2]);
    assertEquals(RevisionType.MOD, ((Object[]) revisions.get(1))[2]);
  }

  @Test
  void getRevisions_returnsChronologicalHistory() {
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-chrono", "sub/chrono", "iss/chrono",
            List.of("did:val:1")))
    );
    transactionTemplate.executeWithoutResult(status ->
        assetDao.update("hash-chrono", AssetStatus.REVOKED.ordinal())
    );

    List<Number> revNumbers = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      // Get the entity's technical ID first
      List<?> results = reader.createQuery()
          .forRevisionsOfEntity(Asset.class, true, true)
          .add(AuditEntity.property("assetHash").eq("hash-chrono"))
          .getResultList();
      Long entityId = ((Asset) results.getFirst()).getId();
      return reader.getRevisions(Asset.class, entityId);
    });

    assertNotNull(revNumbers);
    assertEquals(2, revNumbers.size());
    assertTrue(revNumbers.get(0).intValue() < revNumbers.get(1).intValue(),
        "Revisions should be in chronological order");
  }

  // ===== Schema audit tests =====

  @Test
  void insertSchema_createsAuditEntryWithTerms() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("schema-1", "hash-1", SchemaType.ONTOLOGY,
            "schema content", Set.of("termA", "termB")))
    );

    List<?> schemaRevisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(SchemaFile.class, false, true)
          .add(AuditEntity.property("schemaId").eq("schema-1"))
          .getResultList();
    });

    assertNotNull(schemaRevisions);
    assertEquals(1, schemaRevisions.size());
    Object[] row = (Object[]) schemaRevisions.getFirst();
    assertEquals(RevisionType.ADD, row[2]);

    // Verify schematerms_AUD also has entries
    List<?> termRevisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(SchemaTerm.class, false, true)
          .getResultList();
    });

    assertNotNull(termRevisions);
    assertEquals(2, termRevisions.size());
  }

  @Test
  void updateSchemaTerms_createsAuditEntryForTermChanges() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("schema-upd", "hash-upd", SchemaType.ONTOLOGY,
            "content", Set.of("oldTerm")))
    );

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.update("schema-upd", "new content", Set.of("newTerm"))
    );

    List<?> schemaRevisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(SchemaFile.class, false, true)
          .add(AuditEntity.property("schemaId").eq("schema-upd"))
          .addOrder(AuditEntity.revisionNumber().asc())
          .getResultList();
    });

    assertNotNull(schemaRevisions);
    assertEquals(2, schemaRevisions.size());
    assertEquals(RevisionType.ADD, ((Object[]) schemaRevisions.get(0))[2]);
    assertEquals(RevisionType.MOD, ((Object[]) schemaRevisions.get(1))[2]);
  }

  @Test
  void deleteSchema_cascadesToTermAudit() {
    transactionTemplate.executeWithoutResult(status ->
        schemaDao.insert(new SchemaRecord("schema-del", "hash-del", SchemaType.SHAPE,
            "content", Set.of("termDel")))
    );

    transactionTemplate.executeWithoutResult(status ->
        schemaDao.delete("schema-del")
    );

    List<?> schemaRevisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(SchemaFile.class, false, true)
          .add(AuditEntity.property("schemaId").eq("schema-del"))
          .addOrder(AuditEntity.revisionNumber().asc())
          .getResultList();
    });

    assertNotNull(schemaRevisions);
    assertEquals(2, schemaRevisions.size());
    assertEquals(RevisionType.DEL, ((Object[]) schemaRevisions.get(1))[2]);

    // Verify term deletion also audited
    List<?> termRevisions = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(SchemaTerm.class, false, true)
          .addOrder(AuditEntity.revisionNumber().asc())
          .getResultList();
    });

    assertNotNull(termRevisions);
    assertFalse(termRevisions.isEmpty());
    Object[] lastTermRev = (Object[]) termRevisions.getLast();
    assertEquals(RevisionType.DEL, lastTermRev[2]);
  }

  // ===== AuditReader query tests =====

  @Test
  void findRevision_returnsEntityAtSpecificRevision() {
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-rev", "sub/rev", "iss/rev",
            List.of("did:val:1")))
    );
    transactionTemplate.executeWithoutResult(status ->
        assetDao.update("hash-rev", AssetStatus.REVOKED.ordinal())
    );

    Asset atFirstRevision = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      List<?> results = reader.createQuery()
          .forRevisionsOfEntity(Asset.class, true, true)
          .add(AuditEntity.property("assetHash").eq("hash-rev"))
          .getResultList();
      Long entityId = ((Asset) results.getFirst()).getId();
      List<Number> revisions = reader.getRevisions(Asset.class, entityId);
      return reader.find(Asset.class, entityId, revisions.getFirst());
    });

    assertNotNull(atFirstRevision);
    assertEquals((short) AssetStatus.ACTIVE.ordinal(), atFirstRevision.getStatus());
  }

  @Test
  void getRevisionDate_returnsTimestamp() {
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-ts", "sub/ts", "iss/ts",
            List.of("did:val:1")))
    );

    Instant revisionTimestamp = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      List<?> results = reader.createQuery()
          .forRevisionsOfEntity(Asset.class, false, true)
          .add(AuditEntity.property("assetHash").eq("hash-ts"))
          .getResultList();
      Object[] row = (Object[]) results.getFirst();
      var revEntity = (org.hibernate.envers.DefaultRevisionEntity) row[1];
      return Instant.ofEpochMilli(revEntity.getTimestamp());
    });

    assertNotNull(revisionTimestamp);
    // Timestamp should be recent (within last minute)
    assertTrue(revisionTimestamp.isAfter(Instant.now().minusSeconds(60)));
  }

  // ===== Known gap documentation test =====

  @Test
  void deleteAll_viaJpql_noAuditEntries() {
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(buildAssetRecord("hash-bulk", "sub/bulk", "iss/bulk",
            List.of("did:val:1")))
    );

    // Verify insert was audited
    Integer countBefore = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(Asset.class, false, true)
          .add(AuditEntity.property("assetHash").eq("hash-bulk"))
          .getResultList().size();
    });
    assertEquals(1, countBefore);

    // deleteAll uses JPQL bulk delete — bypasses Envers
    transactionTemplate.executeWithoutResult(status ->
        assetDao.deleteAll()
    );

    // No DEL audit entry created — this is a known gap
    Integer countAfter = transactionTemplate.execute(status -> {
      var reader = AuditReaderFactory.get(entityManager);
      return reader.createQuery()
          .forRevisionsOfEntity(Asset.class, false, true)
          .add(AuditEntity.property("assetHash").eq("hash-bulk"))
          .getResultList().size();
    });
    // Still only 1 (the INSERT) — no DEL entry from bulk delete
    assertEquals(1, countAfter);
  }
}
