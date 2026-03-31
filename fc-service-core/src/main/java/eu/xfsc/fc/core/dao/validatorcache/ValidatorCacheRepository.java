package eu.xfsc.fc.core.dao.validatorcache;

import java.time.Instant;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ValidatorCacheRepository
    extends JpaRepository<ValidatorCache, String> {

  @Modifying
  @Query(value = "DELETE FROM validatorcache WHERE expirationtime < :now",
      nativeQuery = true)
  int deleteExpired(@Param("now") Instant now);
}
