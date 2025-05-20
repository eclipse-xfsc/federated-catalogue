package eu.xfsc.fc.core.service.sdstore;

import static eu.xfsc.fc.core.util.TestUtil.assertThatSdHasTheSameData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
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
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SdFilter;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import eu.xfsc.fc.core.service.graphdb.Neo4jGraphStore;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.util.HashUtils;
import eu.xfsc.fc.testsupport.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {SelfDescriptionStoreTest.TestApplication.class, SelfDescriptionStoreImpl.class, SelfDescriptionDaoImpl.class, SelfDescriptionStoreTest.class,
  DatabaseConfig.class, Neo4jGraphStore.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class, DidResolverConfig.class, HttpDocumentResolver.class})
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
  private Neo4jGraphStore graphStore;

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

  /**
   * Test storing a self-description, and deprecating it by storing a second SD with the same subjectId.
   */
  @Test
  void test02StoreAndUpdateSelfDescription() {
    log.info("test02StoreAndUpdateSelfDescription");
    final String content1 = "Some Test Content 1";
    final String content2 = "Some Test Content 2";

    final SelfDescriptionMetadata sdMeta1 = createSelfDescriptionMeta("TestSd/1", "TestUser/1",
        Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content1);
    final String hash1 = sdMeta1.getSdHash();
    sdMeta1.setSelfDescription(new ContentAccessorDirect(content1));
    sdStorePublisher.storeSelfDescription(sdMeta1, createVerificationResult(sdMeta1));

    final SelfDescriptionMetadata sdMeta2 = createSelfDescriptionMeta("TestSd/1", "TestUser/1",
        Instant.parse("2022-01-01T13:00:00Z"), Instant.parse("2022-01-02T13:00:00Z"), content2);
    final String hash2 = sdMeta2.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta2, createVerificationResult(sdMeta2));

    final SelfDescriptionMetadata byHash1 = sdStorePublisher.getByHash(hash1);
    assertEquals(SelfDescriptionStatus.DEPRECATED, byHash1.getStatus(),
        "First self-description should have been depricated.");
    assertTrue(byHash1.getStatusDatetime().isAfter(sdMeta1.getStatusDatetime()));
    assertThatSdHasTheSameData(sdMeta2, sdStorePublisher.getByHash(hash2), true);

    sdStorePublisher.deleteSelfDescription(hash1);
    sdStorePublisher.deleteSelfDescription(hash2);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash1);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash2);
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

  /**
   * Test applying an SD filter on matching issuer.
   */
  @Test
  void test05FilterMatchingIssuer() {
    log.info("test05FilterMatchingIssuer");
    final String id = "TestSd/1";
    final String issuer = "TestUser/1";
    final String content = "Test: Fetch SD Meta Data via SD Filter, test for matching issuer";
    final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
    final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
    final SelfDescriptionMetadata sdMeta = createSelfDescriptionMeta(id, issuer, statusTime, uploadTime, content);
    final String hash = sdMeta.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta, createVerificationResult(sdMeta));

    final SdFilter filterParams = new SdFilter();
    filterParams.setIssuers(List.of(issuer, "TestUser/21"));
    PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, true);
    int matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
    assertEquals(sdMeta.getId(), byFilter.getResults().get(0).getId());
    SelfDescriptionMetadata firstResult = byFilter.getResults().get(0);
    assertEquals(sdMeta.getSdHash(), firstResult.getSdHash(), "Incorrect Hash");
    assertEquals(sdMeta.getId(), firstResult.getId(), "Incorrect SubjectId");
    assertEquals(sdMeta.getSelfDescription(), firstResult.getSelfDescription(), "Incorrect SelfDescription content");

    byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
    firstResult = byFilter.getResults().get(0);
    assertEquals(sdMeta.getSdHash(), firstResult.getSdHash(), "Incorrect Hash");
    assertEquals(sdMeta.getId(), firstResult.getId(), "Incorrect SubjectId");
    Assertions.assertNull(firstResult.getSelfDescription(), "SelfDescription should not have been returned.");

    byFilter = sdStorePublisher.getByFilter(filterParams, false, true);
    matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
    firstResult = byFilter.getResults().get(0);
    assertEquals(sdMeta.getSdHash(), firstResult.getSdHash(), "Incorrect Hash");
    Assertions.assertNull(firstResult.getId(), "SubjectId should not have been returned");
    assertEquals(sdMeta.getSelfDescription(), firstResult.getSelfDescription(), "Incorrect SelfDescription content");

    byFilter = sdStorePublisher.getByFilter(filterParams, false, false);
    matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
    firstResult = byFilter.getResults().get(0);
    assertEquals(sdMeta.getSdHash(), firstResult.getSdHash(), "Incorrect Hash");
    Assertions.assertNull(firstResult.getId(), "SubjectId should not have been returned");
    Assertions.assertNull(firstResult.getSelfDescription(), "SelfDescription should not have been returned.");

    sdStorePublisher.deleteSelfDescription(hash);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });
    log.info("#### Test 05 succeeded.");
  }

  /**
   * Test applying an SD filter on non-matching issuer.
   */
  @Test
  void test06FilterNonMatchingIssuer() {
    log.info("test06FilterNonMatchingIssuer");
    final String id = "TestSd/1";
    final String issuer = "TestUser/1";
    final String otherIssuer = "TestUser/2";
    final String content = "Test: Fetch SD Meta Data via SD Filter, test for non-matching issuer";
    final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
    final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
    final SelfDescriptionMetadata sdMeta = createSelfDescriptionMeta(id, issuer, statusTime, uploadTime, content);
    final String hash = sdMeta.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta, createVerificationResult(sdMeta));

    final SdFilter filterParams = new SdFilter();
    filterParams.setIssuers(List.of(otherIssuer));
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final int matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(0, matchCount, "expected 0 filter matches, but got " + matchCount);

    sdStorePublisher.deleteSelfDescription(hash);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });

    log.info("#### Test 06 succeeded.");
  }

  /**
   * Test applying an SD filter on matching status start time.
   */
  @Test
  void test07FilterMatchingStatusTimeStart() {
    log.info("test07FilterMatchingStatusTimeStart");
    final String id = "TestSd/1";
    final String issuer = "TestUser/1";
    final String content = "Test: Fetch SD Meta Data via SD Filter, test for matching status time start";
    final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
    final Instant statusTimeStart = Instant.parse("2021-01-01T12:00:00Z");
    final Instant statusTimeEnd = Instant.parse("2022-01-01T12:00:00Z");
    final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
    final SelfDescriptionMetadata sdMeta = createSelfDescriptionMeta(id, issuer, statusTime, uploadTime, content);
    final String hash = sdMeta.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta, createVerificationResult(sdMeta));

    final SdFilter filterParams = new SdFilter();
    filterParams.setStatusTimeRange(statusTimeStart, statusTimeEnd);
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final int matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
    assertEquals(sdMeta.getId(), byFilter.getResults().get(0).getId());

    sdStorePublisher.deleteSelfDescription(hash);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });
    log.info("#### Test 07 succeeded.");
  }

  /**
   * Test applying an SD filter on non-matching issuer.
   */
  @Test
  void test08FilterNonMatchingStatusTimeStart() {
    log.info("test08FilterNonMatchingStatusTimeStart");
    final String id = "TestSd/1";
    final String issuer = "TestUser/1";
    final String content = "Test: Fetch SD Meta Data via SD Filter, test for non-matching issuer";
    final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
    final Instant statusTimeStart = Instant.parse("2022-02-01T12:00:00Z");
    final Instant statusTimeEnd = Instant.parse("2023-02-01T12:00:00Z");
    final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
    final SelfDescriptionMetadata sdMeta = createSelfDescriptionMeta(id, issuer, statusTime, uploadTime, content);
    final String hash = sdMeta.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta, createVerificationResult(sdMeta));

    final SdFilter filterParams = new SdFilter();
    filterParams.setStatusTimeRange(statusTimeStart, statusTimeEnd);
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final int matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(0, matchCount, "expected 0 filter matches, but got " + matchCount);

    sdStorePublisher.deleteSelfDescription(hash);
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });

    log.info("#### Test 08 succeeded.");
  }

  /**
   * Test applying an SD filter that matches multiple records.
   */
  @Test
  void test09FilterMatchingMultipleRecords() {
    log.info("test09FilterMatchingMultipleRecords");
    final String id1 = "TestSd/1";
    final String id2 = "TestSd/2";
    final String id3 = "TestSd/3";
    final String issuer1 = "TestUser/1";
    final String issuer2 = "TestUser/2";
    final String issuer3 = "TestUser/3";
    final String content1 = "Test: Fetch SD Meta Data via SD Filter, test for matching status time start (1/3)";
    final String content2 = "Test: Fetch SD Meta Data via SD Filter, test for matching status time start (2/3)";
    final String content3 = "Test: Fetch SD Meta Data via SD Filter, test for matching status time start (3/3)";
    final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
    final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
    final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
    final Instant statusTimeStart = Instant.parse("2022-01-01T12:00:00Z");
    final Instant statusTimeEnd = Instant.parse("2022-01-02T12:00:00Z");
    final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
    final SelfDescriptionMetadata sdMeta1 = createSelfDescriptionMeta(id1, issuer1, statusTime1, uploadTime1, content1);
    final SelfDescriptionMetadata sdMeta2 = createSelfDescriptionMeta(id2, issuer2, statusTime2, uploadTime2, content2);
    final SelfDescriptionMetadata sdMeta3 = createSelfDescriptionMeta(id3, issuer3, statusTime3, uploadTime3, content3);
    final String hash1 = sdMeta1.getSdHash();
    final String hash2 = sdMeta2.getSdHash();
    final String hash3 = sdMeta3.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta1, createVerificationResult(sdMeta1));
    sdStorePublisher.storeSelfDescription(sdMeta2, createVerificationResult(sdMeta2));
    sdStorePublisher.storeSelfDescription(sdMeta3, createVerificationResult(sdMeta3));

    final SdFilter filterParams = new SdFilter();
    filterParams.setStatusTimeRange(statusTimeStart, statusTimeEnd);
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final int matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(2, matchCount, "expected 2 filter match, but got " + matchCount);
    final SelfDescriptionMetadata filterSdMeta1 = byFilter.getResults().get(0);
    final SelfDescriptionMetadata filterSdMeta2 = byFilter.getResults().get(1);
    assertEquals(true, sdMeta1.getId().equals(filterSdMeta1.getId()) || sdMeta1.getId().equals(filterSdMeta2.getId()),
        "expected filter match sdMeta1 missing in results");
    assertEquals(true, sdMeta2.getId().equals(filterSdMeta1.getId()) || sdMeta2.getId().equals(filterSdMeta2.getId()),
        "expected filter match sdMeta2 missing in results");

    sdStorePublisher.deleteSelfDescription(hash1);
    sdStorePublisher.deleteSelfDescription(hash2);
    sdStorePublisher.deleteSelfDescription(hash3);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash1);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash2);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash3);
    });

    log.info("#### Test 09 succeeded.");
  }

  /**
   * Test applying an empty SD filter for matching all records.
   */
  @Test
  void test10EmptyFilterMatchingMultipleRecords() {
    log.info("test10EmptyFilterMatchingAllRecords");
    final String id1 = "TestSd/1";
    final String id2 = "TestSd/2";
    final String id3 = "TestSd/3";
    final String issuer1 = "TestUser/1";
    final String issuer2 = "TestUser/2";
    final String issuer3 = "TestUser/3";
    final String content1 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (1/3)";
    final String content2 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (2/3)";
    final String content3 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (3/3)";
    final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
    final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
    final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
    final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
    final SelfDescriptionMetadata sdMeta1 = createSelfDescriptionMeta(id1, issuer1, statusTime1, uploadTime1, content1);
    final SelfDescriptionMetadata sdMeta2 = createSelfDescriptionMeta(id2, issuer2, statusTime2, uploadTime2, content2);
    final SelfDescriptionMetadata sdMeta3 = createSelfDescriptionMeta(id3, issuer3, statusTime3, uploadTime3, content3);
    final String hash1 = sdMeta1.getSdHash();
    final String hash2 = sdMeta2.getSdHash();
    final String hash3 = sdMeta3.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta1, createVerificationResult(sdMeta1));
    sdStorePublisher.storeSelfDescription(sdMeta2, createVerificationResult(sdMeta2));
    sdStorePublisher.storeSelfDescription(sdMeta3, createVerificationResult(sdMeta3));

    final SdFilter filterParams = new SdFilter();
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final int matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(3, matchCount, "expected 3 filter match, but got " + matchCount);
    final SelfDescriptionMetadata filterSdMeta1 = byFilter.getResults().get(0);
    final SelfDescriptionMetadata filterSdMeta2 = byFilter.getResults().get(1);
    final SelfDescriptionMetadata filterSdMeta3 = byFilter.getResults().get(2);
    assertEquals(true, sdMeta1.getId().equals(filterSdMeta1.getId()) || sdMeta1.getId().equals(filterSdMeta2.getId())
        || sdMeta1.getId().equals(filterSdMeta3.getId()), "expected filter match sdMeta1 missing in results");
    assertEquals(true, sdMeta2.getId().equals(filterSdMeta1.getId()) || sdMeta2.getId().equals(filterSdMeta2.getId())
        || sdMeta2.getId().equals(filterSdMeta3.getId()), "expected filter match sdMeta2 missing in results");
    assertEquals(true, sdMeta3.getId().equals(filterSdMeta1.getId()) || sdMeta3.getId().equals(filterSdMeta2.getId())
        || sdMeta3.getId().equals(filterSdMeta3.getId()), "expected filter match sdMeta3 missing in results");
    sdStorePublisher.deleteSelfDescription(hash1);
    sdStorePublisher.deleteSelfDescription(hash2);
    sdStorePublisher.deleteSelfDescription(hash3);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash1);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash2);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash3);
    });

    log.info("#### Test 10 succeeded.");
  }

  /**
   * Test applying an SD filter on non-matching validator.
   */
  @Test
  void test11AFilterMatchingValidator() throws Exception {
    log.info("test11AFilterMatchingValidator");
    final String id = "TestSd/1";
    final String validatorId = "TestSd/0815";
    final String issuer = "TestUser/1";
    final String content = "Test: Fetch SD Meta Data via SD Filter, test for matching validator";
    final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
    final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
    final SelfDescriptionMetadata sdMeta = createSelfDescriptionMeta(id, issuer, statusTime, uploadTime, content);
    sdMeta.setValidatorDids(Arrays.asList(validatorId, "TestSd/0816", "TestSd/0817"));
    final String hash = sdMeta.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta, createVerificationResult(sdMeta));

    final SdFilter filterParams = new SdFilter();
    filterParams.setValidators(List.of(validatorId, "TestSd/0820"));
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final long matchCount = byFilter.getTotalCount();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(1, matchCount, "expected 1 filter matches");

    sdStorePublisher.deleteSelfDescription(hash);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });

    log.info("#### Test 11A succeeded.");
  }

  /**
   * Test applying an SD filter on non-matching validator.
   */
  @Test
  void test11BFilterNonMatchingValidator() throws Exception {
    log.info("test11BFilterNonMatchingValidator");
    final String id = "TestSd/1";
    final String validatorId = "TestSd/0815";
    final String issuer = "TestUser/1";
    final String content = "Test: Fetch SD Meta Data via SD Filter, test for non-matching validator";
    final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
    final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
    final SelfDescriptionMetadata sdMeta = createSelfDescriptionMeta(id, issuer, statusTime, uploadTime, content);
    sdMeta.setValidatorDids(Arrays.asList("TestSd/0816", "TestSd/0817"));
    final String hash = sdMeta.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta, createVerificationResult(sdMeta));

    final SdFilter filterParams = new SdFilter();
    filterParams.setValidators(List.of(validatorId));
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final int matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(0, matchCount, "expected 0 filter matches");

    sdStorePublisher.deleteSelfDescription(hash);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash);
    });

    log.info("#### Test 11B succeeded.");
  }

  /**
   * Test applying an SD filter with limited number of results.
   */
  @Test
  void test12FilterLimit() {
    log.info("test12FilterLimit");
    final String id1 = "TestSd/1";
    final String id2 = "TestSd/2";
    final String id3 = "TestSd/3";
    final String issuer1 = "TestUser/1";
    final String issuer2 = "TestUser/2";
    final String issuer3 = "TestUser/3";
    final String content1 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (1/3)";
    final String content2 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (2/3)";
    final String content3 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (3/3)";
    final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
    final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
    final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
    final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
    final SelfDescriptionMetadata sdMeta1 = createSelfDescriptionMeta(id1, issuer1, statusTime1, uploadTime1, content1);
    final SelfDescriptionMetadata sdMeta2 = createSelfDescriptionMeta(id2, issuer2, statusTime2, uploadTime2, content2);
    final SelfDescriptionMetadata sdMeta3 = createSelfDescriptionMeta(id3, issuer3, statusTime3, uploadTime3, content3);
    final String hash1 = sdMeta1.getSdHash();
    final String hash2 = sdMeta2.getSdHash();
    final String hash3 = sdMeta3.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta1, createVerificationResult(sdMeta1));
    sdStorePublisher.storeSelfDescription(sdMeta2, createVerificationResult(sdMeta2));
    sdStorePublisher.storeSelfDescription(sdMeta3, createVerificationResult(sdMeta3));

    final SdFilter filterParams = new SdFilter();
    filterParams.setLimit(2);
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final int matchCount = byFilter.getResults().size();
    log.info("filter returned {} match(es)", matchCount);
    assertEquals(2, matchCount, "expected 2 filter match, but got " + matchCount);
    sdStorePublisher.deleteSelfDescription(hash1);
    sdStorePublisher.deleteSelfDescription(hash2);
    sdStorePublisher.deleteSelfDescription(hash3);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash1);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash2);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash3);
    });
    log.info("#### Test 12 succeeded.");
  }

  private VerificationResult verifySd(String id, Instant firstSigInstant) throws UnsupportedEncodingException {
    List<Validator> signatures = new ArrayList<>();
    signatures.add(new Validator("did:first", "", firstSigInstant));
    signatures.add(new Validator("did:second", "", Instant.now().plus(1, ChronoUnit.DAYS)));
    signatures.add(new Validator("did:third", "", Instant.now().plus(2, ChronoUnit.DAYS)));
    return new VerificationResult(Instant.now(), SelfDescriptionStatus.ACTIVE.getValue(), "issuer", Instant.now(),
        id, new ArrayList<>(), signatures);
  }

  @Test
  void test13PeriodicValidationOfSignatures() throws IOException {
    log.info("test13PeriodicValidationOfSignatures");
    final String id1 = "TestSd/1";
    final String id2 = "TestSd/2";
    final String id3 = "TestSd/3";
    final String issuer1 = "TestUser/1";
    final String issuer2 = "TestUser/2";
    final String issuer3 = "TestUser/3";
    final String content1 = "Test: SD 1 with future expiration date";
    final String content2 = "Test: SD 2 with past expiration date";
    final String content3 = "Test: SD 3 with future expiration date";
    final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
    final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
    final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
    final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
    final SelfDescriptionMetadata sdMeta1 = createSelfDescriptionMeta(id1, issuer1, statusTime1, uploadTime1, content1);
    final SelfDescriptionMetadata sdMeta2 = createSelfDescriptionMeta(id2, issuer2, statusTime2, uploadTime2, content2);
    final SelfDescriptionMetadata sdMeta3 = createSelfDescriptionMeta(id3, issuer3, statusTime3, uploadTime3, content3);
    final String hash1 = sdMeta1.getSdHash();
    final String hash2 = sdMeta2.getSdHash();
    final String hash3 = sdMeta3.getSdHash();
    final VerificationResult vr1 = verifySd(id1, Instant.now().plus(1, ChronoUnit.DAYS));
    final VerificationResult vr2 = verifySd(id2, Instant.now().minus(1, ChronoUnit.DAYS));
    final VerificationResult vr3 = verifySd(id3, Instant.now().plus(1, ChronoUnit.DAYS));
    sdStorePublisher.storeSelfDescription(sdMeta1, vr1);
    sdStorePublisher.storeSelfDescription(sdMeta2, vr2);
    sdStorePublisher.storeSelfDescription(sdMeta3, vr3);

    final int expiredSelfDescriptionsCount = sdStorePublisher.invalidateSelfDescriptions();
    assertEquals(1, expiredSelfDescriptionsCount, "expected 1 expired self-description");
    assertEquals(SelfDescriptionStatus.ACTIVE, sdStorePublisher.getByHash(hash1).getStatus(), "Status should not have been changed.");
    assertEquals(SelfDescriptionStatus.EOL, sdStorePublisher.getByHash(hash2).getStatus(), "Status should have been changed.");
    assertEquals(SelfDescriptionStatus.ACTIVE, sdStorePublisher.getByHash(hash3).getStatus(), "Status should not have been changed.");

    sdStorePublisher.deleteSelfDescription(hash1);
    sdStorePublisher.deleteSelfDescription(hash2);
    sdStorePublisher.deleteSelfDescription(hash3);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash1);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash2);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash3);
    });
    log.info("#### Test 13 succeeded.");
  }

  /**
   * Test applying an SD filter with limited number of results with total SD count number.
   */
  @Test
  void test13FilterLimitWithTotalCount() {
    log.info("test12FilterLimit");
    final String id1 = "TestSd/1";
    final String id2 = "TestSd/2";
    final String id3 = "TestSd/3";
    final String issuer1 = "TestUser/1";
    final String issuer2 = "TestUser/2";
    final String issuer3 = "TestUser/3";
    final String content1 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (1/3)";
    final String content2 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (2/3)";
    final String content3 = "Test: Fetch SD Meta Data via SD Filter, test for matching empty filter (3/3)";
    final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
    final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
    final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
    final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
    final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
    final SelfDescriptionMetadata sdMeta1 = createSelfDescriptionMeta(id1, issuer1, statusTime1, uploadTime1, content1);
    final SelfDescriptionMetadata sdMeta2 = createSelfDescriptionMeta(id2, issuer2, statusTime2, uploadTime2, content2);
    final SelfDescriptionMetadata sdMeta3 = createSelfDescriptionMeta(id3, issuer3, statusTime3, uploadTime3, content3);
    final String hash1 = sdMeta1.getSdHash();
    final String hash2 = sdMeta2.getSdHash();
    final String hash3 = sdMeta3.getSdHash();
    sdStorePublisher.storeSelfDescription(sdMeta1, createVerificationResult(1));
    sdStorePublisher.storeSelfDescription(sdMeta2, createVerificationResult(2));
    sdStorePublisher.storeSelfDescription(sdMeta3, createVerificationResult(3));

    final SdFilter filterParams = new SdFilter();
    filterParams.setLimit(1);
    final PaginatedResults<SelfDescriptionMetadata> byFilter = sdStorePublisher.getByFilter(filterParams, true, false);
    final int matchCount = byFilter.getResults().size();

    log.info("filter returned {} match(es)", matchCount);
    assertEquals(1, matchCount, "expected 2 filter match, but got " + matchCount);
    assertEquals(3, byFilter.getTotalCount(), "expected 3 total count, but got " + matchCount);
    sdStorePublisher.deleteSelfDescription(hash1);
    sdStorePublisher.deleteSelfDescription(hash2);
    sdStorePublisher.deleteSelfDescription(hash3);

    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash1);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash2);
    });
    Assertions.assertThrows(NotFoundException.class, () -> {
      sdStorePublisher.getByHash(hash3);
    });
    log.info("#### Test 13 succeeded.");
  }

}
