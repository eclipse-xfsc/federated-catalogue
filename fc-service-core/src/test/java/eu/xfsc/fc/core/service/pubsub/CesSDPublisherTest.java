package eu.xfsc.fc.core.service.pubsub;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatusCode;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.client.ExternalServiceException;
import eu.xfsc.fc.core.config.JacksonConfig;
import eu.xfsc.fc.core.config.PubSubConfig;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest(properties = { "publisher.impl=ces", "publisher.url=http://localhost:9091", "publisher.comp-url=http://localhost:9090", "publisher.transactional=true" })
@ActiveProfiles({"test"}) 
@ContextConfiguration(classes = {PubSubConfig.class, JacksonConfig.class})
public class CesSDPublisherTest {
	
	@Autowired
	private SDPublisher cesPublisher;

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
    }
    
	@Test
	public void test01SDPublishSuccess() throws Exception {
		ContentAccessor content = getAccessor("Pub-Sub-Tests/sag-research.jsonld");
		VerificationResult vr = new VerificationResultOffering(Instant.now(), "ACTIVE", "did:web:sagresearch.de", Instant.now(), "https://sagresearch.de/participant.json", null, null);
		SelfDescriptionMetadata sdm = new SelfDescriptionMetadata(content, vr);
		ContentAccessor response = getAccessor("Pub-Sub-Tests/sag-research-response.jsonld");
		mockCompService.enqueue(new MockResponse()
			      .setBody(response.getContentAsString())
			      .addHeader("Content-Type", "application/json"));
		mockCesService.enqueue(new MockResponse()
			      .addHeader("Content-Type", "application/json")
			      .addHeader("Location", "https://ces-v1.lab.gaia-x.eu/credentials-events/797d2011-bb4f-4472-8cbd-158bec554f60")
			      .setResponseCode(201));
		boolean pub = cesPublisher.publish(sdm, vr);
		assertTrue(pub);
	}

	@Test
	public void test02SDPublishCompFail() throws Exception {
		ContentAccessor content = getAccessor("Pub-Sub-Tests/sag-research.jsonld");
		VerificationResult vr = new VerificationResultOffering(Instant.now(), "ACTIVE", "did:web:sagresearch.de", Instant.now(), "https://sagresearch.de/participant.json", null, null);
		SelfDescriptionMetadata sdm = new SelfDescriptionMetadata(content, vr);
		mockCompService.enqueue(new MockResponse()
			      .setBody("{\"error\": \"Conflict\"}")
			      .addHeader("Content-Type", "application/json")
			      .setResponseCode(409));
	    Exception ex = assertThrowsExactly(ExternalServiceException.class, () -> cesPublisher.publish(sdm, vr));
	    assertEquals(HttpStatusCode.valueOf(409), ((ExternalServiceException) ex).getStatus());
	}

	@Test
	public void test03SDPublishCesFail() throws Exception {
		ContentAccessor content = getAccessor("Pub-Sub-Tests/sag-research.jsonld");
		VerificationResult vr = new VerificationResultOffering(Instant.now(), "ACTIVE", "did:web:sagresearch.de", Instant.now(), "https://sagresearch.de/participant.json", null, null);
		SelfDescriptionMetadata sdm = new SelfDescriptionMetadata(content, vr);
		ContentAccessor response = getAccessor("Pub-Sub-Tests/comp-response.jsonld");
		mockCompService.enqueue(new MockResponse()
			      .setBody(response.getContentAsString())
			      .addHeader("Content-Type", "application/json"));
		mockCesService.enqueue(new MockResponse()
			      .setBody("{\"error\": \"Internal Server Error\"}")
			      .addHeader("Content-Type", "application/json")
			      .setResponseCode(500));
	    Exception ex = assertThrowsExactly(ExternalServiceException.class, () -> cesPublisher.publish(sdm, vr));
	    assertEquals(HttpStatusCode.valueOf(500), ((ExternalServiceException) ex).getStatus());
	}

	@Test
	public void test04SDPublishTxOff() throws Exception {
		ContentAccessor content = getAccessor("Pub-Sub-Tests/sag-research.jsonld");
		VerificationResult vr = new VerificationResultOffering(Instant.now(), "ACTIVE", "did:web:sagresearch.de", Instant.now(), "https://sagresearch.de/participant.json", null, null);
		SelfDescriptionMetadata sdm = new SelfDescriptionMetadata(content, vr);
		mockCompService.enqueue(new MockResponse()
			      .setBody("{\"error\": \"Conflict\"}")
			      .addHeader("Content-Type", "application/json")
			      .setResponseCode(409));
		mockCesService.enqueue(new MockResponse()
			      .setBody("{\"error\": \"Internal Server Error\"}")
			      .addHeader("Content-Type", "application/json")
			      .setResponseCode(500));
		cesPublisher.setTransactional(false);
		boolean pub = cesPublisher.publish(sdm, vr);
		assertTrue(pub);
	}
	
}
