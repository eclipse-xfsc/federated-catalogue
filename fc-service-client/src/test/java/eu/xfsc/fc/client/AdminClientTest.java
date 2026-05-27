package eu.xfsc.fc.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.RebuildStatus;
import reactor.core.publisher.Mono;

/**
 * Tests for {@link AdminClient}. Focused on the rebuild-trigger path — the OpenAPI
 * advertises a {@code 409} response with a {@link RebuildStatus} body so callers can
 * see current progress when a rebuild is already running. The SDK must surface that
 * body and status code, not throw it away.
 */
class AdminClientTest {

  private static final String CONFLICT_BODY = """
      {
        "running": true,
        "percentComplete": 42,
        "processed": 21,
        "total": 50
      }
      """;

  @Test
  void triggerGraphRebuild_serverReturns409_surfacesStatusAndBody() {
    ExchangeFunction stub = request -> Mono.just(ClientResponse.create(HttpStatus.CONFLICT)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(CONFLICT_BODY)
        .build());
    WebClient webClient = WebClient.builder()
        .filter(ServiceClient.errorPropagationFilter())
        .exchangeFunction(stub)
        .build();
    AdminClient client = new AdminClient("http://test", webClient);

    ResponseEntity<RebuildStatus> response = client.triggerGraphRebuild(null);

    assertEquals(HttpStatus.CONFLICT, response.getStatusCode(),
        "409 status must survive through to the caller");
    RebuildStatus body = response.getBody();
    assertEquals(Boolean.TRUE, body.getRunning(),
        "RebuildStatus body must be deserialized and exposed on the conflict response");
    assertEquals(21L, body.getProcessed());
    assertEquals(50L, body.getTotal());
  }

  @Test
  void triggerGraphRebuild_serverReturns202_surfacesStatusAndBody() {
    String acceptedBody = """
        {
          "running": true,
          "percentComplete": 0,
          "processed": 0,
          "total": 50
        }
        """;
    ExchangeFunction stub = request -> Mono.just(ClientResponse.create(HttpStatus.ACCEPTED)
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .body(acceptedBody)
        .build());
    WebClient webClient = WebClient.builder()
        .filter(ServiceClient.errorPropagationFilter())
        .exchangeFunction(stub)
        .build();
    AdminClient client = new AdminClient("http://test", webClient);

    ResponseEntity<RebuildStatus> response = client.triggerGraphRebuild(null);

    assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
    assertEquals(Boolean.TRUE, response.getBody().getRunning());
  }
}
