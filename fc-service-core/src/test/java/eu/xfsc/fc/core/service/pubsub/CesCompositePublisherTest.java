package eu.xfsc.fc.core.service.pubsub;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

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
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.client.ExternalServiceException;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.JacksonConfig;
import eu.xfsc.fc.core.config.PubSubConfig;
import eu.xfsc.fc.core.config.SelfDescriptionStoreConfig;
import eu.xfsc.fc.core.dao.impl.CesTrackerDaoImpl;
import eu.xfsc.fc.core.dao.impl.SchemaDaoImpl;
import eu.xfsc.fc.core.dao.impl.SelfDescriptionDaoImpl;
import eu.xfsc.fc.core.dao.impl.ValidatorCacheDaoImpl;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import eu.xfsc.fc.core.service.graphdb.DummyGraphStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass;
import eu.xfsc.fc.core.service.verification.VerificationServiceImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest(properties = { "publisher.impl=ces", "publisher.url=http://localhost:9091", "publisher.comp-url=http://localhost:9090" })
@ActiveProfiles({"test"}) 
@ContextConfiguration(classes = {CesCompositePublisherTest.TestApplication.class, PubSubConfig.class, JacksonConfig.class, DatabaseConfig.class, SelfDescriptionStoreConfig.class, SelfDescriptionDaoImpl.class,
		DummyGraphStore.class, VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaDaoImpl.class, FileStoreConfig.class, DidResolverConfig.class, DidDocumentResolver.class, HttpDocumentResolver.class, 
		DocumentLoaderConfig.class,	DocumentLoaderProperties.class, ValidatorCacheDaoImpl.class, CesTrackerDaoImpl.class})
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
	private SDPublisher cesPublisher;
	//@Autowired
	//private Neo4j embeddedDatabaseServer;
	@Autowired
	private GraphStore graphStore;
	@Autowired
	private SelfDescriptionStore sdStorePublisher;
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
	public void test01SDStoreRollback() throws Exception {
		//ContentAccessor content = getAccessor("Pub-Sub-Tests/sag-research.jsonld");
		cesPublisher.setTransactional(true);
	    ContentAccessor content = getAccessor("VerificationService/syntax/legalPerson2.jsonld");
	    schemaStore.initializeDefaultSchemas();
	    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "https://w3id.org/gaia-x/core#Participant"); 
	    VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
	    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "http://w3id.org/gaia-x/participant#Participant"); 
	    assertNotNull(vr);
		SelfDescriptionMetadata sdm = new SelfDescriptionMetadata(content, vr);
		mockCompService.enqueue(new MockResponse()
			      .setBody("{\"error\": \"Conflict\"}")
			      .addHeader("Content-Type", "application/json")
			      .setResponseCode(409));
	    Exception ex = assertThrowsExactly(ExternalServiceException.class, () -> sdStorePublisher.storeSelfDescription(sdm, vr));
	    assertEquals(HttpStatusCode.valueOf(409), ((ExternalServiceException) ex).getStatus());
	    List<Map<String, Object>> claims = graphStore.queryData(
	            new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", sdm.getId()))).getResults();
	    Assertions.assertEquals(0, claims.size());
	    Assertions.assertThrows(NotFoundException.class, () -> {sdStorePublisher.getByHash(sdm.getSdHash());});
	}
	  
	@Test
	public void test02SDStoreCommit() throws Exception {
		//ContentAccessor content = getAccessor("Pub-Sub-Tests/sag-research.jsonld");
		cesPublisher.setTransactional(false);
	    ContentAccessor content = getAccessor("VerificationService/syntax/legalPerson2.jsonld");
	    schemaStore.initializeDefaultSchemas();
	    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "https://w3id.org/gaia-x/core#Participant"); 
	    VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
	    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "http://w3id.org/gaia-x/participant#Participant"); 
	    assertNotNull(vr);
		SelfDescriptionMetadata sdm = new SelfDescriptionMetadata(content, vr);
		mockCompService.enqueue(new MockResponse()
			      .setBody("{\"error\": \"Conflict\"}")
			      .addHeader("Content-Type", "application/json")
			      .setResponseCode(409));
	    sdStorePublisher.storeSelfDescription(sdm, vr);
	    //List<Map<String, Object>> claims = graphStore.queryData(
	    //        new GraphQuery("MATCH (n {uri: $uri}) RETURN labels(n), n", Map.of("uri", sdm.getId()))).getResults();
	    //Assertions.assertTrue(claims.size() > 0);
	    SelfDescriptionMetadata sdm2 = sdStorePublisher.getByHash(sdm.getSdHash());
	    assertNotNull(sdm2);
	    assertEquals(sdm.getSdHash(), sdm2.getSdHash());
	    assertEquals(sdm.getId(), sdm2.getId());
	    assertEquals(sdm.getIssuer(), sdm2.getIssuer());
	}
}
