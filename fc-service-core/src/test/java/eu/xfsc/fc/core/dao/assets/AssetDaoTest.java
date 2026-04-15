package eu.xfsc.fc.core.dao.assets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectHashRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectStatusRecord;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {AssetDaoTest.TestConfig.class, AssetJpaDao.class, AssetAuditRepository.class, DatabaseConfig.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AssetDaoTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private AssetDao assetDao;

  @AfterEach
  void cleanUp() {
    assetDao.deleteAll();
  }

  // --- Helper ---

  private static AssetRecord buildRecord(String hash, String subjectId, String issuer,
      Instant uploadTime, Instant statusTime, Instant expirationTime,
      AssetStatus status, String content, List<String> validators,
      String contentType, Long fileSize, String originalFilename) {
    return AssetRecord.builder()
        .assetHash(hash)
        .id(subjectId)
        .issuer(issuer)
        .uploadTime(uploadTime)
        .statusTime(statusTime)
        .expirationTime(expirationTime)
        .status(status)
        .content(content == null ? null : new ContentAccessorDirect(content))
        .validatorDids(validators)
        .contentType(contentType)
        .fileSize(fileSize)
        .originalFilename(originalFilename)
        .build();
  }

  private static AssetRecord buildSimpleRecord(String hash, String subjectId, String issuer,
      Instant statusTime) {
    return buildRecord(hash, subjectId, issuer,
        Instant.parse("2024-01-01T00:00:00Z"), statusTime, null,
        AssetStatus.ACTIVE, "content-" + hash, List.of("did:validator:1"),
        "application/ld+json", 100L, "file.jsonld");
  }

  // ===== select(hash) =====

  @Test
  void select_existingHash_returnsRecord() {
    Instant uploadTime = Instant.parse("2024-01-01T10:00:00Z");
    Instant statusTime = Instant.parse("2024-01-01T10:00:00Z");
    Instant expirationTime = Instant.parse("2025-01-01T00:00:00Z");
    AssetRecord record = buildRecord("hash001", "subject/1", "issuer/1",
        uploadTime, statusTime, expirationTime,
        AssetStatus.ACTIVE, "test content", List.of("did:val:1", "did:val:2"),
        "application/ld+json", 1024L, "test.jsonld");
    assetDao.insert(record);

    AssetRecord result = assetDao.select("hash001");

    assertNotNull(result);
    assertEquals("hash001", result.getAssetHash());
    assertEquals("subject/1", result.getId());
    assertEquals("issuer/1", result.getIssuer());
    assertEquals(AssetStatus.ACTIVE, result.getStatus());
    assertEquals(uploadTime, result.getUploadDatetime());
    assertEquals(statusTime, result.getStatusDatetime());
    assertEquals(expirationTime, result.getExpirationTime());
    assertEquals("test content", result.getContent());
    assertEquals("application/ld+json", result.getContentType());
    assertEquals(1024L, result.getFileSize());
    assertEquals("test.jsonld", result.getOriginalFilename());
  }

  @Test
  void select_nonExistentHash_returnsNull() {
    AssetRecord result = assetDao.select("nonexistent");

    assertNull(result);
  }

  @Test
  void select_verifyValidatorsArrayMapping() {
    List<String> validators = List.of("did:val:1", "did:val:2", "did:val:3");
    AssetRecord record = buildRecord("hash002", "subject/2", "issuer/2",
        Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), null,
        AssetStatus.ACTIVE, "content", validators,
        "application/ld+json", 100L, "file.jsonld");
    assetDao.insert(record);

    AssetRecord result = assetDao.select("hash002");

    assertNotNull(result);
    assertEquals(validators, result.getValidatorDids());
  }

  // ===== selectByFilter =====

  @Test
  void selectByFilter_emptyFilter_returnsAll() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/3", Instant.parse("2024-01-03T00:00:00Z")));

    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(new AssetFilter(), true, true);

    assertEquals(3, results.getTotalCount());
    assertEquals(3, results.getResults().size());
  }

  @Test
  void selectByFilter_issuerFilter_returnsMatching() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/1", Instant.parse("2024-01-03T00:00:00Z")));

    AssetFilter filter = new AssetFilter();
    filter.setIssuers(List.of("iss/1"));
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(2, results.getTotalCount());
    assertEquals(2, results.getResults().size());
    assertTrue(results.getResults().stream().allMatch(r -> "iss/1".equals(r.getIssuer())));
  }

  @Test
  void selectByFilter_statusFilter_returnsMatching() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));
    // Revoke hash-a to get a non-ACTIVE record
    assetDao.update("hash-a", AssetStatus.REVOKED.ordinal());

    AssetFilter filter = new AssetFilter();
    filter.setStatuses(List.of(AssetStatus.ACTIVE));
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(1, results.getTotalCount());
    assertEquals("hash-b", results.getResults().getFirst().getAssetHash());
  }

  @Test
  void selectByFilter_validatorsOverlapFilter_returnsMatching() {
    AssetRecord r1 = buildRecord("hash-a", "sub/1", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), null,
        AssetStatus.ACTIVE, "c1", List.of("did:val:A", "did:val:B"),
        "application/ld+json", 100L, "f1.jsonld");
    AssetRecord r2 = buildRecord("hash-b", "sub/2", "iss/2",
        Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"), null,
        AssetStatus.ACTIVE, "c2", List.of("did:val:C"),
        "application/ld+json", 100L, "f2.jsonld");
    assetDao.insert(r1);
    assetDao.insert(r2);

    AssetFilter filter = new AssetFilter();
    filter.setValidators(List.of("did:val:A", "did:val:X"));
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(1, results.getTotalCount());
    assertEquals("hash-a", results.getResults().getFirst().getAssetHash());
  }

  @Test
  void selectByFilter_uploadTimeRange_returnsMatching() {
    AssetRecord r1 = buildRecord("hash-a", "sub/1", "iss/1",
        Instant.parse("2024-01-10T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), null,
        AssetStatus.ACTIVE, "c1", List.of("did:val:1"),
        "application/ld+json", 100L, "f1.jsonld");
    AssetRecord r2 = buildRecord("hash-b", "sub/2", "iss/2",
        Instant.parse("2024-02-10T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"), null,
        AssetStatus.ACTIVE, "c2", List.of("did:val:1"),
        "application/ld+json", 100L, "f2.jsonld");
    assetDao.insert(r1);
    assetDao.insert(r2);

    AssetFilter filter = new AssetFilter();
    filter.setUploadTimeRange(Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-31T00:00:00Z"));
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(1, results.getTotalCount());
    assertEquals("hash-a", results.getResults().getFirst().getAssetHash());
  }

  @Test
  void selectByFilter_statusTimeRange_returnsMatching() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-10T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-02-10T00:00:00Z")));

    AssetFilter filter = new AssetFilter();
    filter.setStatusTimeRange(Instant.parse("2024-02-01T00:00:00Z"), Instant.parse("2024-02-28T00:00:00Z"));
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(1, results.getTotalCount());
    assertEquals("hash-b", results.getResults().getFirst().getAssetHash());
  }

  @Test
  void selectByFilter_hashFilter_returnsMatching() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/3", Instant.parse("2024-01-03T00:00:00Z")));

    AssetFilter filter = new AssetFilter();
    filter.setHashes(List.of("hash-a", "hash-c"));
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(2, results.getTotalCount());
    List<String> hashes = results.getResults().stream().map(AssetRecord::getAssetHash).toList();
    assertTrue(hashes.contains("hash-a"));
    assertTrue(hashes.contains("hash-c"));
  }

  @Test
  void selectByFilter_subjectIdFilter_returnsMatching() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));

    AssetFilter filter = new AssetFilter();
    filter.setIds(List.of("sub/2"));
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(1, results.getTotalCount());
    assertEquals("sub/2", results.getResults().getFirst().getId());
  }

  @Test
  void selectByFilter_combinedFilters_returnsIntersection() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/1", Instant.parse("2024-01-02T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/2", Instant.parse("2024-01-03T00:00:00Z")));

    AssetFilter filter = new AssetFilter();
    filter.setIssuers(List.of("iss/1"));
    filter.setIds(List.of("sub/2"));
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(1, results.getTotalCount());
    assertEquals("hash-b", results.getResults().getFirst().getAssetHash());
  }

  @Test
  void selectByFilter_withMetaFalse_nullifiesMetaFields() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));

    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(new AssetFilter(), false, true);

    assertEquals(1, results.getResults().size());
    AssetRecord result = results.getResults().getFirst();
    assertEquals("hash-a", result.getAssetHash());
    assertNull(result.getId());
    assertNull(result.getIssuer());
    assertNull(result.getUploadDatetime());
    assertNull(result.getStatusDatetime());
    assertNull(result.getExpirationTime());
    assertNull(result.getValidatorDids());
    assertNull(result.getContentType());
    assertNull(result.getFileSize());
    assertNull(result.getOriginalFilename());
    // Content should still be present
    assertNotNull(result.getContent());
  }

  @Test
  void selectByFilter_withContentFalse_nullifiesContent() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));

    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(new AssetFilter(), true, false);

    assertEquals(1, results.getResults().size());
    AssetRecord result = results.getResults().getFirst();
    assertEquals("hash-a", result.getAssetHash());
    assertNotNull(result.getId());
    assertNull(result.getContentAccessor());
  }

  @Test
  void selectByFilter_limitAndOffset_returnsPaginated() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/3", Instant.parse("2024-01-03T00:00:00Z")));

    AssetFilter filter = new AssetFilter();
    filter.setLimit(1);
    filter.setOffset(1);
    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(filter, true, true);

    assertEquals(3, results.getTotalCount());
    assertEquals(1, results.getResults().size());
  }

  @Test
  void selectByFilter_orderByStatusTimeDesc() {
    Instant early = Instant.parse("2024-01-01T00:00:00Z");
    Instant middle = Instant.parse("2024-01-02T00:00:00Z");
    Instant late = Instant.parse("2024-01-03T00:00:00Z");
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", early));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", late));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/3", middle));

    PaginatedResults<AssetRecord> results = assetDao.selectByFilter(new AssetFilter(), true, true);

    List<AssetRecord> list = results.getResults();
    assertEquals("hash-b", list.getFirst().getAssetHash());
    assertEquals("hash-c", list.get(1).getAssetHash());
    assertEquals("hash-a", list.get(2).getAssetHash());
  }

  // ===== selectHashes =====

  @Test
  void selectHashes_firstPage_returnsUpToLimit() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/3", Instant.parse("2024-01-03T00:00:00Z")));

    // chunks=1, chunkId=0 means all hashes land in one chunk
    List<String> hashes = assetDao.selectHashes(null, 2, 1, 0);

    assertEquals(2, hashes.size());
    // Ordered ascending by asset_hash
    assertTrue(hashes.getFirst().compareTo(hashes.get(1)) < 0);
  }

  @Test
  void selectHashes_withStartHash_returnsCursorPage() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/3", Instant.parse("2024-01-03T00:00:00Z")));

    List<String> hashes = assetDao.selectHashes("hash-a", 10, 1, 0);

    // Should return hashes > "hash-a", so hash-b and hash-c
    assertEquals(2, hashes.size());
    assertTrue(hashes.stream().allMatch(h -> h.compareTo("hash-a") > 0));
  }

  @Test
  void selectHashes_chunkFiltering_returnsOnlyMatchingChunk() {
    // Insert enough records to make chunk filtering meaningful
    for (int i = 0; i < 10; i++) {
      assetDao.insert(buildSimpleRecord(
          String.format("hash-%03d", i), "sub/" + i, "iss/" + i,
          Instant.parse("2024-01-01T00:00:00Z").plus(i, ChronoUnit.HOURS)));
    }

    List<String> chunk0 = assetDao.selectHashes(null, 100, 2, 0);
    List<String> chunk1 = assetDao.selectHashes(null, 100, 2, 1);

    // Together they should cover all 10 records
    assertEquals(10, chunk0.size() + chunk1.size());
    // Each chunk should have at least some records (with 10 records, very unlikely to all land in one chunk)
    assertTrue(chunk0.size() > 0);
    assertTrue(chunk1.size() > 0);
  }

  // ===== selectExpiredHashes =====

  @Test
  void selectExpiredHashes_mixedExpiration_returnsOnlyExpired() {
    Instant past = Instant.now().minus(1, ChronoUnit.DAYS);
    Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
    AssetRecord expired = buildRecord("hash-exp", "sub/1", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), past,
        AssetStatus.ACTIVE, "c1", List.of("did:val:1"),
        "application/ld+json", 100L, "f1.jsonld");
    AssetRecord notExpired = buildRecord("hash-ok", "sub/2", "iss/2",
        Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"), future,
        AssetStatus.ACTIVE, "c2", List.of("did:val:1"),
        "application/ld+json", 100L, "f2.jsonld");
    assetDao.insert(expired);
    assetDao.insert(notExpired);

    List<String> expiredHashes = assetDao.selectExpiredHashes();

    assertEquals(1, expiredHashes.size());
    assertEquals("hash-exp", expiredHashes.getFirst());
  }

  @Test
  void selectExpiredHashes_noneExpired_returnsEmpty() {
    Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
    AssetRecord record = buildRecord("hash-ok", "sub/1", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), future,
        AssetStatus.ACTIVE, "c1", List.of("did:val:1"),
        "application/ld+json", 100L, "f1.jsonld");
    assetDao.insert(record);

    List<String> expiredHashes = assetDao.selectExpiredHashes();

    assertTrue(expiredHashes.isEmpty());
  }

  // ===== insert =====

  @Test
  void insert_newSubject_returnsNullOldHash() {
    AssetRecord record = buildSimpleRecord("hash-new", "sub/new", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z"));

    SubjectHashRecord result = assetDao.insert(record);

    assertNull(result.assetHash());
  }

  @Test
  void insert_existingActiveSubject_updatesInPlaceAndReturnsNewHash() {
    AssetRecord first = buildSimpleRecord("hash-old", "sub/1", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z"));
    assetDao.insert(first);

    AssetRecord second = buildRecord("hash-new", "sub/1", "iss/1",
        Instant.parse("2024-01-02T00:00:00Z"), Instant.parse("2024-01-02T00:00:00Z"), null,
        AssetStatus.ACTIVE, "new content", List.of("did:val:1"),
        "application/ld+json", 200L, "new.jsonld");

    SubjectHashRecord result = assetDao.insert(second);

    // In-place update: subjectId is returned, hash reflects the updated row
    assertEquals("sub/1", result.subjectId());
    // Old hash no longer exists as a live row — Envers preserves it in assets_aud
    assertNull(assetDao.select("hash-old"));
    // New row has the updated hash and remains ACTIVE
    AssetRecord updated = assetDao.select("hash-new");
    assertNotNull(updated);
    assertEquals(AssetStatus.ACTIVE, updated.getStatus());
  }

  @Test
  void insert_verifyAllFieldsPersisted() {
    Instant uploadTime = Instant.parse("2024-06-15T10:30:00Z");
    Instant statusTime = Instant.parse("2024-06-15T10:30:00Z");
    Instant expirationTime = Instant.parse("2025-06-15T00:00:00Z");
    List<String> validators = List.of("did:val:A", "did:val:B");
    AssetRecord record = buildRecord("hash-full", "sub/full", "iss/full",
        uploadTime, statusTime, expirationTime,
        AssetStatus.ACTIVE, "full content body", validators,
        "application/pdf", 999999L, "document.pdf");
    assetDao.insert(record);

    AssetRecord result = assetDao.select("hash-full");

    assertEquals("hash-full", result.getAssetHash());
    assertEquals("sub/full", result.getId());
    assertEquals("iss/full", result.getIssuer());
    assertEquals(uploadTime, result.getUploadDatetime());
    assertEquals(statusTime, result.getStatusDatetime());
    assertEquals(expirationTime, result.getExpirationTime());
    assertEquals(AssetStatus.ACTIVE, result.getStatus());
    assertEquals("full content body", result.getContent());
    assertEquals(validators, result.getValidatorDids());
    assertEquals("application/pdf", result.getContentType());
    assertEquals(999999L, result.getFileSize());
    assertEquals("document.pdf", result.getOriginalFilename());
  }

  @Test
  void insert_nullExpirationTime_persistsAsNull() {
    AssetRecord record = buildRecord("hash-noexp", "sub/noexp", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), null,
        AssetStatus.ACTIVE, "content", List.of("did:val:1"),
        "application/ld+json", 100L, "f.jsonld");
    assetDao.insert(record);

    AssetRecord result = assetDao.select("hash-noexp");

    assertNull(result.getExpirationTime());
  }

  @Test
  void insert_nullContent_persistsAsNull() {
    AssetRecord record = buildRecord("hash-nocontent", "sub/nocontent", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z"), Instant.parse("2024-01-01T00:00:00Z"), null,
        AssetStatus.ACTIVE, null, List.of("did:val:1"),
        "application/pdf", 5000L, "doc.pdf");
    assetDao.insert(record);

    AssetRecord result = assetDao.select("hash-nocontent");

    assertNull(result.getContentAccessor());
  }

  // ===== update =====

  @Test
  void update_activeToRevoked_returnsSubjectAndNullOldStatus() {
    assetDao.insert(buildSimpleRecord("hash-upd", "sub/upd", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z")));

    SubjectStatusRecord result = assetDao.update("hash-upd", AssetStatus.REVOKED.ordinal());

    assertEquals("sub/upd", result.subjectId());
    assertNull(result.status());

    // Verify status actually changed
    AssetRecord updated = assetDao.select("hash-upd");
    assertEquals(AssetStatus.REVOKED, updated.getStatus());
  }

  @Test
  void update_alreadyNonActive_returnsNullSubjectAndOldStatus() {
    assetDao.insert(buildSimpleRecord("hash-rev", "sub/rev", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z")));
    // First revoke it
    assetDao.update("hash-rev", AssetStatus.REVOKED.ordinal());

    // Try to update again
    SubjectStatusRecord result = assetDao.update("hash-rev", AssetStatus.EOL.ordinal());

    assertNull(result.subjectId());
    assertEquals(AssetStatus.REVOKED.ordinal(), result.status());
  }

  @Test
  void update_nonExistentHash_throwsEmptyResult() {
    // CTE returns no rows when hash doesn't exist — queryForObject throws
    assertThrows(EmptyResultDataAccessException.class,
        () -> assetDao.update("nonexistent", AssetStatus.REVOKED.ordinal()));
  }

  // ===== delete =====

  @Test
  void delete_existingHash_returnsSubjectAndStatus() {
    assetDao.insert(buildSimpleRecord("hash-del", "sub/del", "iss/1",
        Instant.parse("2024-01-01T00:00:00Z")));

    SubjectStatusRecord result = assetDao.delete("hash-del");

    assertNotNull(result);
    assertEquals("sub/del", result.subjectId());
    assertEquals(AssetStatus.ACTIVE.ordinal(), result.status());

    // Verify actually deleted
    assertNull(assetDao.select("hash-del"));
  }

  @Test
  void delete_nonExistentHash_returnsNull() {
    SubjectStatusRecord result = assetDao.delete("nonexistent");

    assertNull(result);
  }

  // ===== deleteAll =====

  @Test
  void deleteAll_multipleRows_returnsCount() {
    assetDao.insert(buildSimpleRecord("hash-a", "sub/1", "iss/1", Instant.parse("2024-01-01T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-b", "sub/2", "iss/2", Instant.parse("2024-01-02T00:00:00Z")));
    assetDao.insert(buildSimpleRecord("hash-c", "sub/3", "iss/3", Instant.parse("2024-01-03T00:00:00Z")));

    int count = assetDao.deleteAll();

    assertEquals(3, count);
  }

  @Test
  void deleteAll_emptyTable_returnsZero() {
    int count = assetDao.deleteAll();

    assertEquals(0, count);
  }

}
