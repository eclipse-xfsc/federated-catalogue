package eu.xfsc.fc.core.dao.revalidator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

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
import eu.xfsc.fc.core.dao.schemas.SchemaFile;
import eu.xfsc.fc.core.dao.schemas.SchemaFileRepository;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {
    RevalidatorChunksDaoTest.TestConfig.class,
    RevalidatorChunksJpaDao.class,
    DatabaseConfig.class
})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class RevalidatorChunksDaoTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private RevalidatorChunksDao revalidatorChunksDao;

  @Autowired
  private SchemaFileRepository schemaFileRepository;

  @Autowired
  private JdbcTemplate jdbc;

  @AfterEach
  void cleanUp() {
    jdbc.update("DELETE FROM revalidatorchunks");
    schemaFileRepository.deleteAll();
  }

  // --- Helpers ---

  private void insertChunk(int chunkId, Instant lastcheck) {
    jdbc.update("INSERT INTO revalidatorchunks(chunkid, lastcheck) VALUES (?, ?)", chunkId, java.sql.Timestamp.from(lastcheck));
  }

  private SchemaFile insertSchemaFile(String schemaId, SchemaType type, Instant updateTime) {
    SchemaFile entity = new SchemaFile();
    entity.setSchemaId(schemaId);
    entity.setNameHash("hash-" + schemaId);
    entity.setCreatedAt(updateTime);
    entity.setModifiedAt(updateTime);
    entity.setType(type);
    entity.setContent("content-" + schemaId);
    return schemaFileRepository.saveAndFlush(entity);
  }

  private List<Integer> allChunkIds() {
    return jdbc.queryForList("SELECT chunkid FROM revalidatorchunks ORDER BY chunkid", Integer.class);
  }

  private Instant getLastcheck(int chunkId) {
    return jdbc.queryForObject(
        "SELECT lastcheck FROM revalidatorchunks WHERE chunkid = ?",
        (rs, rowNum) -> rs.getTimestamp("lastcheck").toInstant(),
        chunkId);
  }

  // ===== findChunkForWork =====

  @Test
  void findChunkForWork_staleChunk_returnsChunkIdAndUpdatesLastcheck() {
    Instant oldTime = Instant.parse("2020-01-01T00:00:00Z");
    Instant schemaUpdateTime = Instant.parse("2024-01-01T00:00:00Z");
    insertChunk(0, oldTime);
    insertSchemaFile("schema-1", SchemaType.SHAPE, schemaUpdateTime);

    int result = revalidatorChunksDao.findChunkForWork("SHAPE");

    assertEquals(0, result);
    Instant updatedLastcheck = getLastcheck(0);
    assertTrue(updatedLastcheck.isAfter(oldTime), "lastcheck should be updated to now()");
  }

  @Test
  void findChunkForWork_noStaleChunks_returnsMinusOne() {
    Instant recentTime = Instant.parse("2099-01-01T00:00:00Z");
    insertChunk(0, recentTime);
    insertSchemaFile("schema-1", SchemaType.SHAPE, Instant.parse("2024-01-01T00:00:00Z"));

    int result = revalidatorChunksDao.findChunkForWork("SHAPE");

    assertEquals(-1, result);
  }

  @Test
  void findChunkForWork_emptyChunkTable_returnsMinusOne() {
    insertSchemaFile("schema-1", SchemaType.SHAPE, Instant.parse("2024-01-01T00:00:00Z"));

    int result = revalidatorChunksDao.findChunkForWork("SHAPE");

    assertEquals(-1, result);
  }

  @Test
  void findChunkForWork_multipleStaleChunks_returnsLowestChunkId() {
    Instant oldTime = Instant.parse("2020-01-01T00:00:00Z");
    Instant schemaUpdateTime = Instant.parse("2024-01-01T00:00:00Z");
    insertChunk(2, oldTime);
    insertChunk(5, oldTime);
    insertChunk(1, oldTime);
    insertSchemaFile("schema-1", SchemaType.SHAPE, schemaUpdateTime);

    int result = revalidatorChunksDao.findChunkForWork("SHAPE");

    assertEquals(1, result);
  }

  @Test
  void findChunkForWork_noSchemaFile_returnsMinusOne() {
    insertChunk(0, Instant.parse("2020-01-01T00:00:00Z"));

    int result = revalidatorChunksDao.findChunkForWork("SHAPE");

    assertEquals(-1, result);
  }

  // ===== checkChunkTable =====

  @Test
  void checkChunkTable_emptyTable_insertsAllChunks() {
    revalidatorChunksDao.checkChunkTable(3);

    List<Integer> ids = allChunkIds();
    assertEquals(List.of(0, 1, 2), ids);
  }

  @Test
  void checkChunkTable_growFromExisting_insertsOnlyNewChunks() {
    insertChunk(0, Instant.now());
    insertChunk(1, Instant.now());

    revalidatorChunksDao.checkChunkTable(4);

    List<Integer> ids = allChunkIds();
    assertEquals(List.of(0, 1, 2, 3), ids);
  }

  @Test
  void checkChunkTable_shrinkFromExisting_deletesExcessChunks() {
    insertChunk(0, Instant.now());
    insertChunk(1, Instant.now());
    insertChunk(2, Instant.now());
    insertChunk(3, Instant.now());

    revalidatorChunksDao.checkChunkTable(2);

    List<Integer> ids = allChunkIds();
    assertEquals(List.of(0, 1), ids);
  }

  @Test
  void checkChunkTable_exactSize_noChanges() {
    Instant fixedTime = Instant.parse("2024-06-01T00:00:00Z");
    insertChunk(0, fixedTime);
    insertChunk(1, fixedTime);

    revalidatorChunksDao.checkChunkTable(2);

    List<Integer> ids = allChunkIds();
    assertEquals(List.of(0, 1), ids);
    // Verify lastcheck was not changed
    assertEquals(fixedTime, getLastcheck(0));
    assertEquals(fixedTime, getLastcheck(1));
  }

  @Test
  void checkChunkTable_zeroInstances_deletesAll() {
    insertChunk(0, Instant.now());
    insertChunk(1, Instant.now());

    revalidatorChunksDao.checkChunkTable(0);

    List<Integer> ids = allChunkIds();
    assertTrue(ids.isEmpty());
  }

  // ===== resetChunkTableTimes =====

  @Test
  void resetChunkTableTimes_resetsAllTimestamps() {
    Instant epoch = Instant.parse("2000-01-01T00:00:00Z");
    insertChunk(0, Instant.parse("2024-01-01T00:00:00Z"));
    insertChunk(1, Instant.parse("2024-06-15T12:00:00Z"));
    insertChunk(2, Instant.parse("2025-01-01T00:00:00Z"));

    revalidatorChunksDao.resetChunkTableTimes();

    assertEquals(epoch, getLastcheck(0));
    assertEquals(epoch, getLastcheck(1));
    assertEquals(epoch, getLastcheck(2));
  }

  @Test
  void resetChunkTableTimes_emptyTable_noException() {
    revalidatorChunksDao.resetChunkTableTimes();
    // No exception = success
  }
}
