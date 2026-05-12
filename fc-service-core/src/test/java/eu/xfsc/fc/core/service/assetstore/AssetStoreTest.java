package eu.xfsc.fc.core.service.assetstore;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.config.RdfContentTypeProperties;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.service.graphdb.DummyGraphStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.provenance.ProvenanceService;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import eu.xfsc.fc.core.util.HashUtils;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static eu.xfsc.fc.core.util.TestUtil.assertThatAssetHasTheSameData;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {
        AssetAuditRepository.class,
        AssetJpaDao.class,
        AssetStoreImpl.class,
        AssetStoreTest.TestApplication.class,
        AssetStoreTest.class,
        DatabaseConfig.class,
        DidDocumentResolver.class,
        DidResolverConfig.class,
        DocumentLoaderConfig.class,
        DocumentLoaderProperties.class,
        DummyGraphStore.class,
        FileStoreConfig.class,
        HttpDocumentResolver.class,
        IriGenerator.class,
        IriValidator.class,
        JwtSignatureVerifier.class,
        ProtectedNamespaceProperties.class,
        RdfContentTypeProperties.class,
        SecurityAuditorAware.class
})
@Slf4j
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class AssetStoreTest {

    @SpringBootApplication
    public static class TestApplication {

        public static void main(final String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @MockitoBean
    private ProvenanceService provenanceService;

    @MockitoBean
    private ValidationResultStore validationResultStore;

    @Autowired
    private AssetStore assetStorePublisher;

    @Autowired
    private GraphStore graphStore;

    @AfterEach
    public void storageSelfCleaning() throws IOException {
        assetStorePublisher.clear();
    }

    private static AssetMetadata createAssetMetadata(final String id, final String issuer,
                                                     final Instant sdt, final Instant udt, final String content) {
        final String hash = HashUtils.calculateSha256AsHex(content);
        AssetMetadata assetMeta = new AssetMetadata();
        assetMeta.setId(id);
        assetMeta.setIssuer(issuer);
        assetMeta.setAssetHash(hash);
        assetMeta.setStatus(AssetStatus.ACTIVE);
        assetMeta.setStatusDatetime(sdt);
        assetMeta.setUploadDatetime(udt);
        assetMeta.setContentAccessor(new ContentAccessorDirect(content));
        return assetMeta;
    }

    private static List<RdfClaim> createClaims(String subject) {
        final RdfClaim claim1 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#providedBy>", "<https://delta-dao.com/.well-known/participant.json>");
        final RdfClaim claim2 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#name>", "\"EuProGigant Portal\"");
        final RdfClaim claim3 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#description>", "\"EuProGigant Minimal Viable Gaia-X Portal\"");
        final RdfClaim claim4 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#TermsAndConditions>", "<https://euprogigant.com/en/terms/>");
        final RdfClaim claim5 = new CredentialClaim(subject, "<https://w3id.org/gaia-x/2511#TermsAndConditions>", "\"contentHash\"");
        return List.of(claim1, claim2, claim3, claim4, claim5);
    }

    private static CredentialVerificationResult createVerificationResult(final int idSuffix, String subject) {
      return new CredentialVerificationResult(Instant.now(), AssetStatus.ACTIVE.getValue(), "issuer" + idSuffix,
          Instant.now(),
            "id" + idSuffix, createClaims(subject), new ArrayList<>(), "", "");
    }

    private static CredentialVerificationResult createVerificationResult(final int idSuffix) {
        return createVerificationResult(idSuffix, "<https://delta-dao.com/.well-known/serviceMVGPortal.json>");
    }

    private static CredentialVerificationResult createVerificationResult(AssetMetadata assetMeta) {
        List<Validator> vals = null;
        if (assetMeta.getValidatorDids() != null) {
            vals = new ArrayList<>(assetMeta.getValidatorDids().size());
            for (String did : assetMeta.getValidatorDids()) {
                vals.add(new Validator(did, "PK", Instant.now().plusSeconds(3600)));
            }
        }
      return new CredentialVerificationResult(assetMeta.getStatusDatetime(), AssetStatus.ACTIVE.getValue(),
          assetMeta.getIssuer(), assetMeta.getUploadDatetime(),
            assetMeta.getId(), createClaims("<https://delta-dao.com/.well-known/serviceMVGPortal.json>"), vals, "", "");
    }

    /**
     * Test storing an asset, and deprecating it by storing a second asset with the same subjectId.
     */
    @Test
    void storeCredential_withDuplicateSubjectId_replacesExistingAsset() {
        final String content1 = "Some Test Content 1";
        final String content2 = "Some Test Content 2";

        final AssetMetadata assetMeta1 = createAssetMetadata("TestAsset/1", "TestUser/1",
                Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content1);
        final String hash1 = assetMeta1.getAssetHash();
        assetMeta1.setContentAccessor(new ContentAccessorDirect(content1));
        assetStorePublisher.storeCredential(assetMeta1, createVerificationResult(assetMeta1));

        final AssetMetadata assetMeta2 = createAssetMetadata("TestAsset/1", "TestUser/1",
                Instant.parse("2022-01-01T13:00:00Z"), Instant.parse("2022-01-02T13:00:00Z"), content2);
        final String hash2 = assetMeta2.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta2, createVerificationResult(assetMeta2));

        // In-place update: hash1 row is gone (updated to hash2 in-place); hash2 row is the current ACTIVE asset
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash1),
                "Old hash should not exist after in-place update.");
        assertThatAssetHasTheSameData(assetMeta2, assetStorePublisher.getByHash(hash2), true);
        assertEquals(AssetStatus.ACTIVE, assetStorePublisher.getByHash(hash2).getStatus());

        assetStorePublisher.deleteAsset(hash2);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash2));
    }

    //@Test
    void storeCredential_withDuplicateContent_throwsConflictException() {
        final String content1 = "Some Test Content";

        final AssetMetadata assetMeta1 = createAssetMetadata("TestAsset/1", "TestUser/1",
                Instant.parse("2022-01-01T12:00:00Z"), Instant.parse("2022-01-02T12:00:00Z"), content1);
        final String hash1 = assetMeta1.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta1, createVerificationResult(assetMeta1));

        List<Map<String, Object>> nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
                Map.of("graphUri", "TestAsset/1")
        )).getResults();
        Assertions.assertEquals(3, nodes.size());

        final AssetMetadata assetMeta2 = createAssetMetadata("TestAsset/1", "TestUser/1",
                Instant.parse("2022-01-01T13:00:00Z"), Instant.parse("2022-01-02T13:00:00Z"), content1);
        assertThrows(ConflictException.class, () -> assetStorePublisher.storeCredential(assetMeta2, createVerificationResult(assetMeta2)));

        nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
                Map.of("graphUri", "TestAsset/1")
        )).getResults();
        Assertions.assertEquals(3, nodes.size(), "After failed put, node count should not have changed");

        final AssetMetadata byHash1 = assetStorePublisher.getByHash(hash1);
        final AssetStatus status1 = byHash1.getStatus();
        assertEquals(AssetStatus.ACTIVE, status1, "First asset should stay active.");

        assetStorePublisher.deleteAsset(hash1);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash1));
    }

    /**
     * Test storing an asset, and updating the status.
     */
    //@Test
    void updateAssetStatus_withValidTransition_changesStatus() throws Exception {
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
        Assertions.assertEquals(3, nodes.size());

        assetStorePublisher.changeLifeCycleStatus(hash, AssetStatus.REVOKED);
        byHash = assetStorePublisher.getByHash(hash);
        assertEquals(AssetStatus.REVOKED, byHash.getStatus(), "Status should have been changed to 'revoked'");

        assertThrows(ConflictException.class, () -> assetStorePublisher.changeLifeCycleStatus(hash, AssetStatus.ACTIVE));
        byHash = assetStorePublisher.getByHash(hash);
        assertEquals(AssetStatus.REVOKED, byHash.getStatus(),
                "Status should not have been changed from 'revoked' to 'active'.");

        nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
                Map.of("graphUri", "TestAsset/1")
        )).getResults();
        Assertions.assertEquals(0, nodes.size(), "Revoked asset should not appear in queries");

        assertThrows(ConflictException.class, () -> {
            assetStorePublisher.storeCredential(assetMeta, createVerificationResult(0));
        }, "Adding the same asset after revocation should not be possible.");

        nodes = graphStore.queryData(new GraphQuery(
                "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
                Map.of("graphUri", "TestAsset/1")
        )).getResults();
        Assertions.assertEquals(0, nodes.size(), "Revoked asset should not appear in queries");

        assetStorePublisher.deleteAsset(hash);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
    }

    /**
     * Test applying an asset filter on matching issuer.
     */
    @Test
    void filter_withMatchingIssuer_returnsMatchingAsset() {
        final String id = "TestAsset/1";
        final String issuer = "TestUser/1";
        final String content = "Test: Fetch asset metadata via asset filter, test for matching issuer";
        final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
        final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
        final AssetMetadata assetMeta = createAssetMetadata(id, issuer, statusTime, uploadTime, content);
        final String hash = assetMeta.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta, createVerificationResult(assetMeta));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setIssuers(List.of(issuer, "TestUser/21"));
        PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, true);
        int matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
        assertEquals(assetMeta.getId(), byFilter.getResults().getFirst().getId());
        AssetMetadata firstResult = byFilter.getResults().getFirst();
        assertEquals(assetMeta.getAssetHash(), firstResult.getAssetHash(), "Incorrect Hash");
        assertEquals(assetMeta.getId(), firstResult.getId(), "Incorrect SubjectId");
        assertEquals(assetMeta.getContentAccessor(), firstResult.getContentAccessor(), "Incorrect asset content");

        byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
        firstResult = byFilter.getResults().getFirst();
        assertEquals(assetMeta.getAssetHash(), firstResult.getAssetHash(), "Incorrect Hash");
        assertEquals(assetMeta.getId(), firstResult.getId(), "Incorrect SubjectId");
        Assertions.assertNull(firstResult.getContentAccessor(), "Asset content should not have been returned.");

        byFilter = assetStorePublisher.getByFilter(filterParams, false, true);
        matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
        firstResult = byFilter.getResults().getFirst();
        assertEquals(assetMeta.getAssetHash(), firstResult.getAssetHash(), "Incorrect Hash");
        Assertions.assertNull(firstResult.getId(), "SubjectId should not have been returned");
        assertEquals(assetMeta.getContentAccessor(), firstResult.getContentAccessor(), "Incorrect asset content");

        byFilter = assetStorePublisher.getByFilter(filterParams, false, false);
        matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
        firstResult = byFilter.getResults().getFirst();
        assertEquals(assetMeta.getAssetHash(), firstResult.getAssetHash(), "Incorrect Hash");
        Assertions.assertNull(firstResult.getId(), "SubjectId should not have been returned");
        Assertions.assertNull(firstResult.getContentAccessor(), "Asset content should not have been returned.");

        assetStorePublisher.deleteAsset(hash);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
    }

    /**
     * Test applying an asset filter on non-matching issuer.
     */
    @Test
    void filter_withNonMatchingIssuer_returnsEmpty() {
        final String id = "TestAsset/1";
        final String issuer = "TestUser/1";
        final String otherIssuer = "TestUser/2";
        final String content = "Test: Fetch asset metadata via asset filter, test for non-matching issuer";
        final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
        final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
        final AssetMetadata assetMeta = createAssetMetadata(id, issuer, statusTime, uploadTime, content);
        final String hash = assetMeta.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta, createVerificationResult(assetMeta));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setIssuers(List.of(otherIssuer));
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final int matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(0, matchCount, "expected 0 filter matches, but got " + matchCount);

        assetStorePublisher.deleteAsset(hash);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
    }

    /**
     * Test applying an asset filter on matching status start time.
     */
    @Test
    void filter_withMatchingStatusStartTime_returnsMatchingAsset() {
        final String id = "TestAsset/1";
        final String issuer = "TestUser/1";
        final String content = "Test: Fetch asset metadata via asset filter, test for matching status time start";
        final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
        final Instant statusTimeStart = Instant.parse("2021-01-01T12:00:00Z");
        final Instant statusTimeEnd = Instant.parse("2022-01-01T12:00:00Z");
        final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
        final AssetMetadata assetMeta = createAssetMetadata(id, issuer, statusTime, uploadTime, content);
        final String hash = assetMeta.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta, createVerificationResult(assetMeta));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setStatusTimeRange(statusTimeStart, statusTimeEnd);
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final int matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(1, matchCount, "expected 1 filter match, but got " + matchCount);
        assertEquals(assetMeta.getId(), byFilter.getResults().getFirst().getId());

        assetStorePublisher.deleteAsset(hash);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
    }

    /**
     * Test applying an asset filter on non-matching issuer.
     */
    @Test
    void filter_withNonMatchingStatusStartTime_returnsEmpty() {
        final String id = "TestAsset/1";
        final String issuer = "TestUser/1";
        final String content = "Test: Fetch asset metadata via asset filter, test for non-matching issuer";
        final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
        final Instant statusTimeStart = Instant.parse("2022-02-01T12:00:00Z");
        final Instant statusTimeEnd = Instant.parse("2023-02-01T12:00:00Z");
        final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
        final AssetMetadata assetMeta = createAssetMetadata(id, issuer, statusTime, uploadTime, content);
        final String hash = assetMeta.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta, createVerificationResult(assetMeta));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setStatusTimeRange(statusTimeStart, statusTimeEnd);
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final int matchCount = byFilter.getResults().size();
        assertEquals(0, matchCount, "expected 0 filter matches, but got " + matchCount);

        assetStorePublisher.deleteAsset(hash);
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));

    }

    /**
     * Test applying an asset filter that matches multiple records.
     */
    @Test
    void filter_withMultipleAssets_returnsAllMatching() {
        final String id1 = "TestAsset/1";
        final String id2 = "TestAsset/2";
        final String id3 = "TestAsset/3";
        final String issuer1 = "TestUser/1";
        final String issuer2 = "TestUser/2";
        final String issuer3 = "TestUser/3";
        final String content1 = "Test: Fetch asset metadata via asset filter, test for matching status time start (1/3)";
        final String content2 = "Test: Fetch asset metadata via asset filter, test for matching status time start (2/3)";
        final String content3 = "Test: Fetch asset metadata via asset filter, test for matching status time start (3/3)";
        final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
        final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
        final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
        final Instant statusTimeStart = Instant.parse("2022-01-01T12:00:00Z");
        final Instant statusTimeEnd = Instant.parse("2022-01-02T12:00:00Z");
        final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
        final AssetMetadata assetMeta1 = createAssetMetadata(id1, issuer1, statusTime1, uploadTime1, content1);
        final AssetMetadata assetMeta2 = createAssetMetadata(id2, issuer2, statusTime2, uploadTime2, content2);
        final AssetMetadata assetMeta3 = createAssetMetadata(id3, issuer3, statusTime3, uploadTime3, content3);
        final String hash1 = assetMeta1.getAssetHash();
        final String hash2 = assetMeta2.getAssetHash();
        final String hash3 = assetMeta3.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta1, createVerificationResult(assetMeta1));
        assetStorePublisher.storeCredential(assetMeta2, createVerificationResult(assetMeta2));
        assetStorePublisher.storeCredential(assetMeta3, createVerificationResult(assetMeta3));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setStatusTimeRange(statusTimeStart, statusTimeEnd);
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final int matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(2, matchCount, "expected 2 filter match, but got " + matchCount);
        final AssetMetadata filterAssetMeta1 = byFilter.getResults().getFirst();
        final AssetMetadata filterAssetMeta2 = byFilter.getResults().get(1);
        assertTrue(assetMeta1.getId().equals(filterAssetMeta1.getId()) || assetMeta1.getId().equals(filterAssetMeta2.getId()), "expected filter match assetMeta1 missing in results");
        assertTrue(assetMeta2.getId().equals(filterAssetMeta1.getId()) || assetMeta2.getId().equals(filterAssetMeta2.getId()), "expected filter match assetMeta2 missing in results");

        assetStorePublisher.deleteAsset(hash1);
        assetStorePublisher.deleteAsset(hash2);
        assetStorePublisher.deleteAsset(hash3);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash1));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash2));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash3));
    }

    /**
     * Test applying an empty asset filter for matching all records.
     */
    @Test
    void filter_withEmptyFilter_returnsAllAssets() {
        final String id1 = "TestAsset/1";
        final String id2 = "TestAsset/2";
        final String id3 = "TestAsset/3";
        final String issuer1 = "TestUser/1";
        final String issuer2 = "TestUser/2";
        final String issuer3 = "TestUser/3";
        final String content1 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (1/3)";
        final String content2 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (2/3)";
        final String content3 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (3/3)";
        final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
        final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
        final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
        final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
        final AssetMetadata assetMeta1 = createAssetMetadata(id1, issuer1, statusTime1, uploadTime1, content1);
        final AssetMetadata assetMeta2 = createAssetMetadata(id2, issuer2, statusTime2, uploadTime2, content2);
        final AssetMetadata assetMeta3 = createAssetMetadata(id3, issuer3, statusTime3, uploadTime3, content3);
        final String hash1 = assetMeta1.getAssetHash();
        final String hash2 = assetMeta2.getAssetHash();
        final String hash3 = assetMeta3.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta1, createVerificationResult(assetMeta1));
        assetStorePublisher.storeCredential(assetMeta2, createVerificationResult(assetMeta2));
        assetStorePublisher.storeCredential(assetMeta3, createVerificationResult(assetMeta3));

        final AssetFilter filterParams = new AssetFilter();
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final int matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(3, matchCount, "expected 3 filter match, but got " + matchCount);
        final AssetMetadata filterAssetMeta1 = byFilter.getResults().getFirst();
        final AssetMetadata filterAssetMeta2 = byFilter.getResults().get(1);
        final AssetMetadata filterAssetMeta3 = byFilter.getResults().get(2);
        assertTrue(assetMeta1.getId().equals(filterAssetMeta1.getId()) || assetMeta1.getId().equals(filterAssetMeta2.getId())
                || assetMeta1.getId().equals(filterAssetMeta3.getId()), "expected filter match assetMeta1 missing in results");
        assertTrue(assetMeta2.getId().equals(filterAssetMeta1.getId()) || assetMeta2.getId().equals(filterAssetMeta2.getId())
                || assetMeta2.getId().equals(filterAssetMeta3.getId()), "expected filter match assetMeta2 missing in results");
        assertTrue(assetMeta3.getId().equals(filterAssetMeta1.getId()) || assetMeta3.getId().equals(filterAssetMeta2.getId())
                || assetMeta3.getId().equals(filterAssetMeta3.getId()), "expected filter match assetMeta3 missing in results");
        assetStorePublisher.deleteAsset(hash1);
        assetStorePublisher.deleteAsset(hash2);
        assetStorePublisher.deleteAsset(hash3);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash1));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash2));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash3));
    }

    /**
     * Test applying an asset filter on non-matching validator.
     */
    @Test
    void filter_withMatchingValidator_returnsMatchingAsset() {
        final String id = "TestAsset/1";
        final String validatorId = "TestAsset/0815";
        final String issuer = "TestUser/1";
        final String content = "Test: Fetch asset metadata via asset filter, test for matching validator";
        final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
        final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
        final AssetMetadata assetMeta = createAssetMetadata(id, issuer, statusTime, uploadTime, content);
        assetMeta.setValidatorDids(Arrays.asList(validatorId, "TestAsset/0816", "TestAsset/0817"));
        final String hash = assetMeta.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta, createVerificationResult(assetMeta));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setValidators(List.of(validatorId, "TestAsset/0820"));
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final long matchCount = byFilter.getTotalCount();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(1, matchCount, "expected 1 filter matches");

        assetStorePublisher.deleteAsset(hash);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
    }

    /**
     * Test applying an asset filter on non-matching validator.
     */
    @Test
    void filter_withNonMatchingValidator_returnsEmpty() {
        final String id = "TestAsset/1";
        final String validatorId = "TestAsset/0815";
        final String issuer = "TestUser/1";
        final String content = "Test: Fetch asset metadata via asset filter, test for non-matching validator";
        final Instant statusTime = Instant.parse("2022-01-01T12:00:00Z");
        final Instant uploadTime = Instant.parse("2022-01-02T12:00:00Z");
        final AssetMetadata assetMeta = createAssetMetadata(id, issuer, statusTime, uploadTime, content);
        assetMeta.setValidatorDids(Arrays.asList("TestAsset/0816", "TestAsset/0817"));
        final String hash = assetMeta.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta, createVerificationResult(assetMeta));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setValidators(List.of(validatorId));
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final int matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(0, matchCount, "expected 0 filter matches");

        assetStorePublisher.deleteAsset(hash);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
    }

    /**
     * Test applying an asset filter with limited number of results.
     */
    @Test
    void filter_withLimitParameter_returnsLimitedResults() {
        final String id1 = "TestAsset/1";
        final String id2 = "TestAsset/2";
        final String id3 = "TestAsset/3";
        final String issuer1 = "TestUser/1";
        final String issuer2 = "TestUser/2";
        final String issuer3 = "TestUser/3";
        final String content1 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (1/3)";
        final String content2 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (2/3)";
        final String content3 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (3/3)";
        final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
        final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
        final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
        final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
        final AssetMetadata assetMeta1 = createAssetMetadata(id1, issuer1, statusTime1, uploadTime1, content1);
        final AssetMetadata assetMeta2 = createAssetMetadata(id2, issuer2, statusTime2, uploadTime2, content2);
        final AssetMetadata assetMeta3 = createAssetMetadata(id3, issuer3, statusTime3, uploadTime3, content3);
        final String hash1 = assetMeta1.getAssetHash();
        final String hash2 = assetMeta2.getAssetHash();
        final String hash3 = assetMeta3.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta1, createVerificationResult(assetMeta1));
        assetStorePublisher.storeCredential(assetMeta2, createVerificationResult(assetMeta2));
        assetStorePublisher.storeCredential(assetMeta3, createVerificationResult(assetMeta3));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setLimit(2);
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final int matchCount = byFilter.getResults().size();
        log.info("filter returned {} match(es)", matchCount);
        assertEquals(2, matchCount, "expected 2 filter match, but got " + matchCount);
        assetStorePublisher.deleteAsset(hash1);
        assetStorePublisher.deleteAsset(hash2);
        assetStorePublisher.deleteAsset(hash3);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash1));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash2));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash3));
    }

    private CredentialVerificationResult verifyAssetCredential(String id, Instant firstSigInstant) {
        List<Validator> signatures = new ArrayList<>();
        signatures.add(new Validator("did:first", "", firstSigInstant));
        signatures.add(new Validator("did:second", "", Instant.now().plus(1, ChronoUnit.DAYS)));
        signatures.add(new Validator("did:third", "", Instant.now().plus(2, ChronoUnit.DAYS)));
        return new CredentialVerificationResult(Instant.now(), AssetStatus.ACTIVE.getValue(), "issuer", Instant.now(),
            id, new ArrayList<>(), signatures, "", "");
    }

    @Test
    void validateSignatures_periodicExecution_revalidatesExpiredAssets() throws IOException {
        final String id1 = "TestAsset/1";
        final String id2 = "TestAsset/2";
        final String id3 = "TestAsset/3";
        final String issuer1 = "TestUser/1";
        final String issuer2 = "TestUser/2";
        final String issuer3 = "TestUser/3";
        final String content1 = "Test: Asset 1 with future expiration date";
        final String content2 = "Test: Asset 2 with past expiration date";
        final String content3 = "Test: Asset 3 with future expiration date";
        final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
        final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
        final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
        final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
        final AssetMetadata assetMeta1 = createAssetMetadata(id1, issuer1, statusTime1, uploadTime1, content1);
        final AssetMetadata assetMeta2 = createAssetMetadata(id2, issuer2, statusTime2, uploadTime2, content2);
        final AssetMetadata assetMeta3 = createAssetMetadata(id3, issuer3, statusTime3, uploadTime3, content3);
        final String hash1 = assetMeta1.getAssetHash();
        final String hash2 = assetMeta2.getAssetHash();
        final String hash3 = assetMeta3.getAssetHash();
        final CredentialVerificationResult vr1 = verifyAssetCredential(id1, Instant.now().plus(1, ChronoUnit.DAYS));
        final CredentialVerificationResult vr2 = verifyAssetCredential(id2, Instant.now().minus(1, ChronoUnit.DAYS));
        final CredentialVerificationResult vr3 = verifyAssetCredential(id3, Instant.now().plus(1, ChronoUnit.DAYS));
        assetStorePublisher.storeCredential(assetMeta1, vr1);
        assetStorePublisher.storeCredential(assetMeta2, vr2);
        assetStorePublisher.storeCredential(assetMeta3, vr3);

        final int expiredAssetsCount = assetStorePublisher.invalidateExpiredAssets();
        assertEquals(1, expiredAssetsCount, "expected 1 expired asset");
        assertEquals(AssetStatus.ACTIVE, assetStorePublisher.getByHash(hash1).getStatus(), "Status should not have been changed.");
        assertEquals(AssetStatus.EOL, assetStorePublisher.getByHash(hash2).getStatus(), "Status should have been changed.");
        assertEquals(AssetStatus.ACTIVE, assetStorePublisher.getByHash(hash3).getStatus(), "Status should not have been changed.");

        assetStorePublisher.deleteAsset(hash1);
        assetStorePublisher.deleteAsset(hash2);
        assetStorePublisher.deleteAsset(hash3);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash1));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash2));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash3));
    }

    /**
     * Test applying an asset filter with limited number of results with total asset count number.
     */
    @Test
    void filter_withLimitAndTotalCount_returnsCorrectCounts() {
        final String id1 = "TestAsset/1";
        final String id2 = "TestAsset/2";
        final String id3 = "TestAsset/3";
        final String issuer1 = "TestUser/1";
        final String issuer2 = "TestUser/2";
        final String issuer3 = "TestUser/3";
        final String content1 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (1/3)";
        final String content2 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (2/3)";
        final String content3 = "Test: Fetch asset metadata via asset filter, test for matching empty filter (3/3)";
        final Instant statusTime1 = Instant.parse("2022-01-01T12:00:00Z");
        final Instant statusTime2 = Instant.parse("2022-01-02T12:00:00Z");
        final Instant statusTime3 = Instant.parse("2022-01-03T12:00:00Z");
        final Instant uploadTime1 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime2 = Instant.parse("2022-02-01T12:00:00Z");
        final Instant uploadTime3 = Instant.parse("2022-02-01T12:00:00Z");
        final AssetMetadata assetMeta1 = createAssetMetadata(id1, issuer1, statusTime1, uploadTime1, content1);
        final AssetMetadata assetMeta2 = createAssetMetadata(id2, issuer2, statusTime2, uploadTime2, content2);
        final AssetMetadata assetMeta3 = createAssetMetadata(id3, issuer3, statusTime3, uploadTime3, content3);
        final String hash1 = assetMeta1.getAssetHash();
        final String hash2 = assetMeta2.getAssetHash();
        final String hash3 = assetMeta3.getAssetHash();
        assetStorePublisher.storeCredential(assetMeta1, createVerificationResult(1));
        assetStorePublisher.storeCredential(assetMeta2, createVerificationResult(2));
        assetStorePublisher.storeCredential(assetMeta3, createVerificationResult(3));

        final AssetFilter filterParams = new AssetFilter();
        filterParams.setLimit(1);
        final PaginatedResults<AssetMetadata> byFilter = assetStorePublisher.getByFilter(filterParams, true, false);
        final int matchCount = byFilter.getResults().size();

        log.info("filter returned {} match(es)", matchCount);
        assertEquals(1, matchCount, "expected 2 filter match, but got " + matchCount);
        assertEquals(3, byFilter.getTotalCount(), "expected 3 total count, but got " + matchCount);
        assetStorePublisher.deleteAsset(hash1);
        assetStorePublisher.deleteAsset(hash2);
        assetStorePublisher.deleteAsset(hash3);

        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash1));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash2));
        assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash3));
    }

}
