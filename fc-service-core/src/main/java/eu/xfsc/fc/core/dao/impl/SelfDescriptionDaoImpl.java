package eu.xfsc.fc.core.dao.impl;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.dao.SelfDescriptionDao;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.service.sdstore.SdMetaRecord;
import eu.xfsc.fc.core.service.sdstore.SubjectHashRecord;
import eu.xfsc.fc.core.service.sdstore.SubjectStatusRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SelfDescriptionDaoImpl implements SelfDescriptionDao {
	
	@Autowired
	private NamedParameterJdbcTemplate jdbc;
	

	@Override
	public SdMetaRecord select(String hash) {
	    FilterQueryBuilder queryBuilder = new FilterQueryBuilder(true, true);
	    queryBuilder.addClause("sdhash = ?", "hash", hash);
	    String query = queryBuilder.buildQuery(0, 0);
	    SdMetaRecord sdmr;
	    try {
	      sdmr = jdbc.queryForObject(query, new SDQueryParameterSource(queryBuilder), new SDMetaMapper());
	    } catch (EmptyResultDataAccessException ex) {
	      sdmr = null;	
	    }
	    return sdmr;
	}
	
	@Override
    public PaginatedResults<SdMetaRecord> selectByFilter(SdFilter filter, boolean withMeta, boolean withContent) {
	    log.debug("selectByFilter.enter; got filter: {}, withMeta: {}, withContent: {}", filter, withMeta, withContent);
	    final FilterQueryBuilder queryBuilder = new FilterQueryBuilder(withMeta, withContent);

	    if (filter.getUploadTimeStart() != null) {
	      queryBuilder.addClause("uploadtime >= ?", "uploadTimeStart", Timestamp.from(filter.getUploadTimeStart()));
	      queryBuilder.addClause("uploadtime <= ?", "uploadTimeEnd", Timestamp.from(filter.getUploadTimeEnd()));
	    }
	    if (filter.getStatusTimeStart() != null) {
	      queryBuilder.addClause("statustime >= ?", "statusTimeStart", Timestamp.from(filter.getStatusTimeStart()));
	      queryBuilder.addClause("statustime <= ?", "statusTimeEnd", Timestamp.from(filter.getStatusTimeEnd()));
	    }
	    if (filter.getIssuers() != null) {
	      queryBuilder.addClause("issuer in (?)", "issuers", filter.getIssuers());
	    }
	    if (filter.getValidators() != null) {
	      queryBuilder.addClause("(validators && cast(? as varchar[]))", "validators", filter.getValidators().toArray(new String[0]));
	    }
	    if (filter.getStatuses() != null) {
	      List<Integer> ords = filter.getStatuses().stream().map(s -> s.ordinal()).collect(Collectors.toList());
	      queryBuilder.addClause("status in (?)", "statuses", ords);
	    }
	    if (filter.getIds() != null) {
	      queryBuilder.addClause("subjectid in (?)", "subjectIds", filter.getIds());
	    }
	    if (filter.getHashes() != null) {
	      queryBuilder.addClause("sdhash in (?)", "hashes", filter.getHashes());
	    }

        String query = queryBuilder.buildCountQuery();
        SqlParameterSource sps = new SDQueryParameterSource(queryBuilder);
        int count = jdbc.queryForObject(query, sps, Integer.class);
        
	    query = queryBuilder.buildQuery(filter.getOffset(), filter.getLimit());
	    Stream<SdMetaRecord> sdStream = jdbc.queryForStream(query, sps, new SDMetaMapper());
	    final List<SdMetaRecord> sdList = sdStream.collect(Collectors.toList());
	    log.debug("selectByFilter.exit; returning records: {}, total: {}", sdList.size(), count);
	    return new PaginatedResults<>(count, sdList);
    }
	
	@Override
	public List<String> selectHashes(String startHash, int count, int chunks, int chunkId) {
		String sql;
		MapSqlParameterSource msps = new MapSqlParameterSource(Map.of("status", SelfDescriptionStatus.ACTIVE.ordinal(), "chunks", chunks, "chunkid", chunkId, "limit", count));
	    if (startHash == null) {
          sql = "select sdhash from sdfiles where status = :status and abs(hashtext(sdhash) % :chunks) = :chunkid order by sdhash asc limit :limit";
	    } else {
	      sql = "select sdhash from sdfiles where sdhash > :lastSdHash and status = :status and abs(hashtext(sdhash) % :chunks) = :chunkid order by sdhash asc limit :limit";
	      msps.addValue("lastSdHash", startHash);
	    }
        return jdbc.queryForList(sql, msps, String.class);
	}

	@Override
	public List<String> selectExpiredHashes() {
        String sql = "select sdhash from sdfiles where status = :status and expirationTime < :expTime";
        return jdbc.queryForList(sql, Map.of("status", SelfDescriptionStatus.ACTIVE.ordinal(), "expTime", Timestamp.from(Instant.now())), String.class);
	}
	
	@Override
	public SubjectHashRecord insert(SdMetaRecord sd) {
	    String upsert = """
   	      with u as (update sdfiles set status = :upStatus, statustime = :upStatusTime
   	          where subjectid = :subjectId and status = 0 returning sdhash oldhash, :sdHash sdhash, subjectid),
   	      i as (insert into sdfiles(sdhash, subjectid, issuer, uploadtime, statustime, expirationtime, status, content, validators)
   	          values (:sdHash, :subjectId, :issuer, :uploadTime, :statusTime, :expirationTime, :status, :content, :validators)
   	          returning sdhash)
   	      select u.subjectid, u.oldhash from i full join u on u.sdhash = i.sdhash""";
	    MapSqlParameterSource msps = new MapSqlParameterSource();
	    msps.addValue("upStatus", SelfDescriptionStatus.DEPRECATED.ordinal());
	    msps.addValue("upStatusTime", Timestamp.from(Instant.now()));
	    msps.addValue("subjectId", sd.getId());
	    msps.addValue("sdHash", sd.getSdHash());
	    msps.addValue("issuer", sd.getIssuer());
	    msps.addValue("uploadTime", Timestamp.from(sd.getUploadDatetime()));
	    msps.addValue("statusTime", Timestamp.from(sd.getStatusDatetime()));
	    msps.addValue("expirationTime", sd.getExpirationTime() == null ? null : Timestamp.from(sd.getExpirationTime()));
	    msps.addValue("status", sd.getStatus().ordinal());
	    msps.addValue("content", sd.getContent());
	    msps.addValue("validators", sd.getValidators());
	    SubjectHashRecord subHash = jdbc.queryForObject(upsert, msps, new SDSubjectHashMapper());
		return subHash;
	}

	@Override
	public SubjectStatusRecord update(String hash, int status) {
	    String sql = """
	      with u as (update sdfiles set status = :status, statustime = :status_dt
	      where sdhash = :hash and status = 0 returning sdhash, subjectid),
	      o as (select sdhash, status from sdfiles where sdhash = :hash and status > 0)
	      select u.subjectid, o.status from u full join o on o.sdhash = u.sdhash""";
	    MapSqlParameterSource msps = new MapSqlParameterSource();
	    msps.addValue("hash", hash);
	    msps.addValue("status", status);
	    msps.addValue("status_dt", Timestamp.from(Instant.now()));
		return jdbc.queryForObject(sql, msps, new SDSubjectStatusMapper());
	}

	@Override
	public SubjectStatusRecord delete(String hash) {
		String sql = "delete from sdfiles where sdhash = :hash returning subjectid, status";
		try {
		  return jdbc.queryForObject(sql, Map.of("hash", hash), new SDSubjectStatusMapper());
	    } catch (EmptyResultDataAccessException ex) {
	      return null;	
	    }
	}

	@Override
	public int deleteAll() {
		return jdbc.update("delete from sdfiles", Map.of());
	}
	
	
	private static class FilterQueryBuilder {

	    private final Map<String, Clause> clauses;
	    private final boolean fullMeta;
	    private final boolean returnContent;

	    private static class Clause {

	      private static final char PLACEHOLDER_SYMBOL = '?';
	      private final String templateInstance;
	      private final Object actualValue;

	      private Clause(final String template, final String formalParameterName, final Object actualParameter) {
	        if (actualParameter == null) {
	          throw new IllegalArgumentException("value for parameter " + formalParameterName + " is null");
	        }
	        final int placeholderPosition = template.indexOf(PLACEHOLDER_SYMBOL);
	        if (placeholderPosition < 0) {
	          throw new IllegalArgumentException("missing parameter placeholder '" + PLACEHOLDER_SYMBOL + "' in template: " + template);
	        }
	        actualValue = actualParameter;
	        templateInstance = template.substring(0, placeholderPosition) + ":" + formalParameterName + template.substring(placeholderPosition + 1);
	        //if (templateInstance.indexOf(PLACEHOLDER_SYMBOL, placeholderPosition + 1) != -1) {
	        //  throw new IllegalArgumentException("multiple parameter placeholders '" + PLACEHOLDER_SYMBOL + "' in template: " + template);
	        //}
	      }
	    }

	    private FilterQueryBuilder(final boolean fullMeta, final boolean returnContent) {
	      this.fullMeta = fullMeta;
	      this.returnContent = returnContent;
	      clauses = new LinkedHashMap<>();
	    }

	    private void addClause(final String template, final String formalParameterName, final Object actualParameter) {
		  clauses.put(formalParameterName, new Clause(template, formalParameterName, actualParameter));
		}

		private void addQueryClauses(StringBuilder query) {
	      for (Map.Entry<String, Clause> cls: clauses.entrySet()) {
	        query.append(" and ");
	        query.append(cls.getValue().templateInstance);
	        //query.append(")");
	      }
		}

	    private String buildCountQuery() {
	      final StringBuilder query = new StringBuilder("select count(*) from sdfiles where 1=1");
	      addQueryClauses(query);
	      log.debug("buildCountQuery; Query: {}", query.toString());
	      return query.toString();
	    }

	    private String buildQuery(int offset, int limit) {
	      final StringBuilder query;
	      if (fullMeta) {
	        query = new StringBuilder("select sdhash, subjectid, status, issuer, uploadtime, statustime, expirationtime, validators");
	      } else {
	        query = new StringBuilder("select sdhash, null as subjectid, status, null as issuer, null as uploadtime, null as statustime, null as expirationtime, null as validators");
	      }
	      if (returnContent) {
	        query.append(", content");
	      } else {
	        query.append(", null as content");  
	      }
	      query.append(" from sdfiles");
	      query.append(" where 1=1");
	      addQueryClauses(query);
	      query.append(" ").append("order by statustime desc, sdhash");
	      
          if (offset > 0) {
        	query.append(" offset ").append(offset);  
          }
          if (limit > 0) {
	  	    query.append(" limit ").append(limit);
	      }
	      
	      log.debug("buildQuery; Query: {}", query.toString());
	      return query.toString();
	    }
	}
	
	private class SDQueryParameterSource implements SqlParameterSource {
		
		private FilterQueryBuilder qBuilder;
		
		private SDQueryParameterSource(FilterQueryBuilder qBuilder) {
			this.qBuilder = qBuilder;
		}

		@Override
		public boolean hasValue(String paramName) {
			return qBuilder.clauses.containsKey(paramName);
					
		}

		@Override
		public Object getValue(String paramName) throws IllegalArgumentException {
			return qBuilder.clauses.get(paramName).actualValue;
		}
	}
	
	private class SDMetaMapper implements RowMapper<SdMetaRecord> {

		@Override
		public SdMetaRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			Array arr = rs.getArray("validators");
			String content = rs.getString("content");
			Timestamp upt = rs.getTimestamp("uploadtime");
			Timestamp stt = rs.getTimestamp("statustime");
			Timestamp exp = rs.getTimestamp("expirationtime");
			return new SdMetaRecord(rs.getString("sdhash"), rs.getString("subjectid"), SelfDescriptionStatus.values()[rs.getInt("status")], rs.getString("issuer"), 
				arr == null ? null : Arrays.asList((String[]) arr.getArray()), upt == null ? null : upt.toInstant(), stt == null ? null : stt.toInstant(), 
				content == null ? null : new ContentAccessorDirect(content), exp == null ? null : exp.toInstant());
		}
    }
	
	private class SDSubjectHashMapper implements RowMapper<SubjectHashRecord> {

		@Override
		public SubjectHashRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new SubjectHashRecord(rs.getString(1), rs.getString(2));
		}
	}

	private class SDSubjectStatusMapper implements RowMapper<SubjectStatusRecord> {

		@Override
		public SubjectStatusRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new SubjectStatusRecord(rs.getString(1), rs.getInt(2));
		}
	}

}
