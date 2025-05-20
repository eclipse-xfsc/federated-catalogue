package eu.xfsc.fc.core.dao.impl;

import static java.sql.Types.*;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.core.dao.RevalidatorChunksDao;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class RevalidatorChunksDaoImpl implements RevalidatorChunksDao {
	
	@Autowired
	private JdbcTemplate jdbc;
	
	public Connection getConnection() throws SQLException {
		return jdbc.getDataSource().getConnection();
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
	public int findChunkForWork(int schemaType) {
		int chunkId = -1;
	    final String query = """
    		update revalidatorchunks set lastcheck = now() 
    		where chunkid = (select chunkid from revalidatorchunks 
    		  where lastcheck < (select updatetime from schemafiles where type = ? order by updatetime desc limit 1) 
	          order by chunkid limit 1)
	        returning chunkid""";

	    List<Integer> result = jdbc.queryForList(query, new Object[] {schemaType}, new int[] {INTEGER}, Integer.class);
        log.debug("findChunkForWork; found chunk: {}", result);
        if (!result.isEmpty()) {
	        chunkId = result.get(0);
	    }
	    return chunkId;		
	}
	
	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void checkChunkTable(int instanceCount) {
	    log.debug("checkChunkTable.enter; instenceCount: {}", instanceCount);
        jdbc.execute("lock table revalidatorchunks");
        Integer maxChunkObject = jdbc.queryForObject("select max(chunkid) from revalidatorchunks", Integer.class);
        int maxChunk = maxChunkObject == null ? -1 : maxChunkObject;
        if (maxChunk + 1 < instanceCount) {
            int firstChunkId = maxChunk + 1;
            int lastChunkId = instanceCount - 1;
            log.debug("checkChunkTable; adding chunks {} to {} to chunk table", firstChunkId, lastChunkId);
            int cnt = jdbc.update("insert into revalidatorchunks(chunkid) select generate_series(?, ?)", firstChunkId, lastChunkId);
    		log.debug("checkChunkTable.exit; checking chunk table done, inserted: {}", cnt);
        }
        if (maxChunk >= instanceCount) {
            log.debug("checkChunkTable; Removing chunks >= {} from chunk table", instanceCount);
            int cnt = jdbc.update("delete from revalidatorchunks where chunkid >= ?", instanceCount);
    		log.debug("checkChunkTable.exit; checking chunk table done, deleted: {}", cnt);
        }
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void resetChunkTableTimes() {
		log.debug("resetChunkTableTimes.enter; Resetting chunk table times...");
        jdbc.execute("lock table revalidatorchunks");
        int cnt = jdbc.update("update revalidatorchunks set lastcheck = ?", new Object[] {Timestamp.from(Instant.parse("2000-01-01T00:00:00Z"))}, new int[] {TIMESTAMP});
	    log.debug("resetChunkTableTimes.exit; resetting chunk table times done, updated: {}", cnt);
    }
	
}
