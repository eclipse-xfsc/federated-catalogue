package eu.xfsc.fc.core.dao.impl;

import eu.xfsc.fc.core.dao.CesTrackerDao;
import eu.xfsc.fc.core.service.pubsub.ces.CesTracking;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import static java.sql.Types.VARCHAR;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;

@Component
public class CesTrackerDaoImpl implements CesTrackerDao {

	@Autowired
    private JdbcTemplate jdbc;

	@Override
	public void insert(CesTracking event) {
		String sql = "INSERT INTO ces_tracker(ces_id, event, created_at, cred_processed, cred_id, error) VALUES (?, ?, ?, ?, ?, ?)";
        jdbc.update(sql, new Object[] {event.getCesId(), event.getEvent(), Timestamp.from(event.getCreatedAt()), event.getCredProcessed(), event.getCredId(), event.getError()});
	}

	@Override
	public CesTracking select(String cesId) {
        String sql = "SELECT * FROM ces_tracker WHERE ces_id=?";
        try {
            return jdbc.queryForObject(sql, new Object[] {cesId}, new int[] {VARCHAR}, new CesTrackingMapper());
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
	}

	@Override
	public CesTracking selectLatest() {
        String sql = "SELECT * FROM ces_tracker ORDER BY created_at DESC LIMIT 1"; 
        try {
            return jdbc.queryForObject(sql, new CesTrackingMapper());
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
	}

	private class CesTrackingMapper implements RowMapper<CesTracking> {

		@Override
		public CesTracking mapRow(ResultSet rs, int rowNum) throws SQLException {
			return new CesTracking(rs.getString(1), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getInt(4), rs.getString(5), rs.getString(6));
		}
	}
	
}

