package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheDao;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;


@Slf4j
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {ValidatorCacheTest.TestApplication.class, ValidatorCacheJpaDao.class, DatabaseConfig.class, FileStoreConfig.class,
        VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaJpaDao.class, SchemaAuditRepository.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class,
        DidResolverConfig.class, DidDocumentResolver.class, HttpDocumentResolver.class,
        JwtSignatureVerifier.class, ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class,
        SecurityAuditorAware.class})
//@DirtiesContext
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public class ValidatorCacheTest {

  @SpringBootApplication
  public static class TestApplication {

    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private ValidatorCacheDao validatorCache;

  @Test
  void test01AddingAndRemoving() {
    log.info("test01AddingAndRemoving");
    Validator validator = new Validator("SomeUrl", "Some Text Content", getInstantNow());
    validatorCache.addToCache(validator);

    Validator fromCache = validatorCache.getFromCache(validator.getDidURI());
    Assertions.assertEquals(validator, fromCache, "Returned Validator is not the same as the stored Validator");

    validatorCache.removeFromCache(validator.getDidURI());
    fromCache = validatorCache.getFromCache(validator.getDidURI());
    Assertions.assertNull(fromCache, "Validator should have been removed from cache");
  }

  @Test
  void test02Expiration() {
    log.info("test02Expiration");
    Validator v1 = new Validator("SomeUrl1", "Some Text Content", getInstantNow().minus(1, ChronoUnit.MINUTES));
    validatorCache.addToCache(v1);
    Validator v2 = new Validator("SomeUrl2", "Some Text Content", getInstantNow().plus(1, ChronoUnit.MINUTES));
    validatorCache.addToCache(v2);

    Validator fromCache1 = validatorCache.getFromCache(v1.getDidURI());
    Assertions.assertEquals(v1, fromCache1, "Returned Validator is not the same as the stored Validator");
    Validator fromCache2 = validatorCache.getFromCache(v2.getDidURI());
    Assertions.assertEquals(v2, fromCache2, "Returned Validator is not the same as the stored Validator");

    int expired = validatorCache.expireValidators();
    log.info("Expired {} keys", expired);
    Assertions.assertEquals(1, expired, "Incorrect number of keys expired.");

    fromCache1 = validatorCache.getFromCache(v1.getDidURI());
    log.info("Found {}", fromCache1);
    Assertions.assertNull(fromCache1, "Validator should have been removed from cache");
    fromCache2 = validatorCache.getFromCache(v2.getDidURI());
    Assertions.assertEquals(v2, fromCache2, "Validator should not have been removed from the cache");

    validatorCache.removeFromCache(v2.getDidURI());
  }

  private static Instant getInstantNow() {
    return Instant.now().truncatedTo(ChronoUnit.SECONDS);
  }

}
