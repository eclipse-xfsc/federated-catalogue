package eu.xfsc.fc.server.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.pojo.GraphBackendType;
import lombok.extern.slf4j.Slf4j;

/**
 * Reachability probe for graph store backends, independent of which backend is currently
 * active. Used by {@code GraphAdminService} to pre-flight a switch — the operator
 * cannot persist a backend the server will then fail to boot against.
 *
 * <p>Each probe opens a short-lived connection (Bolt driver verifyConnectivity, HTTP GET
 * on the Fuseki dataset URL) and reports a {@link Result} with the reason on failure so
 * the admin endpoint can return an actionable {@code 400}.
 */
@Slf4j
@Component
public class GraphStoreProbe {

  private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(3);

  private final String neo4jUri;
  private final String neo4jUser;
  private final String neo4jPassword;
  private final String fusekiUri;

  public GraphStoreProbe(
      @Value("${graphstore.neo4j.uri:${graphstore.uri:}}") String neo4jUri,
      @Value("${graphstore.user:}") String neo4jUser,
      @Value("${graphstore.password:}") String neo4jPassword,
      @Value("${graphstore.fuseki.uri:${graphstore.uri:}}") String fusekiUri) {
    this.neo4jUri = neo4jUri;
    this.neo4jUser = neo4jUser;
    this.neo4jPassword = neo4jPassword;
    this.fusekiUri = fusekiUri;
  }

  public Result probe(GraphBackendType target) {
    return switch (target) {
      case NEO4J -> probeNeo4j();
      case FUSEKI -> probeFuseki();
      case NONE -> Result.reachable("DummyGraphStore is always available");
    };
  }

  private Result probeNeo4j() {
    if (neo4jUri == null || neo4jUri.isBlank()) {
      return Result.unreachable("Neo4j URI is not configured (graphstore.neo4j.uri)");
    }
    // Cap driver-side connect / acquisition / pool size so a black-holed host fails within
    // PROBE_TIMEOUT instead of stalling on the OS-level connect timeout (~75s on Linux).
    Config cfg = Config.builder()
        .withConnectionTimeout(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        .withConnectionAcquisitionTimeout(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)
        .withMaxConnectionPoolSize(1)
        .build();
    try (Driver driver = GraphDatabase.driver(
        neo4jUri, AuthTokens.basic(neo4jUser, neo4jPassword), cfg)) {
      driver.verifyConnectivity();
      return Result.reachable("Neo4j at " + neo4jUri + " is reachable");
    } catch (RuntimeException ex) {
      log.warn("Neo4j probe failed at {}", neo4jUri, ex);
      return Result.unreachable("Neo4j at " + neo4jUri + " is not reachable: " + ex.getMessage());
    }
  }

  private Result probeFuseki() {
    if (fusekiUri == null || fusekiUri.isBlank()) {
      return Result.unreachable("Fuseki URI is not configured (graphstore.fuseki.uri)");
    }
    try {
      HttpClient client = HttpClient.newBuilder().connectTimeout(PROBE_TIMEOUT).build();
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(fusekiUri))
          .timeout(PROBE_TIMEOUT)
          .GET()
          .build();
      HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
      // Fuseki returns 200 on a dataset URL and 400 on root-level GET without a query.
      // Either is a positive signal that the server is up; we only fail on connection
      // errors or 5xx.
      if (resp.statusCode() >= 500) {
        return Result.unreachable("Fuseki at " + fusekiUri + " responded with HTTP " + resp.statusCode());
      }
      return Result.reachable("Fuseki at " + fusekiUri + " is reachable (HTTP " + resp.statusCode() + ")");
    } catch (RuntimeException | java.io.IOException | InterruptedException ex) {
      if (ex instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      log.warn("Fuseki probe failed at {}", fusekiUri, ex);
      return Result.unreachable("Fuseki at " + fusekiUri + " is not reachable: " + ex.getMessage());
    }
  }

  public record Result(boolean reachable, String message) {
    public static Result reachable(String message) {
      return new Result(true, message);
    }

    public static Result unreachable(String message) {
      return new Result(false, message);
    }
  }
}
