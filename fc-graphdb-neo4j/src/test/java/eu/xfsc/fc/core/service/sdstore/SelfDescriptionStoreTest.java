package eu.xfsc.fc.core.service.sdstore;

import static eu.xfsc.fc.core.util.TestUtil.assertThatSdHasTheSameData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
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

import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.dao.impl.SelfDescriptionDaoImpl;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
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
@ContextConfiguration(classes = {SelfDescriptionStoreTest.TestApplication.class, SelfDescriptionStoreImpl.class, SelfDescriptionDaoImpl.class, SelfDescriptionStoreTest.class,
  Neo4jGraphStore.class, DatabaseConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class, DidResolverConfig.class, HttpDocumentResolver.class}) 
@Slf4j
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class SelfDescriptionStoreTest {

  @SpringBootApplication
  public static class TestApplication {

    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private SelfDescriptionStore sdStorePublisher;

  @Autowired
  private Neo4j embeddedDatabaseServer;

  @Autowired
  private GraphStore graphStore;

  @AfterEach
  public void storageSelfCleaning() throws IOException {
    sdStorePublisher.clear();
  }

  @AfterAll
  void closeNeo4j() {
    embeddedDatabaseServer.close();
  }

  private static SelfDescriptionMetadata createSelfDescriptionMeta(final String id, final String issuer,
      final Instant sdt, final Instant udt, final String content) {
    final String hash = HashUtils.calculateSha256AsHex(content);
    SelfDescriptionMetadata sdMeta = new SelfDescriptionMetadata();
    sdMeta.setId(id);
    sdMeta.setIssuer(issuer);
    sdMeta.setSdHash(hash);
    sdMeta.setStatus(SelfDescriptionStatus.ACTIVE);
    sdMeta.setStatusDatetime(sdt);
    sdMeta.setUploadDatetime(udt);
    sdMeta.setSelfDescription(new ContentAccessorDirect(content));
    return sdMeta;
  }

  private static List<SdClaim> createClaims(String subject) {
    final SdClaim claim1 = new SdClaim(subject, "<https://www.w3id.org/gaia-x/service#providedBy>", "<https://delta-dao.com/.well-known/participant.json>");
    final SdClaim claim2 = new SdClaim(subject, "<https://www.w3id.org/gaia-x/service#name>", "\"EuProGigant Portal\"");
    final SdClaim claim3 = new SdClaim(subject, "<https://www.w3id.org/gaia-x/service#description>", "\"EuProGigant Minimal Viable Gaia-X Portal\"");
    final SdClaim claim4 = new SdClaim(subject, "<https://www.w3id.org/gaia-x/service#TermsAndConditions>", "<https://euprogigant.com/en/terms/>");
    final SdClaim claim5 = new SdClaim(subject, "<https://www.w3id.org/gaia-x/service#TermsAndConditions>", "\"contentHash\"");
    return List.of(claim1, claim2, claim3, claim4, claim5);
  }

  private static VerificationResult createVerificationResult(final int idSuffix, String subject) {
    return new VerificationResultOffering(Instant.now(), SelfDescriptionStatus.ACTIVE.getValue(), "issuer" + idSuffix, Instant.now(),
        "id" + idSuffix, createClaims(subject), new ArrayList<>());
  }

  private static VerificationResult createVerificationResult(final int idSuffix) {
    return createVerificationResult(idSuffix, "<https://delta-dao.com/.well-known/serviceMVGPortal.json>");
  }

  private static VerificationResult createVerificationResult(SelfDescriptionMetadata sdMeta) {
	List<Validator> vals = null;
	if (sdMeta.getValidatorDids() != null) {
		vals = new ArrayList<>(sdMeta.getValidatorDids().size());
		for (String did: sdMeta.getValidatorDids()) {
			vals.add(new Validator(did, "PK", Instant.now().plusSeconds(3600)));
		}
	}
    return new VerificationResultOffering(sdMeta.getStatusDatetime(), SelfDescriptionStatus.ACTIVE.getValue(), sdMeta.getIssuer(), sdMeta.getUploadDatetime(),
            sdMeta.getId(), createClaims("<https://delta-dao.com/.well-known/serviceMVGPortal.json>"), vals); 
  }
  
  /**
   * Test storing a self-description, ensuring it creates exactly one file on disk, retrieving it by hash, and deleting
   * it again.
   */
  @Test
  void test01StoreSelfDescription() throws Exception {
    log.info("test01StoreSelfDescription");
    final String content = "Some Test Content";

    final SelfDescriptionMetadata sdMeta = createSelfDescriptionMeta("https://delta-dao.com/.well-known/serviceMVGPortal.json", // "TestSd/1",
        "TestUser/1",
        Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content);
    final String hash = sdMeta.getSdHash();
    VerificationResult vr = createVerificationResult(sdMeta); //0);
    sdStorePublisher.storeSelfDescription(sdMeta, vr);

    assertThatSdHasTheSameData(sdMeta, sdStorePublisher.getByHash(hash), true);

    List<Map<String, Object>> claims = graphStore.queryData(
        new GraphQuery("MATCH (n {uri: $uri}) RETURN n", Map.of("uri", sdMeta.getId()))).getResults();
    Assertions.assertTrue(claims.size() > 0); //only 1 node found..

    final ContentAccessor sdfileByHash = sdStorePublisher.getSDFileByHash(hash);
    assertEquals(sdfileByHash, sdMeta.getSelfDescription(), "Getting the SD file by hash is equal to the stored SD file");

    sdStorePublisher.deleteSelfDescription(hash);

    claims = graphStore.queryData(new GraphQuery("MATCH (n {uri: $uri}) RETURN n", Map.of("uri", sdMeta.getId()))).getResults();
    Assertions.assertEquals(0, claims.size());

    Assertions.assertThrows(NotFoundException.class, () -> {sdStorePublisher.getByHash(hash);
    });
  }

  @Test
  void test03StoreDuplicateSelfDescription() {
    log.info("test03StoreDuplicateSelfDescription");
    final String content1 = "Some Test Content";

    final SelfDescriptionMetadata sdMeta1 = createSelfDescriptionMeta("TestSd/1", "TestUser/1",
        Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content1);
    final String hash1 = sdMeta1.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta1, createVerificationResult(sdMeta1));

    List<Map<String, Object>> nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestSd/1")
    )).getResults();
    log.debug("test03StoreDuplicateSelfDescription-1; got {} nodes", nodes.size());
    Assertions.assertEquals(3, nodes.size());

    final SelfDescriptionMetadata sdMeta2 = createSelfDescriptionMeta("TestSd/1", "TestUser/1",
        Instant.parse("2022-01-01T13:00:00Z"), Instant.parse("2022-01-02T13:00:00Z"), content1);
    Assertions.assertThrows(ConflictException.class, () -> {
      sdStorePublisher.storeSelfDescription(sdMeta2, createVerificationResult(sdMeta2));
    });

    nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestSd/1")
    )).getResults();
    log.debug("test03StoreDuplicateSelfDescription-2; got {} nodes", nodes.size());
    Assertions.assertEquals(3, nodes.size(), "After failed put, node count should not have changed");

    final SelfDescriptionMetadata byHash1 = sdStorePublisher.getByHash(hash1);
    final SelfDescriptionStatus status1 = byHash1.getStatus();
    assertEquals(SelfDescriptionStatus.ACTIVE, status1, "First self-description should stay active.");

    sdStorePublisher.deleteSelfDescription(hash1);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash1);
    });
  }

  /**
   * Test storing a self-description, and updating the status.
   */
  @Test
  void test04ChangeSelfDescriptionStatus() throws Exception {
    log.info("test04ChangeSelfDescriptionStatus");
    final String content = "Some Test Content";

    final SelfDescriptionMetadata sdMeta = createSelfDescriptionMeta("TestSd/1", "TestUser/1",
        Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content);
    final String hash = sdMeta.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta, createVerificationResult(sdMeta));

    SelfDescriptionMetadata byHash = sdStorePublisher.getByHash(hash);
    assertThatSdHasTheSameData(sdMeta, byHash, true);

    List<Map<String, Object>> nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestSd/1")
    )).getResults();
    log.debug("test04ChangeSelfDescriptionStatus-1; got {} nodes", nodes.size());
    Assertions.assertEquals(3, nodes.size());

    sdStorePublisher.changeLifeCycleStatus(hash, SelfDescriptionStatus.REVOKED);
    byHash = sdStorePublisher.getByHash(hash);
    assertEquals(SelfDescriptionStatus.REVOKED, byHash.getStatus(), "Status should have been changed to 'revoked'");

    Assertions.assertThrows(ConflictException.class, () -> {
      sdStorePublisher.changeLifeCycleStatus(hash, SelfDescriptionStatus.ACTIVE);
    });
    byHash = sdStorePublisher.getByHash(hash);
    assertEquals(SelfDescriptionStatus.REVOKED, byHash.getStatus(),
        "Status should not have been changed from 'revoked' to 'active'.");

    nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestSd/1")
    )).getResults();
    log.debug("test04ChangeSelfDescriptionStatus-2; got {} nodes", nodes.size());
    Assertions.assertEquals(0, nodes.size(), "Revoked SD should not appear in queries");

    Assertions.assertThrows(ConflictException.class, () -> {
      sdStorePublisher.storeSelfDescription(sdMeta, createVerificationResult(0));
    }, "Adding the same SD after revokation should not be possible.");

    nodes = graphStore.queryData(new GraphQuery(
        "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
        Map.of("graphUri", "TestSd/1")
    )).getResults();
    log.debug("test04ChangeSelfDescriptionStatus-3; got {} nodes", nodes.size());
    Assertions.assertEquals(0, nodes.size(), "Revoked SD should not appear in queries");

    sdStorePublisher.deleteSelfDescription(hash);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });
  }

}
