package eu.xfsc.fc.server.service.graphdb;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegating {@link GraphStore} that holds the currently-active adapter behind a
 * {@code volatile} reference. The admin "Switch Backend" action updates this reference
 * in process, so a switch takes effect without a JVM restart.
 *
 * <p>On cold boot the active backend is resolved in this order:
 * <ol>
 *   <li>{@code admin_config[graphstore.preferred.backend]} — the persisted preference set
 *       by a prior switch.</li>
 *   <li>{@code graphstore.impl} environment property — the deployment-time bootstrap.</li>
 *   <li>{@link GraphBackendType#NONE} — last-resort fallback so the application can boot
 *       even without a configured graph store.</li>
 * </ol>
 */
@Slf4j
@Component
@Primary
@RequiredArgsConstructor
public class RoutingGraphStore implements GraphStore {

  public static final String KEY_PREFERRED_BACKEND = "graphstore.preferred.backend";

  private final List<GraphStore> availableAdapters;
  private final AdminConfigRepository adminConfigRepository;

  @Value("${graphstore.impl:none}")
  private String envBackendName;

  private final Map<GraphBackendType, GraphStore> adapterByType =
      new EnumMap<>(GraphBackendType.class);
  private volatile GraphStore activeAdapter;

  @PostConstruct
  void initialize() {
    for (GraphStore adapter : availableAdapters) {
      if (adapter == this) {
        continue;
      }
      adapterByType.put(adapter.getBackendType(), adapter);
    }
    GraphBackendType target = resolveInitialBackend();
    activeAdapter = adapterFor(target);
    log.info("RoutingGraphStore initialized with active backend: {} (available adapters: {})",
        target, adapterByType.keySet());
  }

  private GraphBackendType resolveInitialBackend() {
    Optional<GraphBackendType> persisted = adminConfigRepository.getValue(KEY_PREFERRED_BACKEND)
        .flatMap(RoutingGraphStore::parseBackend);
    if (persisted.isPresent()) {
      log.info("Active backend from admin_config: {}", persisted.get());
      return persisted.get();
    }
    Optional<GraphBackendType> fromEnv = parseBackend(envBackendName);
    if (fromEnv.isPresent()) {
      log.info("Active backend from graphstore.impl env: {}", fromEnv.get());
      return fromEnv.get();
    }
    log.info("No persisted or env-configured backend; defaulting to NONE");
    return GraphBackendType.NONE;
  }

  private static Optional<GraphBackendType> parseBackend(String raw) {
    if (raw == null || raw.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(GraphBackendType.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT)));
    } catch (IllegalArgumentException ex) {
      log.warn("Unknown graph backend name '{}' — ignoring", raw);
      return Optional.empty();
    }
  }

  private GraphStore adapterFor(GraphBackendType type) {
    GraphStore adapter = adapterByType.get(type);
    if (adapter == null) {
      log.warn("No adapter for {}; falling back to NONE", type);
      adapter = adapterByType.get(GraphBackendType.NONE);
    }
    if (adapter == null) {
      throw new IllegalStateException(
          "No graph store adapter available — DummyGraphStore must be on the classpath");
    }
    return adapter;
  }

  /**
   * Switches the active adapter to the given backend. Serialized — concurrent switches
   * apply one at a time. The volatile reference makes the change immediately visible to
   * any thread observing it on the next read.
   *
   * <p>Unlike cold-boot resolution, an explicit switch must not silently fall back when
   * the requested backend has no registered adapter — that would let a probe-passing
   * switch return 200 against a backend the server cannot actually serve. Throws a
   * {@link ClientException} so the admin endpoint returns {@code 400}.
   *
   * @param target the backend to activate
   * @throws ClientException if no adapter is registered for the requested backend
   */
  public synchronized void setActive(GraphBackendType target) {
    GraphStore next = adapterByType.get(target);
    if (next == null) {
      throw new ClientException(
          "Backend %s is not available in this deployment (no adapter registered). Available backends: %s".formatted(
              target.name(), adapterByType.keySet()));
    }
    GraphBackendType previous = activeAdapter == null ? null : activeAdapter.getBackendType();
    activeAdapter = next;
    log.info("RoutingGraphStore active backend changed: {} -> {}", previous, target);
  }

  // --- GraphStore delegation ---

  @Override
  public void addClaims(List<RdfClaim> claimList, String credentialSubject) {
    activeAdapter.addClaims(claimList, credentialSubject);
  }

  @Override
  public void deleteClaims(String credentialSubject) {
    activeAdapter.deleteClaims(credentialSubject);
  }

  @Override
  public void deleteValidationResultClaims(String resultIri) {
    activeAdapter.deleteValidationResultClaims(resultIri);
  }

  @Override
  public PaginatedResults<Map<String, Object>> queryData(GraphQuery query) {
    return activeAdapter.queryData(query);
  }

  @Override
  public Optional<String> queryDataAsSparqlResultsJson(GraphQuery query) {
    return activeAdapter.queryDataAsSparqlResultsJson(query);
  }

  @Override
  public Optional<QueryLanguage> getSupportedQueryLanguage() {
    return activeAdapter.getSupportedQueryLanguage();
  }

  @Override
  public GraphBackendType getBackendType() {
    return activeAdapter.getBackendType();
  }

  @Override
  public boolean isHealthy() {
    return activeAdapter.isHealthy();
  }

  @Override
  public long getClaimCount() {
    return activeAdapter.getClaimCount();
  }

  @Override
  public long getRDFAssetCountInGraph() {
    return activeAdapter.getRDFAssetCountInGraph();
  }
}
