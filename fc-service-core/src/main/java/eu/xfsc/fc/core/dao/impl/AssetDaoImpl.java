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

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.dao.AssetDao;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectHashRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectStatusRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AssetDaoImpl implements AssetDao {
	
	@Autowired
	private NamedParameterJdbcTemplate jdbc;
	

	@Override
	public AssetRecord select(String hash) {
	    FilterQueryBuilder queryBuilder = new FilterQueryBuilder(true, true);
	    queryBuilder.addClause("asset_hash = ?", "hash", hash);
	    String query = queryBuilder.buildQuery(0, 0);
	    AssetRecord assetRecord;
	    try {
	      assetRecord = jdbc.queryForObject(query, new AssetQueryParameterSource(queryBuilder), new AssetMetaMapper());
	    } catch (EmptyResultDataAccessException ex) {
	      assetRecord = null;	
	    }
	    return assetRecord;
	}
	
	@Override
    public PaginatedResults<AssetRecord> selectByFilter(AssetFilter filter, boolean withMeta, boolean withContent) {
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
	      queryBuilder.addClause("asset_hash in (?)", "hashes", filter.getHashes());
	    }

        String query = queryBuilder.buildCountQuery();
        SqlParameterSource sps = new AssetQueryParameterSource(queryBuilder);
        int count = jdbc.queryForObject(query, sps, Integer.class);
        
	    query = queryBuilder.buildQuery(filter.getOffset(), filter.getLimit());
	    Stream<AssetRecord> assetStream = jdbc.queryForStream(query, sps, new AssetMetaMapper());
	    final List<AssetRecord> assetList = assetStream.collect(Collectors.toList());
	    log.debug("selectByFilter.exit; returning records: {}, total: {}", assetList.size(), count);
	    return new PaginatedResults<>(count, assetList);
    }
	
	@Override
	public List<String> selectHashes(String startHash, int count, int chunks, int chunkId) {
		String sql;
		MapSqlParameterSource msps = new MapSqlParameterSource(Map.of("status", AssetStatus.ACTIVE.ordinal(), "chunks", chunks, "chunkid", chunkId, "limit", count));
	    if (startHash == null) {
          sql = "select asset_hash from assets where status = :status and abs(hashtext(asset_hash) % :chunks) = :chunkid order by asset_hash asc limit :limit";
	    } else {
	      sql = "select asset_hash from assets where asset_hash > :lastAssetHash and status = :status and abs(hashtext(asset_hash) % :chunks) = :chunkid order by asset_hash asc limit :limit";
	      msps.addValue("lastAssetHash", startHash);
	    }
        return jdbc.queryForList(sql, msps, String.class);
	}

	@Override
	public List<String> selectExpiredHashes() {
        String sql = "select asset_hash from assets where status = :status and expirationTime < :expTime";
        return jdbc.queryForList(sql, Map.of("status", AssetStatus.ACTIVE.ordinal(), "expTime", Timestamp.from(Instant.now())), String.class);
	}
	
	@Override
	public SubjectHashRecord insert(AssetRecord assetRecord) {
	    String upsert = """
   	      with u as (update assets set status = :upStatus, statustime = :upStatusTime
   	          where subjectid = :subjectId and status = 0 returning asset_hash oldhash, :assetHash asset_hash, subjectid),
   	      i as (insert into assets(asset_hash, subjectid, issuer, uploadtime, statustime, expirationtime, status, content, validators,
   	              content_type, file_size, original_filename)
   	          values (:assetHash, :subjectId, :issuer, :uploadTime, :statusTime, :expirationTime, :status, :content, :validators,
   	              :contentType, :fileSize, :originalFilename)
   	          returning asset_hash)
   	      select u.subjectid, u.oldhash from i full join u on u.asset_hash = i.asset_hash""";
	    MapSqlParameterSource msps = new MapSqlParameterSource();
	    msps.addValue("upStatus", AssetStatus.DEPRECATED.ordinal());
	    msps.addValue("upStatusTime", Timestamp.from(Instant.now()));
	    msps.addValue("subjectId", assetRecord.getId());
	    msps.addValue("assetHash", assetRecord.getAssetHash());
	    msps.addValue("issuer", assetRecord.getIssuer());
	    msps.addValue("uploadTime", Timestamp.from(assetRecord.getUploadDatetime()));
	    msps.addValue("statusTime", Timestamp.from(assetRecord.getStatusDatetime()));
	    msps.addValue("expirationTime", assetRecord.getExpirationTime() == null ? null : Timestamp.from(assetRecord.getExpirationTime()));
	    msps.addValue("status", assetRecord.getStatus().ordinal());
	    msps.addValue("content", assetRecord.getContent());
	    msps.addValue("validators", assetRecord.getValidators());
	    msps.addValue("contentType", assetRecord.getContentType());
	    msps.addValue("fileSize", assetRecord.getFileSize());
	    msps.addValue("originalFilename", assetRecord.getOriginalFilename());
	    SubjectHashRecord subHash = jdbc.queryForObject(upsert, msps, new AssetSubjectHashMapper());
		return subHash;
	}

	@Override
	public SubjectStatusRecord update(String hash, int status) {
	    String sql = """
	      with u as (update assets set status = :status, statustime = :status_dt
	      where asset_hash = :hash and status = 0 returning asset_hash, subjectid),
	      o as (select asset_hash, status from assets where asset_hash = :hash and status > 0)
	      select u.subjectid, o.status from u full join o on o.asset_hash = u.asset_hash""";
	    MapSqlParameterSource msps = new MapSqlParameterSource();
	    msps.addValue("hash", hash);
	    msps.addValue("status", status);
	    msps.addValue("status_dt", Timestamp.from(Instant.now()));
		return jdbc.queryForObject(sql, msps, new AssetSubjectStatusMapper());
	}

	@Override
	public SubjectStatusRecord delete(String hash) {
		String sql = "delete from assets where asset_hash = :hash returning subjectid, status";
		try {
		  return jdbc.queryForObject(sql, Map.of("hash", hash), new AssetSubjectStatusMapper());
	    } catch (EmptyResultDataAccessException ex) {
	      return null;	
	    }
	}

	@Override
	public int deleteAll() {
		return jdbc.update("delete from assets", Map.of());
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
	      final StringBuilder query = new StringBuilder("select count(*) from assets where 1=1");
	      addQueryClauses(query);
	      log.debug("buildCountQuery; Query: {}", query.toString());
	      return query.toString();
	    }

	    private String buildQuery(int offset, int limit) {
	      final StringBuilder query;
	      if (fullMeta) {
	        query = new StringBuilder("select asset_hash, subjectid, status, issuer, uploadtime, statustime, expirationtime, validators, content_type, file_size, original_filename");
	      } else {
	        query = new StringBuilder("select asset_hash, null as subjectid, status, null as issuer, null as uploadtime, null as statustime, null as expirationtime, null as validators, null as content_type, null::bigint as file_size, null as original_filename");
	      }
	      if (returnContent) {
	        query.append(", content");
	      } else {
	        query.append(", null as content");
	      }
	      query.append(" from assets");
	      query.append(" where 1=1");
	      addQueryClauses(query);
	      query.append(" ").append("order by statustime desc, asset_hash");
	      
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
	
	private class AssetQueryParameterSource implements SqlParameterSource {
		
		private FilterQueryBuilder qBuilder;
		
		private AssetQueryParameterSource(FilterQueryBuilder qBuilder) {
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
	
	private class AssetMetaMapper implements RowMapper<AssetRecord> {

		@Override
		public AssetRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			Array arr = rs.getArray("validators");
			String content = rs.getString("content");
			Timestamp upt = rs.getTimestamp("uploadtime");
			Timestamp stt = rs.getTimestamp("statustime");
			Timestamp exp = rs.getTimestamp("expirationtime");
			String contentType = rs.getString("content_type");
			long fileSize = rs.getLong("file_size");
			String originalFilename = rs.getString("original_filename");
			return AssetRecord.builder()
				.assetHash(rs.getString("asset_hash"))
				.id(rs.getString("subjectid"))
				.status(AssetStatus.values()[rs.getInt("status")])
				.issuer(rs.getString("issuer"))
				.validatorDids(arr == null ? null : Arrays.asList((String[]) arr.getArray()))
				.uploadTime(upt == null ? null : upt.toInstant())
				.statusTime(stt == null ? null : stt.toInstant())
				.content(content == null ? null : new ContentAccessorDirect(content))
				.expirationTime(exp == null ? null : exp.toInstant())
				.contentType(contentType)
				.fileSize(rs.wasNull() ? null : fileSize)
				.originalFilename(originalFilename)
				.build();
		}
    }
	
	private class AssetSubjectHashMapper implements RowMapper<SubjectHashRecord> {

		@Override
		public SubjectHashRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new SubjectHashRecord(rs.getString(1), rs.getString(2));
		}
	}

	private class AssetSubjectStatusMapper implements RowMapper<SubjectStatusRecord> {

		@Override
		public SubjectStatusRecord mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new SubjectStatusRecord(rs.getString(1), rs.getInt(2));
		}
	}

}
