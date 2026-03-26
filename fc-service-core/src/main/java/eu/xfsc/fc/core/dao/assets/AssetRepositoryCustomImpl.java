package eu.xfsc.fc.core.dao.assets;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Uses NamedParameterJdbcTemplate (JDBC) instead of JPA/JPQL because selectByFilter
// builds dynamic SQL with PostgreSQL-specific features (array operators, conditional column
// projection) that are not expressible in JPQL.
@Slf4j
@RequiredArgsConstructor
public class AssetRepositoryCustomImpl implements AssetRepositoryCustom {

  private final NamedParameterJdbcTemplate jdbc;

  @Override
  public PaginatedResults<AssetRecord> selectByFilter(AssetFilter filter,
      boolean withMeta, boolean withContent) {
    log.debug("selectByFilter.enter; got filter: {}, withMeta: {}, withContent: {}",
        filter, withMeta, withContent);
    final FilterQueryBuilder queryBuilder = new FilterQueryBuilder(withMeta, withContent);

    if (filter.getUploadTimeStart() != null) {
      queryBuilder.addClause("uploadtime >= ?", "uploadTimeStart",
          Timestamp.from(filter.getUploadTimeStart()));
      queryBuilder.addClause("uploadtime <= ?", "uploadTimeEnd",
          Timestamp.from(filter.getUploadTimeEnd()));
    }
    if (filter.getStatusTimeStart() != null) {
      queryBuilder.addClause("statustime >= ?", "statusTimeStart",
          Timestamp.from(filter.getStatusTimeStart()));
      queryBuilder.addClause("statustime <= ?", "statusTimeEnd",
          Timestamp.from(filter.getStatusTimeEnd()));
    }
    if (filter.getIssuers() != null) {
      queryBuilder.addClause("issuer in (?)", "issuers", filter.getIssuers());
    }
    if (filter.getValidators() != null) {
      queryBuilder.addClause("(validators && cast(? as varchar[]))", "validators",
          filter.getValidators().toArray(new String[0]));
    }
    if (filter.getStatuses() != null) {
      List<Integer> ords = filter.getStatuses().stream()
          .map(Enum::ordinal).collect(Collectors.toList());
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
    log.debug("selectByFilter.exit; returning records: {}, total: {}",
        assetList.size(), count);
    return new PaginatedResults<>(count, assetList);
  }

  // --- FilterQueryBuilder ---

  private static class FilterQueryBuilder {

    private final Map<String, Clause> clauses;
    private final boolean fullMeta;
    private final boolean returnContent;

    private static class Clause {

      private static final char PLACEHOLDER_SYMBOL = '?';
      private final String templateInstance;
      private final Object actualValue;

      private Clause(final String template, final String formalParameterName,
          final Object actualParameter) {
        if (actualParameter == null) {
          throw new IllegalArgumentException(
              "value for parameter " + formalParameterName + " is null");
        }
        final int placeholderPosition = template.indexOf(PLACEHOLDER_SYMBOL);
        if (placeholderPosition < 0) {
          throw new IllegalArgumentException(
              "missing parameter placeholder '" + PLACEHOLDER_SYMBOL
              + "' in template: " + template);
        }
        actualValue = actualParameter;
        templateInstance = template.substring(0, placeholderPosition)
            + ":" + formalParameterName
            + template.substring(placeholderPosition + 1);
      }
    }

    private FilterQueryBuilder(final boolean fullMeta, final boolean returnContent) {
      this.fullMeta = fullMeta;
      this.returnContent = returnContent;
      clauses = new LinkedHashMap<>();
    }

    private void addClause(final String template, final String formalParameterName,
        final Object actualParameter) {
      clauses.put(formalParameterName,
          new Clause(template, formalParameterName, actualParameter));
    }

    private void addQueryClauses(StringBuilder query) {
      for (Map.Entry<String, Clause> cls : clauses.entrySet()) {
        query.append(" and ");
        query.append(cls.getValue().templateInstance);
      }
    }

    private String buildCountQuery() {
      final StringBuilder query = new StringBuilder(
          "select count(*) from assets where 1=1");
      addQueryClauses(query);
      log.debug("buildCountQuery; Query: {}", query.toString());
      return query.toString();
    }

    private String buildQuery(int offset, int limit) {
      final StringBuilder query;
      if (fullMeta) {
        query = new StringBuilder("select asset_hash, subjectid, status, issuer,"
            + " uploadtime, statustime, expirationtime, validators,"
            + " content_type, file_size, original_filename");
      } else {
        query = new StringBuilder("select asset_hash, null as subjectid, status,"
            + " null as issuer, null as uploadtime, null as statustime,"
            + " null as expirationtime, null as validators,"
            + " null as content_type, null::bigint as file_size,"
            + " null as original_filename");
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

  private static class AssetQueryParameterSource implements SqlParameterSource {

    private final FilterQueryBuilder qBuilder;

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

  private static class AssetMetaMapper implements RowMapper<AssetRecord> {

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
          .validatorDids(arr == null ? null
              : Arrays.asList((String[]) arr.getArray()))
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
}
