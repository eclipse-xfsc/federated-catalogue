package eu.xfsc.fc.core.service.assetstore;

import static eu.xfsc.fc.core.util.TestUtil.assertThatAssetHasTheSameData;
import static eu.xfsc.fc.core.util.TestUtil.getAccessor;

import java.util.List;
import java.util.Map;

import eu.xfsc.fc.core.config.RdfContentTypeProperties;
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
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultParticipant;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.CredentialVerificationStrategy;
import eu.xfsc.fc.core.service.verification.JwtContentPreprocessor;
import eu.xfsc.fc.core.service.verification.SchemaValidationServiceImpl;
import eu.xfsc.fc.core.service.verification.VerificationService;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.Vc11Processor;
import eu.xfsc.fc.core.service.verification.Vc2Processor;
import eu.xfsc.fc.core.service.verification.VerificationServiceImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
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
@ContextConfiguration(classes = {AssetStoreCompositeTest.TestApplication.class, FileStoreConfig.class, VerificationServiceImpl.class, ValidatorCacheJpaDao.class,
  AssetStoreImpl.class, AssetJpaDao.class, IriGenerator.class, IriValidator.class, AssetStoreCompositeTest.class,
  SchemaStoreImpl.class, SchemaJpaDao.class, DatabaseConfig.class,
  Neo4jGraphStore.class, DidResolverConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class, HttpDocumentResolver.class,
  CredentialVerificationStrategy.class, SchemaValidationServiceImpl.class, ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class,
  RdfContentTypeProperties.class, JwtContentPreprocessor.class, Vc11Processor.class, Vc2Processor.class,
  JwtSignatureVerifier.class, DidDocumentResolver.class})
@Slf4j
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class AssetStoreCompositeTest {

  @SpringBootApplication
  public static class TestApplication {

    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private VerificationService verificationService;

  @Autowired
  private AssetStore assetStorePublisher;

  @Autowired
  private SchemaStoreImpl schemaStore;

  @Autowired
  private Neo4j embeddedDatabaseServer;

  @Autowired
  private GraphStore graphStore;

  @Autowired
  private ProtectedNamespaceFilter protectedNamespaceFilter;

  @AfterEach
  public void storageSelfCleaning() {
    schemaStore.clear();
    assetStorePublisher.clear();
  }

  @AfterAll
  void closeNeo4j() {
    embeddedDatabaseServer.close();
  }

  /**
   * Test storing a credential, ensuring it creates exactly one file on disk, retrieving it by hash, and deleting
   * it again.
   */
  @Test
  void test01StoreCredential() {
    log.info("test01StoreCredential");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/providerTest.jsonld");
    // Only verify semantics, not schema or signatures
    CredentialVerificationResultParticipant result = (CredentialVerificationResultParticipant) verificationService.verifyCredential(content, true, false, false, false);
    AssetMetadata assetMeta = new AssetMetadata(content, result);
    assetStorePublisher.storeCredential(assetMeta, result);

    String hash = assetMeta.getAssetHash();
    assertThatAssetHasTheSameData(assetMeta, assetStorePublisher.getByHash(hash), false);

    String uri = "http://example.org/test-issuer";
    List<Map<String, Object>> claims = graphStore.queryData(
        new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", uri))).getResults();
      Assertions.assertFalse(claims.isEmpty());

    List<Map<String, Object>> hNodes = graphStore.queryData(
        new GraphQuery("MATCH (n)-[r:legalAddress]->(a {locality: $locality}) RETURN n, r, a", Map.of("locality", "Hamburg"))).getResults();

    List<Map<String, Object>> aNodes = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN labels(n), n", Map.of())).getResults();
    
    //final ContentAccessor credentialFileByHash = assetStore.getFileByHash(hash);
    //assertEquals(credentialFileByHash, assetMeta.getContentAccessor(),
    //    "Getting the credential file by hash is equal to the stored credential file");
    assetStorePublisher.deleteAsset(hash);

    claims = graphStore.queryData(
        new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", uri))).getResults();
    Assertions.assertEquals(0, claims.size());

    Assertions.assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
  }

  @Test
  void test02RebuildGraphDb() {
    log.info("test02RebuildGraphDb");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/providerTest.jsonld");
    // Only verify semantics, not schema or signatures
    CredentialVerificationResultParticipant result = (CredentialVerificationResultParticipant) verificationService.verifyCredential(content, true, false, false, false);
    AssetMetadata assetMeta = new AssetMetadata(content, result);
    assetStorePublisher.storeCredential(assetMeta, result);

    String hash = assetMeta.getAssetHash();

    assertThatAssetHasTheSameData(assetMeta, assetStorePublisher.getByHash(hash), false);

    List<Map<String, Object>> claims = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN n", null)).getResults();
    Assertions.assertEquals(3, claims.size());

    graphStore.deleteClaims(assetMeta.getId());

    claims = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN n", null)).getResults();
    Assertions.assertEquals(1, claims.size());

    GraphRebuilder reBuilder = new GraphRebuilder(assetStorePublisher, graphStore, verificationService, protectedNamespaceFilter);
    reBuilder.rebuildGraphDb(1, 0, 1, 1);

    claims = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN n", null)).getResults();
    Assertions.assertEquals(3, claims.size());

    assetStorePublisher.deleteAsset(hash);

    claims = graphStore.queryData(
        new GraphQuery("MATCH (n) RETURN n", null)).getResults();
    Assertions.assertEquals(1, claims.size());

    Assertions.assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
  }

  @Test
  void test03RebuildGraphDb_filtersProtectedNamespaceClaims() {
    log.info("test03RebuildGraphDb_filtersProtectedNamespaceClaims");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantCredential-with-fcmeta.jsonld");
    // Skip all verification — we only care about claim storage and rebuild filtering
    CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);
    AssetMetadata assetMeta = new AssetMetadata(content, result);
    assetStorePublisher.storeCredential(assetMeta, result);

    String hash = assetMeta.getAssetHash();
    String assetId = assetMeta.getId();

    // Verify no fcmeta relationships exist after initial (filtered) storage
    List<Map<String, Object>> rels = graphStore.queryData(
        new GraphQuery("MATCH ()-[r]->() RETURN type(r) AS relType", null)).getResults();
    for (Map<String, Object> rel : rels) {
      String relType = (String) rel.get("relType");
      Assertions.assertFalse(relType.contains("complianceResult"),
          "Protected namespace relationship should not exist after initial store: " + relType);
    }

    // Delete graph claims, then rebuild — simulates a graph rebuild from stored raw credentials
    graphStore.deleteClaims(assetId);

    GraphRebuilder reBuilder = new GraphRebuilder(assetStorePublisher, graphStore, verificationService, protectedNamespaceFilter);
    reBuilder.rebuildGraphDb(1, 0, 1, 1);

    // Verify fcmeta claims are still filtered after rebuild
    rels = graphStore.queryData(
        new GraphQuery("MATCH ()-[r]->() RETURN type(r) AS relType", null)).getResults();
    for (Map<String, Object> rel : rels) {
      String relType = (String) rel.get("relType");
      Assertions.assertFalse(relType.contains("complianceResult"),
          "Protected namespace relationship should not exist after rebuild: " + relType);
    }

    assetStorePublisher.deleteAsset(hash);
  }

}
