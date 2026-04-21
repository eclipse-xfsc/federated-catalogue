package eu.xfsc.fc.core.dao.validatorcache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.pojo.Validator;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {ValidatorCacheDaoTest.TestConfig.class,
    eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao.class, DatabaseConfig.class, SecurityAuditorAware.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class ValidatorCacheDaoTest {

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @Autowired
  private ValidatorCacheDao validatorCacheDao;

  @AfterEach
  void cleanUp() {
    // Remove all entries to ensure test isolation
    validatorCacheDao.removeFromCache("did:test:1");
    validatorCacheDao.removeFromCache("did:test:2");
    validatorCacheDao.removeFromCache("did:test:3");
  }

  // ===== addToCache =====

  @Test
  void addToCache_newValidator_persistsSuccessfully() {
    Instant expiration = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    Validator validator = new Validator("did:test:1", "publicKey1", expiration);

    validatorCacheDao.addToCache(validator);

    Validator result = validatorCacheDao.getFromCache("did:test:1");
    assertNotNull(result);
    assertEquals("did:test:1", result.getDidURI());
    assertEquals("publicKey1", result.getPublicKey());
    assertEquals(expiration, result.getExpirationDate());
  }

  @Test
  void addToCache_duplicateKey_updatesExistingEntry() {
    Instant expiration = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    Validator validator = new Validator("did:test:1", "publicKey1", expiration);
    validatorCacheDao.addToCache(validator);

    Validator duplicate = new Validator("did:test:1", "differentKey", expiration);
    validatorCacheDao.addToCache(duplicate);

    Validator result = validatorCacheDao.getFromCache("did:test:1");
    assertEquals("differentKey", result.getPublicKey());
  }

  // ===== getFromCache =====

  @Test
  void getFromCache_existingKey_returnsValidator() {
    Instant expiration = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    Validator validator = new Validator("did:test:1", "publicKey1", expiration);
    validatorCacheDao.addToCache(validator);

    Validator result = validatorCacheDao.getFromCache("did:test:1");

    assertNotNull(result);
    assertEquals("did:test:1", result.getDidURI());
    assertEquals("publicKey1", result.getPublicKey());
    assertEquals(expiration, result.getExpirationDate());
  }

  @Test
  void getFromCache_nonExistentKey_returnsNull() {
    Validator result = validatorCacheDao.getFromCache("did:test:nonexistent");

    assertNull(result);
  }

  // ===== removeFromCache =====

  @Test
  void removeFromCache_existingKey_removesEntry() {
    Instant expiration = Instant.now().plus(1, ChronoUnit.HOURS);
    validatorCacheDao.addToCache(new Validator("did:test:1", "publicKey1", expiration));

    validatorCacheDao.removeFromCache("did:test:1");

    assertNull(validatorCacheDao.getFromCache("did:test:1"));
  }

  @Test
  void removeFromCache_nonExistentKey_noException() {
    validatorCacheDao.removeFromCache("did:test:nonexistent");
    // Should complete without throwing
  }

  // ===== expireValidators =====

  @Test
  void expireValidators_withExpiredEntries_deletesAndReturnsCount() {
    Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
    validatorCacheDao.addToCache(new Validator("did:test:1", "key1", past));
    validatorCacheDao.addToCache(new Validator("did:test:2", "key2", past));

    int count = validatorCacheDao.expireValidators();

    assertEquals(2, count);
    assertNull(validatorCacheDao.getFromCache("did:test:1"));
    assertNull(validatorCacheDao.getFromCache("did:test:2"));
  }

  @Test
  void expireValidators_noExpiredEntries_returnsZero() {
    Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
    validatorCacheDao.addToCache(new Validator("did:test:1", "key1", future));

    int count = validatorCacheDao.expireValidators();

    assertEquals(0, count);
  }

  @Test
  void expireValidators_mixedEntries_deletesOnlyExpired() {
    Instant past = Instant.now().minus(1, ChronoUnit.HOURS);
    Instant future = Instant.now().plus(1, ChronoUnit.DAYS);
    validatorCacheDao.addToCache(new Validator("did:test:1", "key1", past));
    validatorCacheDao.addToCache(new Validator("did:test:2", "key2", future));

    int count = validatorCacheDao.expireValidators();

    assertEquals(1, count);
    assertNull(validatorCacheDao.getFromCache("did:test:1"));
    assertNotNull(validatorCacheDao.getFromCache("did:test:2"));
  }
}
