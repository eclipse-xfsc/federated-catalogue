package eu.xfsc.fc.core.service.assetstore;

import static eu.xfsc.fc.core.util.TestUtil.assertThatAssetHasTheSameData;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import eu.xfsc.fc.core.config.FileStoreConfig;
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

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.graphdb.service.Neo4jGraphStore;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;

import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.util.HashUtils;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {AssetStoreTest.TestApplication.class, AssetStoreImpl.class, AssetJpaDao.class, AssetAuditRepository.class, IriGenerator.class, IriValidator.class,
  AssetStoreTest.class, Neo4jGraphStore.class, DatabaseConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class,
  DidResolverConfig.class, HttpDocumentResolver.class, FileStoreConfig.class, RdfContentTypeProperties.class,
  SecurityAuditorAware.class, ProtectedNamespaceProperties.class})
@Slf4j
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class AssetStoreTest {

  @SpringBootApplication
  public static class TestApplication {

    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private AssetStore assetStorePublisher;

  @Autowired
  private Neo4j embeddedDatabaseServer;

  @Autowired
  private GraphStore graphStore;

  @AfterEach
  public void storageSelfCleaning() {
    assetStorePublisher.clear();
  }

  @AfterAll
  void closeNeo4j() {
    embeddedDatabaseServer.close();
  }

  private static AssetMetadata createAssetMetadata(final String id, final String issuer,
      final Instant statusTime, final Instant uploadTime, final String content) {
    final String hash = HashUtils.calculateSha256AsHex(content);
    AssetMetadata assetMeta = new AssetMetadata();
    assetMeta.setId(id);
    assetMeta.setIssuer(issuer);
    assetMeta.setAssetHash(hash);
    assetMeta.setStatus(AssetStatus.ACTIVE);
    assetMeta.setStatusDatetime(statusTime);
    assetMeta.setUploadDatetime(uploadTime);
    assetMeta.setContentAccessor(new ContentAccessorDirect(content));
    return assetMeta;
  }

  private static List<CredentialClaim> createClaims(String subject) {
    final CredentialClaim claim1 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#providedBy>", "<https://delta-dao.com/.well-known/participant.json>");
    final CredentialClaim claim2 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#name>", "\"EuProGigant Portal\"");
    final CredentialClaim claim3 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#description>", "\"EuProGigant Minimal Viable Gaia-X Portal\"");
    final CredentialClaim claim4 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#TermsAndConditions>", "<https://euprogigant.com/en/terms/>");
    final CredentialClaim claim5 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#TermsAndConditions>", "\"contentHash\"");
    return List.of(claim1, claim2, claim3, claim4, claim5);
  }

  private static CredentialVerificationResult createVerificationResult(final int idSuffix, String subject) {
    return new CredentialVerificationResultOffering(Instant.now(), AssetStatus.ACTIVE.getValue(), "issuer" + idSuffix, Instant.now(),
        "id" + idSuffix, createClaims(subject), new ArrayList<>());
  }

  private static CredentialVerificationResult createVerificationResult(final int idSuffix) {
    return createVerificationResult(idSuffix, "<https://delta-dao.com/.well-known/serviceMVGPortal.json>");
  }

  private static CredentialVerificationResult createVerificationResult(AssetMetadata assetMeta) {
	List<Validator> vals = null;
	if (assetMeta.getValidatorDids() != null) {
		vals = new ArrayList<>(assetMeta.getValidatorDids().size());
		for (String did: assetMeta.getValidatorDids()) {
			vals.add(new Validator(did, "PK", Instant.now().plusSeconds(3600)));
		}
	}
    return new CredentialVerificationResultOffering(assetMeta.getStatusDatetime(), AssetStatus.ACTIVE.getValue(), assetMeta.getIssuer(), assetMeta.getUploadDatetime(),
            assetMeta.getId(), createClaims("<https://delta-dao.com/.well-known/serviceMVGPortal.json>"), vals); 
  }
  
  /**
   * Test storing a credential, ensuring it creates exactly one file on disk, retrieving it by hash, and deleting
   * it again.
   */
  @Test
  void test01StoreCredential() {
    log.info("test01StoreCredential");
    final String content = "Some Test Content";

    final AssetMetadata assetMeta = createAssetMetadata("https://delta-dao.com/.well-known/serviceMVGPortal.json", // "TestAsset/1",
        "TestUser/1",
        Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content);
    final String hash = assetMeta.getAssetHash();
    CredentialVerificationResult vr = createVerificationResult(assetMeta); //0);
    assetStorePublisher.storeCredential(assetMeta, vr);

    assertThatAssetHasTheSameData(assetMeta, assetStorePublisher.getByHash(hash), true);

    List<Map<String, Object>> claims = graphStore.queryData(
        new GraphQuery("MATCH (n {uri: $uri}) RETURN n", Map.of("uri", assetMeta.getId()))).getResults();
    Assertions.assertTrue(claims.size() > 0); //only 1 node found..

    final ContentAccessor credentialFileByHash = assetStorePublisher.getFileByHash(hash);
    assertEquals(credentialFileByHash, assetMeta.getContentAccessor(), "Getting the credential file by hash is equal to the stored credential file");

    assetStorePublisher.deleteAsset(hash);

    claims = graphStore.queryData(new GraphQuery("MATCH (n {uri: $uri}) RETURN n", Map.of("uri", assetMeta.getId()))).getResults();
    Assertions.assertEquals(0, claims.size());

    Assertions.assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
  }

  @Test
  void test03StoreDuplicateCredential() {
    log.info("test03StoreDuplicateCredential");
    final String content1 = "Some Test Content";

    final AssetMetadata assetMeta1 = createAssetMetadata("TestAsset/1", "TestUser/1",
        Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content1);
    final String hash1 = assetMeta1.getAssetHash();
    assetStorePublisher.storeCredential(assetMeta1, createVerificationResult(assetMeta1));

    List<Map<String, Object>> nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestAsset/1")
    )).getResults();
    log.debug("test03StoreDuplicateCredential-1; got {} nodes", nodes.size());
    Assertions.assertEquals(3, nodes.size());

    final AssetMetadata assetMeta2 = createAssetMetadata("TestAsset/1", "TestUser/1",
        Instant.parse("2022-01-01T13:00:00Z"), Instant.parse("2022-01-02T13:00:00Z"), content1);
    Assertions.assertThrows(ConflictException.class, () -> assetStorePublisher.storeCredential(assetMeta2, createVerificationResult(assetMeta2)));

    nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestAsset/1")
    )).getResults();
    log.debug("test03StoreDuplicateCredential-2; got {} nodes", nodes.size());
    Assertions.assertEquals(3, nodes.size(), "After failed put, node count should not have changed");

    final AssetMetadata byHash1 = assetStorePublisher.getByHash(hash1);
    final AssetStatus status1 = byHash1.getStatus();
    assertEquals(AssetStatus.ACTIVE, status1, "First credential should stay active.");

    assetStorePublisher.deleteAsset(hash1);

    Assertions.assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash1));
  }

  /**
   * Test storing a credential, and updating the status.
   */
  @Test
  void test04ChangeAssetStatus() {
    log.info("test04ChangeAssetStatus");
    final String content = "Some Test Content";

    final AssetMetadata assetMeta = createAssetMetadata("TestAsset/1", "TestUser/1",
        Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content);
    final String hash = assetMeta.getAssetHash();
    assetStorePublisher.storeCredential(assetMeta, createVerificationResult(assetMeta));

    AssetMetadata byHash = assetStorePublisher.getByHash(hash);
    assertThatAssetHasTheSameData(assetMeta, byHash, true);

    List<Map<String, Object>> nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestAsset/1")
    )).getResults();
    log.debug("test04ChangeAssetStatus-1; got {} nodes", nodes.size());
    Assertions.assertEquals(3, nodes.size());

    assetStorePublisher.changeLifeCycleStatus(hash, AssetStatus.REVOKED);
    byHash = assetStorePublisher.getByHash(hash);
    assertEquals(AssetStatus.REVOKED, byHash.getStatus(), "Status should have been changed to 'revoked'");

    Assertions.assertThrows(ConflictException.class, () -> assetStorePublisher.changeLifeCycleStatus(hash, AssetStatus.ACTIVE));
    byHash = assetStorePublisher.getByHash(hash);
    assertEquals(AssetStatus.REVOKED, byHash.getStatus(),
        "Status should not have been changed from 'revoked' to 'active'.");

    nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestAsset/1")
    )).getResults();
    log.debug("test04ChangeAssetStatus-2; got {} nodes", nodes.size());
    Assertions.assertEquals(0, nodes.size(), "Revoked credential should not appear in queries");

    Assertions.assertThrows(ConflictException.class, () -> assetStorePublisher.storeCredential(assetMeta, createVerificationResult(0)), "Adding the same credential after revocation should not be possible.");

    nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestAsset/1")
    )).getResults();
    log.debug("test04ChangeAssetStatus-3; got {} nodes", nodes.size());
    Assertions.assertEquals(0, nodes.size(), "Revoked credential should not appear in queries");

    assetStorePublisher.deleteAsset(hash);

    Assertions.assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
  }

}
