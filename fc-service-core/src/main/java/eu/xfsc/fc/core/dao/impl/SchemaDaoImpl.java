package eu.xfsc.fc.core.dao.impl;

import static java.sql.Types.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;

import org.bouncycastle.util.Arrays;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.dao.SchemaDao;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaDaoImpl implements SchemaDao {

	private final JdbcTemplate jdbc;

	@Override
	public int getSchemaCount() {
		String sql = "select count(*) from schemafiles";
		return jdbc.queryForObject(sql, Integer.class);
	}

	@Override
	public Optional<SchemaRecord> select(String schemaId) {
		String sql = "select schemaId, nameHash, type, uploadTime, updateTime, content from schemafiles where schemaid = ?";
		try {
			return Optional.ofNullable(jdbc.queryForObject(sql, new Object[]{schemaId}, new int[]{VARCHAR},
					(rs, rowNum) -> new SchemaRecord(rs.getString(1), rs.getString(2), SchemaType.valueOf(rs.getString(3)), rs.getTimestamp(4).toInstant(), rs.getTimestamp(5).toInstant(), rs.getString(6), null)));
		} catch (EmptyResultDataAccessException ex) {
			return Optional.empty();
		}
	}

	@Override
	public Map<String, Collection<String>> selectSchemas() {
	    String sql = "select s.type, s.schemaId from schemafiles s";
	    return jdbc.query(sql, new SchemaAggregateExtractor());
	}

	@Override
	public Map<String, Collection<String>> selectSchemasByTerm(String term) {
	    String sql = "select s.type, s.schemaId from schemafiles s join schematerms t on t.schemaid = s.schemaid where t.term = ?";
	    return jdbc.query(sql, new Object[] {term}, new int[] {VARCHAR}, new SchemaAggregateExtractor());
	}

	@Override
	public boolean insert(SchemaRecord sr) {
		log.debug("insert.enter; got schema: {}", sr.getId());
		String insertSchemaSql = "insert into schemafiles(schemaId, nameHash, type, uploadTime, updateTime, content)"
				+ " values(?, ?, ?, ?, ?, ?)";
		int rows = jdbc.update(insertSchemaSql, sr.getId(), sr.nameHash(), sr.type().name(),
				Timestamp.from(sr.updateTime()), Timestamp.from(sr.updateTime()), sr.content());
		if (rows > 0 && sr.terms() != null && !sr.terms().isEmpty()) {
			String insertTermsSql = "insert into schematerms(term, schemaid) select * from unnest(?::varchar[], ?::varchar[])";
			String[] schemaIds = new String[sr.terms().size()];
			Arrays.fill(schemaIds, sr.getId());
			String[] terms = sr.terms().toArray(new String[0]);
			jdbc.update(insertTermsSql, (Object) terms, (Object) schemaIds);
		}
		log.debug("insert.exit; inserted: {}", rows);
		return rows > 0;
	}

	@Override
	public int update(String id, String content, Collection<String> terms) {
		log.debug("update.enter; got id: {}, content length: {}, terms: {}", id, content.length(), terms);
		String deleteTermsSql = "delete from schematerms where schemaid = ?";
		int cnt = jdbc.update(deleteTermsSql, id);
		log.debug("update; deleted {} terms", cnt);
		String updateSchemaSql = "update schemafiles set updateTime = ?, content = ? where schemaid = ?";
		int rows = jdbc.update(updateSchemaSql, Timestamp.from(Instant.now()), content, id);
		if (rows > 0 && terms != null && !terms.isEmpty()) {
			String insertTermsSql = "insert into schematerms(term, schemaid) select * from unnest(?::varchar[], ?::varchar[])";
			String[] schemaIds = new String[terms.size()];
			Arrays.fill(schemaIds, id);
			String[] termIds = terms.toArray(new String[0]);
			jdbc.update(insertTermsSql, (Object) termIds, (Object) schemaIds);
		}
		log.debug("update.exit; updated: {}", rows);
		return rows;
	}

	@Override
	public String delete(String schemaId) {
		String sql = "delete from schemafiles where schemaid = ? returning type";
		try {
		  return jdbc.queryForObject(sql, new Object[] {schemaId}, new int[] {VARCHAR}, String.class);
		} catch (EmptyResultDataAccessException ex) {
		  return null;
		}
	}

	@Override
	public Optional<String> selectLatestContentByType(String typeName) {
		String sql = "select content from schemafiles where type = ? order by uploadtime desc limit 1";
		try {
			return Optional.ofNullable(jdbc.queryForObject(sql, new Object[]{typeName}, new int[]{VARCHAR}, String.class));
		} catch (EmptyResultDataAccessException ex) {
			return Optional.empty();
		}
	}

	@Override
	public int deleteAll() {
		return jdbc.update("delete from schemafiles");
	}

	private class SchemaAggregateExtractor implements ResultSetExtractor<Map<String, Collection<String>>> {

		@Override
		public Map<String, Collection<String>> extractData(ResultSet rs) throws SQLException, DataAccessException {
			Map<String, Collection<String>> result = new HashMap<>();
			while (rs.next()) {
				result.computeIfAbsent(rs.getString(1), t -> new HashSet<>()).add(rs.getString(2));
			}
			return result;
		}
	}

}
