package eu.xfsc.fc.core.dao.impl;

import static java.sql.Types.*;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.dao.ValidatorCacheDao;
import eu.xfsc.fc.core.pojo.Validator;

/**
 * A cache for Validator information that is loaded from a DID.
 *
 */
@Slf4j
@Component
public class ValidatorCacheDaoImpl implements ValidatorCacheDao {

  @Autowired
  private JdbcTemplate jdbc;

  @Override
  public Validator getFromCache(String didURI) {
	String sql ="select diduri, publickey, expirationtime from validatorcache where diduri = ?";
	try {
	  return jdbc.queryForObject(sql, new Object[] {didURI}, new int[] {VARCHAR}, new RowMapper<Validator>() {

		@Override
		public Validator mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new Validator(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant());
		}
	  });
	} catch (EmptyResultDataAccessException ex) {
		// expected..
		return null;
	}
  }

  @Override
  public void addToCache(Validator validator) {
	String sql ="insert into validatorcache(diduri, publickey, expirationtime) values(?, ?, ?)";
	int cnt = jdbc.update(sql, validator.getDidURI(), validator.getPublicKey(), Timestamp.from(validator.getExpirationDate()));
  }

  @Override
  public void removeFromCache(String didURI) {
    String sql = "delete from validatorcache where diduri = ?";
    int cnt = jdbc.update(sql, didURI);
  }

  /**
   * Removed expired validators from the cache.
   *
   * @return the number if deleted validators.
   */
  @Override
  public int expireValidators() {
    String sql = "delete from validatorcache where expirationtime < ?";
    int cnt = jdbc.update(sql, Timestamp.from(Instant.now()));
    log.debug("expireValidators; expired {} validators", cnt);
    return cnt;
  }

}
