package eu.xfsc.fc.core.service.sdstore;

import static eu.xfsc.fc.core.util.TestUtil.assertThatSdHasTheSameData;
import static eu.xfsc.fc.core.util.TestUtil.getAccessor;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.harness.Neo4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.dao.impl.SchemaDaoImpl;
import eu.xfsc.fc.core.dao.impl.SelfDescriptionDaoImpl;
import eu.xfsc.fc.core.dao.impl.ValidatorCacheDaoImpl;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResultParticipant;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.core.service.verification.VerificationServiceImpl;
import eu.xfsc.fc.core.util.GraphRebuilder;
import eu.xfsc.fc.graphdb.service.Neo4jGraphStore;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {SelfDescriptionStoreCompositeTest.TestApplication.class, FileStoreConfig.class, VerificationServiceImpl.class, ValidatorCacheDaoImpl.class,
  SelfDescriptionStoreImpl.class, SelfDescriptionDaoImpl.class, SelfDescriptionStoreCompositeTest.class, SchemaStoreImpl.class, SchemaDaoImpl.class, DatabaseConfig.class, 
  Neo4jGraphStore.class, DidResolverConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class, HttpDocumentResolver.class})
@Slf4j
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class SelfDescriptionStoreCompositeTest {

  @SpringBootApplication
  public static class TestApplication {

    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private VerificationService verificationService;

  @Autowired
  private SelfDescriptionStore sdStorePublisher;

  @Autowired
  private SchemaStoreImpl schemaStore;

  @Autowired
  private Neo4j embeddedDatabaseServer;

  @Autowired
  private GraphStore graphStore;

  @AfterEach
  public void storageSelfCleaning() {
    schemaStore.clear();
    sdStorePublisher.clear();
  }

  @AfterAll
  void closeNeo4j() {
    embeddedDatabaseServer.close();
  }

  /**
   * Test storing a self-description, ensuring it creates exactly one file on disk, retrieving it by hash, and deleting
   * it again.
   */
  @Test
  void test01StoreSelfDescription() throws Exception {
    log.info("test01StoreSelfDescription");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/providerTest.jsonld");
    // Only verify semantics, not schema or signatures
    VerificationResultParticipant result = (VerificationResultParticipant) verificationService.verifySelfDescription(content, true, false, false, false);
    SelfDescriptionMetadata sdMeta = new SelfDescriptionMetadata(content, result);
    sdStorePublisher.storeSelfDescription(sdMeta, result);

    String hash = sdMeta.getSdHash();
    assertThatSdHasTheSameData(sdMeta, sdStorePublisher.getByHash(hash), false);

    String uri = "http://example.org/test-issuer";
    List<Map<String, Object>> claims = graphStore.queryData(
        new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", uri))).getResults();
    Assertions.assertTrue(claims.size() > 0); 

    List<Map<String, Object>> hNodes = graphStore.queryData(
        new GraphQuery("MATCH (n)-[r:legalAddress]->(a {locality: $locality}) RETURN n, r, a", Map.of("locality", "Hamburg"))).getResults();
    log.debug("test01StoreSelfDescription; got Hamburg nodes: {}", hNodes);

    List<Map<String, Object>> aNodes = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN labels(n), n", Map.of())).getResults();
    log.debug("test01StoreSelfDescription; got All nodes: {}", aNodes);
    
    //final ContentAccessor sdfileByHash = sdStore.getSDFileByHash(hash);
    //assertEquals(sdfileByHash, sdMeta.getSelfDescription(),
    //    "Getting the SD file by hash is equal to the stored SD file");
    sdStorePublisher.deleteSelfDescription(hash);

    claims = graphStore.queryData(
        new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", uri))).getResults();
    Assertions.assertEquals(0, claims.size());

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });
  }

  @Test
  void test02RebuildGraphDb() throws Exception {
    log.info("test02RebuildGraphDb");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/providerTest.jsonld");
    // Only verify semantics, not schema or signatures
    VerificationResultParticipant result = (VerificationResultParticipant) verificationService.verifySelfDescription(content, true, false, false, false);
    SelfDescriptionMetadata sdMeta = new SelfDescriptionMetadata(content, result);
    sdStorePublisher.storeSelfDescription(sdMeta, result);

    String hash = sdMeta.getSdHash();

    assertThatSdHasTheSameData(sdMeta, sdStorePublisher.getByHash(hash), false);

    List<Map<String, Object>> claims = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN n", null)).getResults();
    log.debug("Claims: {}", claims);
    Assertions.assertEquals(3, claims.size());

    graphStore.deleteClaims(sdMeta.getId());

    claims = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN n", null)).getResults();
    log.debug("Claims: {}", claims);
    Assertions.assertEquals(1, claims.size());

    GraphRebuilder reBuilder = new GraphRebuilder(sdStorePublisher, graphStore, verificationService);
    reBuilder.rebuildGraphDb(1, 0, 1, 1);

    claims = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN n", null)).getResults();
    log.debug("Claims: {}", claims);
    Assertions.assertEquals(3, claims.size());

    sdStorePublisher.deleteSelfDescription(hash);

    claims = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN n", null)).getResults();
    Assertions.assertEquals(1, claims.size());

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });
  }

}
