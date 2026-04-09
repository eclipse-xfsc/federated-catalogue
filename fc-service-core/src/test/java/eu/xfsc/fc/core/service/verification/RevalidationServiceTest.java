package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.revalidator.RevalidatorChunksJpaDao;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorFile;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.assetstore.AssetStoreImpl;
import eu.xfsc.fc.core.service.graphdb.DummyGraphStore;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import eu.xfsc.fc.core.service.assetstore.IriGenerator;
import eu.xfsc.fc.core.service.assetstore.IriValidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static java.sql.Types.TIMESTAMP;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {RevalidationServiceTest.TestApplication.class, RevalidationServiceImpl.class, RevalidatorChunksJpaDao.class, FileStoreConfig.class, DummyGraphStore.class,
  VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaJpaDao.class, SchemaAuditRepository.class, DatabaseConfig.class, ValidatorCacheJpaDao.class, AssetStoreImpl.class, AssetJpaDao.class, AssetAuditRepository.class,
  DocumentLoaderConfig.class, DocumentLoaderProperties.class, DidResolverConfig.class, DidDocumentResolver.class, HttpDocumentResolver.class,
  JwtSignatureVerifier.class, ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class,
  IriGenerator.class, IriValidator.class, ObjectMapper.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
//@Import(EmbeddedNeo4JConfig.class)
public class RevalidationServiceTest {

  @SpringBootApplication
  public static class TestApplication {

    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
  
  @Autowired
  private JdbcTemplate jdbc;

  @Autowired
  private RevalidationServiceImpl revalidator;

  @Autowired
  private VerificationService verificationService;

  @Autowired
  private AssetStore assetStore;

  @Autowired
  private SchemaStore schemaStore;

  //@Autowired
  //private Neo4j embeddedDatabaseServer;

  @AfterAll
  void closeNeo4j() {
    //embeddedDatabaseServer.close();
  }

  @AfterEach
  public void storageSelfCleaning() throws IOException {
    revalidator.cleanup();
    assetStore.clear();
    schemaStore.clear();
  }

  @Test
  void testRevalidatorSetup() throws Exception {
    log.info("testRevalidatorSetup");
    int origCount = revalidator.getInstanceCount();

    revalidator.setInstanceCount(5);
    revalidator.setup();
    Assertions.assertEquals(5, countChunks(), "Unexpected number of chunks created.");
    revalidator.cleanup();

    revalidator.setInstanceCount(2);
    revalidator.setup();
    Assertions.assertEquals(2, countChunks(), "Unexpected number of chunks created.");
    revalidator.cleanup();

    revalidator.setInstanceCount(3);
    revalidator.setup();
    Assertions.assertEquals(3, countChunks(), "Unexpected number of chunks created.");
    revalidator.cleanup();

    revalidator.setInstanceCount(10);
    revalidator.setup();
    Assertions.assertEquals(10, countChunks(), "Unexpected number of chunks created.");
    revalidator.cleanup();

    revalidator.setInstanceCount(origCount);
    revalidator.setup();
    Assertions.assertEquals(origCount, countChunks(), "Unexpected number of chunks created.");
    revalidator.cleanup();
  }

  @Test
  void testRevalidatorManualStart() throws Exception {
    log.info("testRevalidatorManualStart");
    schemaStore.initializeDefaultSchemas();
    revalidator.setup();
    Instant treshold = Instant.now();
    addAsset("VerificationService/syntax/input.vp.jsonld");
    addAsset("Claims-Extraction-Tests/providerTest.jsonld");
    revalidator.startValidating();
    int count = 0;
    while ((revalidator.isWorking() || !allChunksAfter(treshold)) && count < 10) {
      log.debug("Revalidator working...");
      Thread.sleep(1000);
      count++;
    }
    revalidator.cleanup();
    assertTrue(allChunksAfter(treshold), "All chunks should have been revalidated.");
  }

  @Test
  public void testRevalidatorAutostart() throws Exception {
    log.info("testRevalidatorAutostart");
    revalidator.setInstanceCount(1);
    revalidator.setBatchSize(500);
    revalidator.setWorkerCount(Runtime.getRuntime().availableProcessors());
    revalidator.setup();
    addAsset("VerificationService/syntax/input.vp.jsonld");
    addAsset("Claims-Extraction-Tests/providerTest.jsonld");
    //addAssetsFromDirectory("GeneratedAssets);
    Instant treshold = Instant.now();
    schemaStore.initializeDefaultSchemas();
    int count = 0;
    do {
      log.debug("Revalidator working...");
      Thread.sleep(1000);
      count++;
    } while ((revalidator.isWorking() || !allChunksAfter(treshold)) && count < 60);
    revalidator.cleanup();
    assertTrue(allChunksAfter(treshold), "All chunks should have been revalidated.");
  }

  private void addAssetsFromDirectory(final String path) {
    long start = System.currentTimeMillis();
    URL url = getClass().getClassLoader().getResource(path);
    String str = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
    File assetDir = new File(str);
    File[] files = assetDir.listFiles();
    Arrays.sort(files, (var o1, var o2) -> o1.getName().compareTo(o1.getName()));
    ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    for (File asset : assetDir.listFiles()) {
      service.submit(() -> addAsset(new ContentAccessorFile(asset)));
    }
    service.shutdown();
    try {
      service.awaitTermination(2, TimeUnit.MINUTES);
    } catch (InterruptedException ex) {
      log.warn("Interrupted while waiting for assets to be added.");
    }
    long time = System.currentTimeMillis() - start;
    Integer count = jdbc.queryForObject("select count(*) from assets where status = ?", Integer.class, AssetStatus.ACTIVE.ordinal());
    log.debug("added {} assets from {} in {}ms", count, path, time);
  }

  private boolean allChunksAfter(Instant treshold) {
    Integer count = jdbc.queryForObject("select count(chunkid) from revalidatorchunks where lastcheck < ?", new Object[] {Timestamp.from(treshold)}, new int[] {TIMESTAMP}, Integer.class);
    log.debug("Open Chunk Count: {}", count);
    return count == 0;
  }

  private int countChunks() {
    Integer count = jdbc.queryForObject("select count(chunkid) from revalidatorchunks", Integer.class);
    log.debug("Chunk Count: {}", count);
    return count;
  }

  private String addAsset(String path) throws VerificationException {
    log.debug("Adding asset from {}", path);
    final ContentAccessor content = getAccessor(path);
    return addAsset(content);
  }

  public String addAsset(final ContentAccessor content) throws VerificationException {
    try {
      final CredentialVerificationResult vr = verificationService.verifyCredential(content, true, true, false, false);
      final AssetMetadata assetMetadata = new AssetMetadata(content, vr);
      assetStore.storeCredential(assetMetadata, vr);
      return assetMetadata.getAssetHash();
    } catch (VerificationException exc) {
      log.debug("Failed to add: {}", exc.getMessage());
      return null;
    } catch (Exception ex) {
      ex.printStackTrace();
      throw ex;
    }
  }

}
