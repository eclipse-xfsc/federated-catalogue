package eu.xfsc.fc.core.dao.revalidator;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class RevalidatorChunkRepositoryCustomImpl implements RevalidatorChunkRepositoryCustom {

  private final EntityManager entityManager;

  @Override
  public int findChunkForWork(String schemaType) {
    String sql = """
        UPDATE revalidatorchunks SET lastcheck = now()
        WHERE chunkid = (SELECT chunkid FROM revalidatorchunks
          WHERE lastcheck < (SELECT created_at FROM schemafiles WHERE type = ?1 ORDER BY created_at DESC LIMIT 1)
          ORDER BY chunkid LIMIT 1)
        RETURNING chunkid""";

    Query query = entityManager.createNativeQuery(sql);
    query.setParameter(1, schemaType);

    @SuppressWarnings("unchecked")
    List<Integer> result = query.getResultList();
    log.debug("findChunkForWork; found chunk: {}", result);

    if (!result.isEmpty()) {
      return result.getFirst();
    }
    return -1;
  }

  @Override
  public void checkChunkTable(int instanceCount) {
    log.debug("checkChunkTable.enter; instanceCount: {}", instanceCount);
    entityManager.createNativeQuery("LOCK TABLE revalidatorchunks").executeUpdate();

    Integer maxChunkObject = (Integer) entityManager
        .createNativeQuery("SELECT max(chunkid) FROM revalidatorchunks")
        .getSingleResult();
    int maxChunk = maxChunkObject == null ? -1 : maxChunkObject;

    if (maxChunk + 1 < instanceCount) {
      int firstChunkId = maxChunk + 1;
      int lastChunkId = instanceCount - 1;
      log.debug("checkChunkTable; adding chunks {} to {} to chunk table", firstChunkId, lastChunkId);
      int cnt = entityManager
          .createNativeQuery("INSERT INTO revalidatorchunks(chunkid) SELECT generate_series(?1, ?2)")
          .setParameter(1, firstChunkId)
          .setParameter(2, lastChunkId)
          .executeUpdate();
      log.debug("checkChunkTable.exit; checking chunk table done, inserted: {}", cnt);
    }

    if (maxChunk >= instanceCount) {
      log.debug("checkChunkTable; Removing chunks >= {} from chunk table", instanceCount);
      int cnt = entityManager
          .createNativeQuery("DELETE FROM revalidatorchunks WHERE chunkid >= ?1")
          .setParameter(1, instanceCount)
          .executeUpdate();
      log.debug("checkChunkTable.exit; checking chunk table done, deleted: {}", cnt);
    }
  }

  @Override
  public void resetChunkTableTimes() {
    log.debug("resetChunkTableTimes.enter; Resetting chunk table times...");
    entityManager.createNativeQuery("LOCK TABLE revalidatorchunks").executeUpdate();
    int cnt = entityManager
        .createNativeQuery("UPDATE revalidatorchunks SET lastcheck = ?1")
        .setParameter(1, Timestamp.from(Instant.parse("2000-01-01T00:00:00Z")))
        .executeUpdate();
    log.debug("resetChunkTableTimes.exit; resetting chunk table times done, updated: {}", cnt);
  }
}
