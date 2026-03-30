package eu.xfsc.fc.core.dao.cestracker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.service.pubsub.ces.CesTracking;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {CesTrackerDaoTest.TestConfig.class,
    eu.xfsc.fc.core.dao.cestracker.CesTrackerJpaDao.class, DatabaseConfig.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class CesTrackerDaoTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private CesTrackerDao cesTrackerDao;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @AfterEach
  void cleanUp() {
    jdbcTemplate.update("DELETE FROM ces_tracker");
  }

  // --- Helper ---

  private static CesTracking buildTracking(String cesId, String event, Instant createdAt,
      int credProcessed, String credId, String error) {
    return new CesTracking(cesId, event, createdAt, credProcessed, credId, error);
  }

  // ===== insert =====

  @Test
  void insert_newEvent_persistsSuccessfully() {
    Instant createdAt = Instant.parse("2024-06-15T10:30:00Z");
    CesTracking tracking = buildTracking("ces-001", "{\"type\":\"test\"}", createdAt, 5, "cred-1", null);

    cesTrackerDao.insert(tracking);

    CesTracking result = cesTrackerDao.select("ces-001");
    assertNotNull(result);
    assertEquals("ces-001", result.getCesId());
    assertEquals("{\"type\":\"test\"}", result.getEvent());
    assertEquals(createdAt, result.getCreatedAt());
    assertEquals(5, result.getCredProcessed());
    assertEquals("cred-1", result.getCredId());
    assertNull(result.getError());
  }

  @Test
  void insert_duplicateCesId_updatesExistingEntry() {
    Instant createdAt = Instant.parse("2024-06-15T10:30:00Z");
    CesTracking first = buildTracking("ces-dup", "event1", createdAt, 0, null, null);
    cesTrackerDao.insert(first);

    CesTracking duplicate = buildTracking("ces-dup", "event2", createdAt, 1, null, null);
    cesTrackerDao.insert(duplicate);

    CesTracking result = cesTrackerDao.select("ces-dup");
    assertEquals("event2", result.getEvent());
    assertEquals(1, result.getCredProcessed());
  }

  // ===== select =====

  @Test
  void select_existingKey_returnsTracking() {
    Instant createdAt = Instant.parse("2024-06-15T12:00:00Z");
    CesTracking tracking = buildTracking("ces-sel", "event-data", createdAt, 3, "cred-x", "some error");
    cesTrackerDao.insert(tracking);

    CesTracking result = cesTrackerDao.select("ces-sel");

    assertNotNull(result);
    assertEquals("ces-sel", result.getCesId());
    assertEquals("event-data", result.getEvent());
    assertEquals(createdAt, result.getCreatedAt());
    assertEquals(3, result.getCredProcessed());
    assertEquals("cred-x", result.getCredId());
    assertEquals("some error", result.getError());
  }

  @Test
  void select_nonExistentKey_returnsNull() {
    CesTracking result = cesTrackerDao.select("nonexistent");

    assertNull(result);
  }

  @Test
  void select_verifyNullableFields() {
    Instant createdAt = Instant.parse("2024-06-15T12:00:00Z");
    CesTracking tracking = buildTracking("ces-null", "event", createdAt, 0, null, null);
    cesTrackerDao.insert(tracking);

    CesTracking result = cesTrackerDao.select("ces-null");

    assertNotNull(result);
    assertNull(result.getCredId());
    assertNull(result.getError());
  }

  // ===== selectLatest =====

  @Test
  void selectLatest_multipleEntries_returnsLatestByCreatedAt() {
    Instant early = Instant.parse("2024-01-01T00:00:00Z");
    Instant middle = Instant.parse("2024-06-15T00:00:00Z");
    Instant late = Instant.parse("2024-12-31T23:59:59Z");
    cesTrackerDao.insert(buildTracking("ces-early", "e1", early, 0, null, null));
    cesTrackerDao.insert(buildTracking("ces-late", "e3", late, 0, null, null));
    cesTrackerDao.insert(buildTracking("ces-middle", "e2", middle, 0, null, null));

    CesTracking result = cesTrackerDao.selectLatest();

    assertNotNull(result);
    assertEquals("ces-late", result.getCesId());
  }

  @Test
  void selectLatest_emptyTable_returnsNull() {
    CesTracking result = cesTrackerDao.selectLatest();

    assertNull(result);
  }

  @Test
  void selectLatest_singleEntry_returnsThatEntry() {
    Instant createdAt = Instant.parse("2024-06-15T10:00:00Z");
    cesTrackerDao.insert(buildTracking("ces-only", "single", createdAt, 1, null, null));

    CesTracking result = cesTrackerDao.selectLatest();

    assertNotNull(result);
    assertEquals("ces-only", result.getCesId());
  }
}
