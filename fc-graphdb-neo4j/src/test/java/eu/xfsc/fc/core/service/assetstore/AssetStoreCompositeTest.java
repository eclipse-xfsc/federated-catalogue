package eu.xfsc.fc.core.service.assetstore;

import static eu.xfsc.fc.core.util.TestUtil.assertThatAssetHasTheSameData;
import static eu.xfsc.fc.core.util.TestUtil.getAccessor;

import java.util.List;
import java.util.Map;

import eu.xfsc.fc.core.config.RdfContentTypeProperties;
import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;
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
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.NonCredentialVerificationResult;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.CredentialVerificationStrategy;
import eu.xfsc.fc.core.service.verification.DanubeTechFormatMatcher;
import eu.xfsc.fc.core.service.verification.FormatDetector;
import eu.xfsc.fc.core.service.verification.JwtContentPreprocessor;
import eu.xfsc.fc.core.service.verification.LoireJwtParser;
import eu.xfsc.fc.core.service.verification.SchemaModuleConfigService;
import eu.xfsc.fc.core.service.verification.SchemaValidationServiceImpl;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.Vc2Processor;
import eu.xfsc.fc.core.service.verification.VerificationServiceImpl;
import eu.xfsc.fc.core.service.verification.claims.JenaAllTriplesExtractor;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import eu.xfsc.fc.core.service.validation.ValidationResultGraphWriter;
import eu.xfsc.fc.core.service.validation.ValidationResultHasher;
import eu.xfsc.fc.core.service.validation.ValidationResultStoreImpl;
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
@ContextConfiguration(classes = {
        AdminConfigRepository.class,
        AssetAuditRepository.class,
        AssetJpaDao.class,
        AssetStoreCompositeTest.TestApplication.class,
        AssetStoreCompositeTest.class,
        AssetStoreImpl.class,
        ClaimExtractionService.class,
        CredentialVerificationStrategy.class,
        DanubeTechFormatMatcher.class,
        DatabaseConfig.class,
        DidDocumentResolver.class,
        DidResolverConfig.class,
        DocumentLoaderConfig.class,
        DocumentLoaderProperties.class,
        FileStoreConfig.class,
        FormatDetector.class,
        GraphRebuilder.class,
        HttpDocumentResolver.class,
        IriGenerator.class,
        IriValidator.class,
        JenaAllTriplesExtractor.class,
        JwtContentPreprocessor.class,
        JwtSignatureVerifier.class,
        LoireJwtParser.class,
        Neo4jGraphStore.class,
        ProtectedNamespaceFilter.class,
        ProtectedNamespaceProperties.class,
        RdfContentTypeProperties.class,
        SchemaAuditRepository.class,
        SchemaJpaDao.class,
        SchemaModuleConfigService.class,
        SchemaStoreImpl.class,
        SchemaValidationServiceImpl.class,
        SecurityAuditorAware.class,
        ValidatorCacheJpaDao.class,
        ValidationResultGraphWriter.class,
        ValidationResultHasher.class,
        ValidationResultStoreImpl.class,
        Vc2Processor.class,
        VerificationServiceImpl.class
})
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
    private VerificationServiceImpl verificationService;

    @Autowired
    private ClaimExtractionService claimExtractionService;

    @Autowired
    private AssetStore assetStorePublisher;

    @Autowired
    private SchemaStoreImpl schemaStore;

    @Autowired
    private Neo4j embeddedDatabaseServer;

    @Autowired
    private GraphStore graphStore;

    @Autowired
    private GraphRebuilder graphRebuilder;

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

        schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
        ContentAccessor content = getAccessor("Claims-Extraction-Tests/providerTest.jsonld");
        // Only verify semantics, not schema or signatures
        CredentialVerificationResult result = verificationService.verifyCredential(content, true, false, false, false);
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

        assetStorePublisher.deleteAsset(hash);

        claims = graphStore.queryData(
                new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", uri))).getResults();
        Assertions.assertEquals(0, claims.size());

        Assertions.assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(hash));
    }

    @Test
    void test02RebuildGraphDb() {

        schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
        ContentAccessor content = getAccessor("Claims-Extraction-Tests/providerTest.jsonld");
        // Only verify semantics, not schema or signatures
        CredentialVerificationResult result = verificationService.verifyCredential(content, true, false, false, false);
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

        graphRebuilder.rebuildGraphDb(1, 0, 1, 1);

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

        schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
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

        graphRebuilder.rebuildGraphDb(1, 0, 1, 1);

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

    @Test
    void test04RebuildGraphDb_nonCredentialNTriples_restoresClaimsAfterRebuild() {

        // Arrange — minimal N-Triples document, deliberately not a VC/VP
        final String subjectUri = "http://example.org/non-credential-test/subject1";
        final String nTriples = "<" + subjectUri + "> "
                + "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type> "
                + "<http://example.org/non-credential-test/Resource> .";
        ContentAccessorDirect content = new ContentAccessorDirect(nTriples, VerificationConstants.MEDIA_TYPE_NTRIPLES);

        CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);
        Assertions.assertInstanceOf(NonCredentialVerificationResult.class, result,
                "N-Triples content without VC/VP structure must produce a NonCredentialVerificationResult");
        Assertions.assertFalse(result.getClaims().isEmpty(),
                "N-Triples content must yield at least one extracted claim");

        AssetMetadata assetMeta = new AssetMetadata(subjectUri, null, null, content);
        assetMeta.setContentType(VerificationConstants.MEDIA_TYPE_NTRIPLES);
        assetStorePublisher.storeCredential(assetMeta, result);

        // Verify initial graph storage
        List<Map<String, Object>> nodes = graphStore.queryData(
                new GraphQuery("MATCH (n {uri: $uri}) RETURN n", Map.of("uri", subjectUri))).getResults();
        Assertions.assertFalse(nodes.isEmpty(), "Graph must contain subject node after initial store");

        // Delete claims — simulates the scenario that triggers a graph rebuild
        graphStore.deleteClaims(assetMeta.getId());

        nodes = graphStore.queryData(
                new GraphQuery("MATCH (n {uri: $uri}) RETURN n", Map.of("uri", subjectUri))).getResults();
        Assertions.assertTrue(nodes.isEmpty(), "Graph must be empty after deleting claims");

        // Rebuild
        graphRebuilder.rebuildGraphDb(1, 0, 1, 10);

        // Assert — FAILS with current code: addAssetToGraph() calls extractCredentialClaims()
        // which returns empty for N-Triples content, leaving the graph empty after rebuild
        nodes = graphStore.queryData(
                new GraphQuery("MATCH (n {uri: $uri}) RETURN n", Map.of("uri", subjectUri))).getResults();
        Assertions.assertFalse(nodes.isEmpty(),
                "Graph must contain non-credential triples after rebuild");

        assetStorePublisher.deleteAsset(assetMeta.getAssetHash());
    }
}
