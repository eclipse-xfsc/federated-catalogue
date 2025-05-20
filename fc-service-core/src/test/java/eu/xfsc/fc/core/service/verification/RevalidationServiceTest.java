package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static java.sql.Types.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.neo4j.harness.Neo4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.dao.impl.RevalidatorChunksDaoImpl;
import eu.xfsc.fc.core.dao.impl.SchemaDaoImpl;
import eu.xfsc.fc.core.dao.impl.SelfDescriptionDaoImpl;
import eu.xfsc.fc.core.dao.impl.ValidatorCacheDaoImpl;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorFile;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.graphdb.Neo4jGraphStore;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStoreImpl;
import eu.xfsc.fc.testsupport.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {RevalidationServiceTest.TestApplication.class, RevalidationServiceImpl.class, RevalidatorChunksDaoImpl.class, FileStoreConfig.class, Neo4jGraphStore.class,
  VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaDaoImpl.class, DatabaseConfig.class, ValidatorCacheDaoImpl.class, SelfDescriptionStoreImpl.class, SelfDescriptionDaoImpl.class,
  DocumentLoaderConfig.class, DocumentLoaderProperties.class, DidResolverConfig.class, HttpDocumentResolver.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
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
  private SelfDescriptionStore sdStore;

  @Autowired
  private SchemaStore schemaStore;

  @Autowired
  private Neo4j embeddedDatabaseServer;

  @AfterAll
  void closeNeo4j() {
    embeddedDatabaseServer.close();
  }

  @AfterEach
  public void storageSelfCleaning() throws IOException {
    revalidator.cleanup();
    sdStore.clear();
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
    addSelfDescription("VerificationService/syntax/input.vp.jsonld");
    addSelfDescription("Claims-Extraction-Tests/providerTest.jsonld");
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
    addSelfDescription("VerificationService/syntax/input.vp.jsonld");
    addSelfDescription("Claims-Extraction-Tests/providerTest.jsonld");
    //addSdsFromDirectory("GeneratedSds");
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

  private void addSdsFromDirectory(final String path) {
    long start = System.currentTimeMillis();
    URL url = getClass().getClassLoader().getResource(path);
    String str = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
    File sdDir = new File(str);
    File[] files = sdDir.listFiles();
    Arrays.sort(files, (var o1, var o2) -> o1.getName().compareTo(o1.getName()));
    ExecutorService service = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    for (File sd : sdDir.listFiles()) {
      service.submit(() -> addSelfDescription(new ContentAccessorFile(sd)));
    }
    service.shutdown();
    try {
      service.awaitTermination(2, TimeUnit.MINUTES);
    } catch (InterruptedException ex) {
      log.warn("Interrupted while waiting for SDs to be added.");
    }
    long time = System.currentTimeMillis() - start;
    Integer count = jdbc.queryForObject("select count(*) from sdfiles where status = ?", Integer.class, SelfDescriptionStatus.ACTIVE.ordinal());
    log.debug("added {} Self-Descriptions from {} in {}ms", count, path, time);
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

  private String addSelfDescription(String path) throws VerificationException, UnsupportedEncodingException, InterruptedException {
    log.debug("Adding SD from {}", path);
    final ContentAccessor content = getAccessor(path);
    return addSelfDescription(content);
  }

  public String addSelfDescription(final ContentAccessor content) throws VerificationException {
    try {
      final VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
      final SelfDescriptionMetadata sd = new SelfDescriptionMetadata(content, vr);
      sdStore.storeSelfDescription(sd, vr);
      return sd.getSdHash();
    } catch (VerificationException exc) {
      log.debug("Failed to add: {}", exc.getMessage());
      return null;
    } catch (Exception ex) {
      ex.printStackTrace();
      throw ex;
    }
  }

}
