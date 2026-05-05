package eu.xfsc.fc.core.dao.validation;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {ValidationResultRepositoryTest.TestConfig.class,
    DatabaseConfig.class, SecurityAuditorAware.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class ValidationResultRepositoryTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private ValidationResultRepository repository;

  @AfterEach
  void cleanUp() {
    repository.deleteAll();
  }

  // --- helper ---

  private ValidationResult buildResult(String[] assetIds, String[] schemaIds,
      boolean conforms, GraphSyncStatus syncStatus) {
    ValidationResult r = new ValidationResult();
    r.setAssetIds(assetIds);
    r.setValidatorIds(schemaIds);
    r.setValidatorType(ValidatorType.SCHEMA);
    r.setConforms(conforms);
    r.setValidatedAt(Instant.parse("2024-06-01T12:00:00Z"));
    r.setContentHash("aabbccdd".repeat(8));
    r.setGraphSyncStatus(syncStatus);
    return r;
  }

  // ===== findByAssetId =====

  @Test
  void findByAssetId_matchingEntry_returnsResultInPage() {
    ValidationResult saved = repository.save(buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"https://example.org/schema/1"},
        true, GraphSyncStatus.SYNCED));

    Page<ValidationResult> page = repository.findByAssetId(
        "https://example.org/asset/1", PageRequest.of(0, 10));

    assertEquals(1, page.getTotalElements());
    assertEquals(saved.getId(), page.getContent().get(0).getId());
  }

  @Test
  void findByAssetId_multiAssetResult_foundByEitherAssetId() {
    repository.save(buildResult(
        new String[]{"https://example.org/asset/A", "https://example.org/asset/B"},
        new String[]{"https://example.org/schema/1"},
        false, GraphSyncStatus.FAILED));

    Page<ValidationResult> byA = repository.findByAssetId(
        "https://example.org/asset/A", PageRequest.of(0, 10));
    Page<ValidationResult> byB = repository.findByAssetId(
        "https://example.org/asset/B", PageRequest.of(0, 10));

    assertEquals(1, byA.getTotalElements(), "Should find by first asset id");
    assertEquals(1, byB.getTotalElements(), "Should find by second asset id");
  }

  @Test
  void findByAssetId_noMatch_returnsEmptyPage() {
    repository.save(buildResult(
        new String[]{"https://example.org/asset/OTHER"},
        new String[]{"ref/1"}, true, GraphSyncStatus.SYNCED));

    Page<ValidationResult> page = repository.findByAssetId(
        "https://example.org/asset/NONE", PageRequest.of(0, 10));

    assertEquals(0, page.getTotalElements());
    assertTrue(page.getContent().isEmpty());
  }

  // ===== @CreatedDate audit =====

  @Test
  void save_createdAtPopulatedByAuditing() {
    ValidationResult saved = repository.save(buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, true, GraphSyncStatus.SYNCED));

    assertNotNull(saved.getCreatedAt(), "createdAt must be set by @CreatedDate");
  }

  // ===== findAll (used by GraphRebuilder) =====

  @Test
  void findAll_emptyRepository_returnsEmptyPage() {
    Page<ValidationResult> page = repository.findAll(PageRequest.of(0, 10));

    assertEquals(0, page.getTotalElements());
    assertTrue(page.getContent().isEmpty());
  }

  @Test
  void findAll_multipleResults_supportsPagination() {
    // Create 5 validation results
    for (int i = 1; i <= 5; i++) {
      repository.save(buildResult(
          new String[]{"https://example.org/asset/" + i},
          new String[]{"https://example.org/schema/1"},
          i % 2 == 0, // alternating conforms
          i % 3 == 0 ? GraphSyncStatus.FAILED : GraphSyncStatus.SYNCED));
    }

    // Page 1: first 2 results
    Page<ValidationResult> page1 = repository.findAll(PageRequest.of(0, 2));
    assertEquals(5, page1.getTotalElements(), "Total should be 5");
    assertEquals(2, page1.getNumberOfElements(), "Page should have 2 elements");
    assertEquals(3, page1.getTotalPages(), "Should have 3 pages (5 / 2 = 2.5 rounds up)");
    assertTrue(page1.hasNext(), "Should have next page");

    // Page 2: next 2 results
    Page<ValidationResult> page2 = repository.findAll(PageRequest.of(1, 2));
    assertEquals(2, page2.getNumberOfElements());
    assertTrue(page2.hasNext());

    // Page 3: last result
    Page<ValidationResult> page3 = repository.findAll(PageRequest.of(2, 2));
    assertEquals(1, page3.getNumberOfElements(), "Last page should have 1 element");
    assertTrue(!page3.hasNext(), "Last page should not have next");
  }

  @Test
  @Transactional
  void markOutdatedByAssetId_existingResults_marksAllOutdatedWithReason() {
    final String assetId = "https://example.org/asset/mark-1";

    repository.save(buildResult(
        new String[]{assetId}, new String[]{"ref/1"}, true, GraphSyncStatus.SYNCED));
    repository.save(buildResult(
        new String[]{assetId, "https://example.org/asset/other"}, new String[]{"ref/2"},
        true, GraphSyncStatus.SYNCED));

    repository.markOutdatedByAssetId(assetId, OutdatedReason.ASSET_UPDATED.name());

    Page<ValidationResult> page = repository.findByAssetId(assetId, PageRequest.of(0, 10));
    assertEquals(2, page.getTotalElements());
    page.getContent().forEach(r -> {
      assertTrue(r.isOutdated(), "Result must be marked outdated");
      assertEquals(OutdatedReason.ASSET_UPDATED, r.getOutdatedReason());
    });
  }

  @Test
  @Transactional
  void markOutdatedByAssetId_alreadyOutdated_isIdempotent() {
    final String assetId = "https://example.org/asset/idem-1";
    repository.save(buildResult(new String[]{assetId}, new String[]{"ref/1"}, true, GraphSyncStatus.SYNCED));

    repository.markOutdatedByAssetId(assetId, OutdatedReason.ASSET_REVOKED.name());
    repository.markOutdatedByAssetId(assetId, OutdatedReason.ASSET_REVOKED.name());

    Page<ValidationResult> page = repository.findByAssetId(assetId, PageRequest.of(0, 10));
    assertEquals(1, page.getTotalElements());
    assertTrue(page.getContent().getFirst().isOutdated());
    assertEquals(OutdatedReason.ASSET_REVOKED, page.getContent().getFirst().getOutdatedReason());
  }

  @Test
  @Transactional
  void deleteByAssetId_existingResults_deletesAll() {
    final String assetId = "https://example.org/asset/delete-1";

    repository.save(buildResult(new String[]{assetId}, new String[]{"ref/1"}, true, GraphSyncStatus.SYNCED));
    repository.save(buildResult(new String[]{assetId}, new String[]{"ref/2"}, false, GraphSyncStatus.FAILED));

    repository.deleteByAssetId(assetId);

    Page<ValidationResult> page = repository.findByAssetId(assetId, PageRequest.of(0, 10));
    assertEquals(0, page.getTotalElements(), "All results for the asset must be deleted");
  }

  @Test
  @Transactional
  void deleteByAssetId_noMatchingResults_noError() {
    assertDoesNotThrow(() -> repository.deleteByAssetId("https://example.org/asset/none"));
  }

  @Test
  void findAll_mixedGraphSyncStatuses_returnsAll() {
    repository.save(buildResult(
        new String[]{"https://example.org/asset/1"},
        new String[]{"ref/1"}, true, GraphSyncStatus.SYNCED));
    repository.save(buildResult(
        new String[]{"https://example.org/asset/2"},
        new String[]{"ref/2"}, false, GraphSyncStatus.FAILED));
    repository.save(buildResult(
        new String[]{"https://example.org/asset/3"},
        new String[]{"ref/3"}, true, GraphSyncStatus.SYNCED));

    Page<ValidationResult> page = repository.findAll(PageRequest.of(0, 10));

    assertEquals(3, page.getTotalElements());
    long failedCount = page.getContent().stream()
        .filter(r -> r.getGraphSyncStatus() == GraphSyncStatus.FAILED)
        .count();
    assertEquals(1, failedCount, "Should include FAILED results for rebuild processing");
  }
}
