package eu.xfsc.fc.core.service.pubsub;

import eu.xfsc.fc.client.ExternalServiceException;
import eu.xfsc.fc.core.config.AssetStoreConfig;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.JacksonConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.config.PubSubConfig;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.cestracker.CesTrackerJpaDao;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.DummyGraphStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.assetstore.IriGenerator;
import eu.xfsc.fc.core.service.assetstore.IriValidator;
import eu.xfsc.fc.core.service.verification.CredentialVerificationStrategy;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import eu.xfsc.fc.core.service.verification.JwtContentPreprocessor;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.SchemaValidationServiceImpl;
import eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass;
import eu.xfsc.fc.core.service.verification.Vc11Processor;
import eu.xfsc.fc.core.service.verification.Vc2Processor;
import eu.xfsc.fc.core.service.verification.VerificationServiceImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest(properties = {"publisher.impl=ces", "publisher.url=http://localhost:9091", "publisher.comp-url=http://localhost:9090"})
@ActiveProfiles({"test"})
@ContextConfiguration(classes = {CesCompositePublisherTest.TestApplication.class, PubSubConfig.class, JacksonConfig.class, DatabaseConfig.class, AssetStoreConfig.class, AssetJpaDao.class,
		DummyGraphStore.class, VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaJpaDao.class, FileStoreConfig.class, DidResolverConfig.class, DidDocumentResolver.class, HttpDocumentResolver.class,
		DocumentLoaderConfig.class, DocumentLoaderProperties.class, ValidatorCacheJpaDao.class, CesTrackerJpaDao.class, CredentialVerificationStrategy.class, SchemaValidationServiceImpl.class, ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class,
		JwtContentPreprocessor.class, Vc11Processor.class, Vc2Processor.class, JwtSignatureVerifier.class,
		IriGenerator.class, IriValidator.class})
//@Import(EmbeddedNeo4JConfig.class)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
public class CesCompositePublisherTest {

    @SpringBootApplication
    public static class TestApplication {

        public static void main(final String[] args) {
            SpringApplication.run(TestApplication.class, args);
        }
    }

    @Autowired
    private AssetPublisher cesPublisher;
    //@Autowired
    //private Neo4j embeddedDatabaseServer;
    @Autowired
    private GraphStore graphStore;
    @Autowired
    private AssetStore assetStorePublisher;
    @Autowired
    private VerificationServiceImpl verificationService;
    @Autowired
    private SchemaStoreImpl schemaStore;

    private MockWebServer mockCesService;
    private MockWebServer mockCompService;

    @BeforeAll
    public void setup() throws Exception {
        mockCompService = new MockWebServer();
        mockCompService.noClientAuth();
        mockCompService.start(9090);
        mockCesService = new MockWebServer();
        mockCesService.noClientAuth();
        mockCesService.start(9091);
    }

    @AfterAll
    void cleanUpStores() throws Exception {
        mockCompService.shutdown();
        mockCesService.shutdown();
        //embeddedDatabaseServer.close();
    }

    @AfterEach
    public void storageSelfCleaning() throws IOException {
        schemaStore.clear();
    }


    @Test
    public void test01AssetStoreRollback() {
        //ContentAccessor content = getAccessor("Pub-Sub-Tests/sag-research.jsonld");
        cesPublisher.setTransactional(true);
        ContentAccessor content = getAccessor("VerificationService/syntax/legalPerson2.jsonld");
        schemaStore.initializeDefaultSchemas();
        verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "https://w3id.org/gaia-x/core#Participant");
        CredentialVerificationResult vr = verificationService.verifyCredential(content, true, true, false, false);
        verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "http://w3id.org/gaia-x/participant#Participant");
        assertNotNull(vr);
        AssetMetadata assetMetadata = new AssetMetadata(content, vr);
        mockCompService.enqueue(new MockResponse()
                .setBody("{\"error\": \"Conflict\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(409));
        ExternalServiceException ex = assertThrowsExactly(ExternalServiceException.class, () -> assetStorePublisher.storeCredential(assetMetadata, vr));
        assertEquals(HttpStatusCode.valueOf(409), ex.getStatus());
        assertNotNull(assetMetadata.getId());
        List<Map<String, Object>> claims = graphStore.queryData(
                new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", assetMetadata.getId()))).getResults();
        Assertions.assertEquals(0, claims.size());
        Assertions.assertThrows(NotFoundException.class, () -> assetStorePublisher.getByHash(assetMetadata.getAssetHash()));
    }

    @Test
    public void test02AssetStoreCommit() {
        //ContentAccessor content = getAccessor("Pub-Sub-Tests/sag-research.jsonld");
        cesPublisher.setTransactional(false);
        ContentAccessor content = getAccessor("VerificationService/syntax/legalPerson2.jsonld");
        schemaStore.initializeDefaultSchemas();
        verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "https://w3id.org/gaia-x/core#Participant");
        CredentialVerificationResult vr = verificationService.verifyCredential(content, true, true, false, false);
        verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "http://w3id.org/gaia-x/participant#Participant");
        assertNotNull(vr);
        AssetMetadata assetMetadata = new AssetMetadata(content, vr);
        mockCompService.enqueue(new MockResponse()
                .setBody("{\"error\": \"Conflict\"}")
                .addHeader("Content-Type", "application/json")
                .setResponseCode(409));
        assetStorePublisher.storeCredential(assetMetadata, vr);
        //List<Map<String, Object>> claims = graphStore.queryData(
        //        new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", assetMetadata.getId()))).getResults();
        //Assertions.assertTrue(claims.size() > 0);
        AssetMetadata assetMetadata2 = assetStorePublisher.getByHash(assetMetadata.getAssetHash());
        assertNotNull(assetMetadata2);
        assertEquals(assetMetadata.getAssetHash(), assetMetadata2.getAssetHash());
        assertEquals(assetMetadata.getId(), assetMetadata2.getId());
        assertEquals(assetMetadata.getIssuer(), assetMetadata2.getIssuer());
    }
}
