package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.pojo.GraphBackendType;

/**
 * Unit tests for {@link GraphStoreProbe}.
 *
 * <p>Probes must honor the class-level timeout budget for any backend — including the
 * Neo4j Bolt path. A black-holed host (non-routable IP) would otherwise stall on the
 * OS-level connect timeout (tens of seconds on Linux) and trap callers that assume the
 * configured budget.
 */
class GraphStoreProbeTest {

  // Non-routable IP (RFC 1918 unused range), guaranteed to silently drop SYNs locally.
  // Picking a port that no Bolt listener uses; the connect must time out, not refuse.
  private static final String BLACKHOLE_BOLT_URI = "bolt://10.255.255.1:7687";
  private static final Duration BUDGET_PLUS_SLACK = Duration.ofSeconds(8);

  @Test
  void probe_blackHoledNeo4jHost_failsWithinTimeoutBudget() {
    GraphStoreProbe probe = new GraphStoreProbe(BLACKHOLE_BOLT_URI, "", "", "");

    Instant before = Instant.now();
    GraphStoreProbe.Result result = probe.probe(GraphBackendType.NEO4J);
    Duration elapsed = Duration.between(before, Instant.now());

    assertFalse(result.reachable(), "black-holed host must be reported unreachable");
    assertTrue(elapsed.compareTo(BUDGET_PLUS_SLACK) < 0,
        "Neo4j probe must fail within " + BUDGET_PLUS_SLACK + " but took " + elapsed);
  }
}
