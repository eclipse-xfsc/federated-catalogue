package eu.xfsc.fc.core.service.trustframework.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import eu.xfsc.fc.core.exception.ServiceUnavailableException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * Unit tests for {@link GxdchComplianceClient} against a local HTTP stub.
 * No Spring context required.
 */
class GxdchComplianceClientTest {

  // JWT with payload {"id":"https://example.com/asset-001"} — sent as VP body
  private static final String TEST_VP_JWT =
      "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
      + ".eyJpZCI6Imh0dHBzOi8vZXhhbXBsZS5jb20vYXNzZXQtMDAxIn0.";

  // JWT with payload {} — no id claim
  private static final String VP_JWT_NO_ID =
      "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.e30.";

  // Compliance credential JWT with iss=did:web:compliance.example, exp=1767223999
  private static final String CANNED_CC_JWT =
      "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0"
      + ".eyJpc3MiOiJkaWQ6d2ViOmNvbXBsaWFuY2UuZXhhbXBsZSIsImV4cCI6MTc2NzIyMzk5OX0.";

  private MockWebServer server;
  private GxdchComplianceClient client;
  private TrustFrameworkProfileConfig config;

  @BeforeEach
  void setUp() throws IOException {
    server = new MockWebServer();
    server.start();

    client = new GxdchComplianceClient();

    config = new TrustFrameworkProfileConfig(
        "mock-2026",
        "mock",
        "gxdch-loire",
        server.url("").toString(),
        "loire",
        30
    );
  }

  @AfterEach
  void tearDown() throws IOException {
    server.shutdown();
  }

  @Test
  void clientType_returns_gxdchLoire() {
    assertEquals("gxdch-loire", client.clientType());
  }

  @Test
  void check_201WithComplianceJwt_returnsIssuedAttestation() throws InterruptedException {

    server.enqueue(new MockResponse()
        .setResponseCode(201)
        .setBody(CANNED_CC_JWT)
        .addHeader("Content-Type", "text/plain"));

    var credential = new ContentAccessorDirect(TEST_VP_JWT);

    ComplianceCheckOutcome outcome = client.check(credential, config);

    assertInstanceOf(IssuedAttestation.class, outcome);
    assertTrue(outcome.compliant());
    var attestation = (IssuedAttestation) outcome;
    assertEquals(CANNED_CC_JWT, attestation.attestationCredential());

    RecordedRequest req = server.takeRequest();
    String expectedVcid = URLEncoder.encode("https://example.com/asset-001", StandardCharsets.UTF_8);
    assertTrue(req.getPath().contains("/api/credential-offers/standard-compliance"),
        "Path must include Loire compliance endpoint");
    assertTrue(req.getPath().contains("vcid=" + expectedVcid),
        "vcid query param must be single-percent-encoded");
    assertEquals(TEST_VP_JWT, req.getBody().readUtf8());
  }

  @Test
  void check_400Response_returnsUnverifiableAttestation() {

    final String errorBody = "Invalid Certificate: mock non-compliant asset";
    server.enqueue(new MockResponse()
        .setResponseCode(400)
        .setBody(errorBody)
        .addHeader("Content-Type", "text/plain"));

    var credential = new ContentAccessorDirect(TEST_VP_JWT);

    ComplianceCheckOutcome outcome = client.check(credential, config);

    assertInstanceOf(UnverifiableAttestation.class, outcome);
    assertFalse(outcome.compliant());
    var unverifiable = (UnverifiableAttestation) outcome;
    assertEquals(FailureCategory.UNVERIFIABLE_ATTESTATION, unverifiable.failureCategory());
    assertEquals(errorBody, unverifiable.rawAttestation());
  }

  @Test
  void check_vpJwtWithNoIdClaim_returnsUnverifiableAttestation_withoutHttpCall() {

    var credential = new ContentAccessorDirect(VP_JWT_NO_ID);

    ComplianceCheckOutcome outcome = client.check(credential, config);

    assertInstanceOf(UnverifiableAttestation.class, outcome);
    assertFalse(outcome.compliant());
    var unverifiable = (UnverifiableAttestation) outcome;
    assertEquals(FailureCategory.UNVERIFIABLE_ATTESTATION, unverifiable.failureCategory());
    assertEquals(VP_JWT_NO_ID, unverifiable.rawAttestation());
    assertEquals("VP JWT has no 'id' claim", unverifiable.verificationError());
    assertEquals(0, server.getRequestCount(), "No HTTP request must be sent for missing id claim");
  }

  @Test
  void check_slowUpstream_throwsTimeoutException() {

    server.enqueue(new MockResponse()
        .setBodyDelay(3, TimeUnit.SECONDS)
        .setResponseCode(201)
        .setBody(CANNED_CC_JWT));

    var shortTimeoutConfig = new TrustFrameworkProfileConfig(
        "mock-2026", "mock", "gxdch-loire",
        server.url("").toString(), "loire", 1
    );
    var credential = new ContentAccessorDirect(TEST_VP_JWT);

    assertThrows(TimeoutException.class, () -> client.check(credential, shortTimeoutConfig));
  }

  @Test
  void check_5xxResponse_throwsServiceUnavailableException() {

    server.enqueue(new MockResponse()
        .setResponseCode(500)
        .setBody("Internal Server Error"));

    var credential = new ContentAccessorDirect(TEST_VP_JWT);

    assertThrows(ServiceUnavailableException.class, () -> client.check(credential, config));
  }

  @Test
  void check_serviceUrlWithTrailingSlash_stripsTrailingSlash() throws InterruptedException {

    server.enqueue(new MockResponse()
        .setResponseCode(201)
        .setBody(CANNED_CC_JWT)
        .addHeader("Content-Type", "text/plain"));

    var trailingSlashConfig = new TrustFrameworkProfileConfig(
        "mock-2026", "mock", "gxdch-loire",
        server.url("") + "/", "loire", 30
    );
    var credential = new ContentAccessorDirect(TEST_VP_JWT);

    assertInstanceOf(IssuedAttestation.class, client.check(credential, trailingSlashConfig),
        "Expected HTTP request to be made and compliance credential returned");

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertNotNull(req, "Expected an HTTP request to be recorded");
    assertTrue(req.getPath().startsWith("/api/credential-offers/standard-compliance"),
        "Path must not have double slash from trailing serviceUrl");
  }

  @Test
  void check_malformedComplianceJwtOn201_returnsUnverifiableAttestation() {

    server.enqueue(new MockResponse()
        .setResponseCode(201)
        .setBody("not-a-jwt")
        .addHeader("Content-Type", "text/plain"));

    var credential = new ContentAccessorDirect(TEST_VP_JWT);

    ComplianceCheckOutcome outcome = client.check(credential, config);

    assertInstanceOf(UnverifiableAttestation.class, outcome);
    assertFalse(outcome.compliant());
    var unverifiable = (UnverifiableAttestation) outcome;
    assertEquals(FailureCategory.UNVERIFIABLE_ATTESTATION, unverifiable.failureCategory());
    assertEquals("Compliance credential is not a parseable JWT", unverifiable.verificationError());
  }
}
