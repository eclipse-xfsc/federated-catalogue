package eu.xfsc.fc.server.service;

import java.util.List;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.GraphDatabaseStatus;
import eu.xfsc.fc.api.generated.model.GraphDatabaseSwitchResult;
import eu.xfsc.fc.api.generated.model.GraphStatus;
import eu.xfsc.fc.api.generated.model.RebuildStatus;
import eu.xfsc.fc.api.generated.model.SwitchGraphDatabaseRequest;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigEntry;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.dao.assets.ContentKind;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildProgress;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.server.generated.controller.AdminGraphApiDelegate;
import eu.xfsc.fc.server.service.graphdb.RoutingGraphStore;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegate implementation for graph administration endpoints — both the rebuild/status
 * operations and the backend selection/switch operations.
 *
 * <p>A "Switch Backend" request performs an in-process swap via
 * {@link RoutingGraphStore#setActive(GraphBackendType)} so the change takes effect
 * immediately, with no JVM restart. The chosen backend is also written to
 * {@code admin_config} so the same backend is reactivated on the next cold boot.
 *
 * <p>Switches are pre-flighted via {@link GraphStoreProbe} against the target backend's
 * configured URI. Persisting a preference for an unreachable backend would trap the
 * next cold boot, so the endpoint rejects with {@code 400} when the probe fails and
 * leaves both the persisted preference and the active adapter untouched.
 */
@Slf4j
@Service
public class GraphAdminService implements AdminGraphApiDelegate {

  private static final int DEFAULT_CHUNK_COUNT = 1;
  private static final int DEFAULT_CHUNK_ID = 0;

  private final GraphRebuildService graphRebuildService;
  private final GraphStore graphStore;
  private final AssetStore assetStore;
  private final AdminConfigRepository adminConfigRepository;
  private final GraphStoreProbe graphStoreProbe;
  private final RoutingGraphStore routingGraphStore;
  private final int rebuildThreads;
  private final int rebuildBatchSize;

  public GraphAdminService(GraphRebuildService graphRebuildService, GraphStore graphStore,
                           AssetStore assetStore,
                           AdminConfigRepository adminConfigRepository,
                           GraphStoreProbe graphStoreProbe,
                           RoutingGraphStore routingGraphStore,
                           @Value("${graphstore.rebuild-threads:4}") int rebuildThreads,
                           @Value("${graphstore.rebuild-batch-size:100}") int rebuildBatchSize) {
    this.graphRebuildService = graphRebuildService;
    this.graphStore = graphStore;
    this.assetStore = assetStore;
    this.adminConfigRepository = adminConfigRepository;
    this.graphStoreProbe = graphStoreProbe;
    this.routingGraphStore = routingGraphStore;
    this.rebuildThreads = rebuildThreads;
    this.rebuildBatchSize = rebuildBatchSize;
  }

  /**
   * Triggers an async graph rebuild. Returns 202 if started, 409 if already running.
   *
   * @return rebuild status with the appropriate HTTP status code
   */
  @Override
  public ResponseEntity<RebuildStatus> triggerGraphRebuild() {
    log.info("triggerGraphRebuild.enter");
    boolean started = graphRebuildService.triggerRebuild(
        DEFAULT_CHUNK_COUNT, DEFAULT_CHUNK_ID, rebuildThreads, rebuildBatchSize);
    RebuildStatus dto = toRebuildStatusDto(graphRebuildService.getStatus());
    HttpStatus status = started ? HttpStatus.ACCEPTED : HttpStatus.CONFLICT;
    return ResponseEntity.status(status).body(dto);
  }

  /**
   * Returns the current rebuild progress.
   *
   * @return current {@link RebuildStatus}
   */
  @Override
  public ResponseEntity<RebuildStatus> getGraphRebuildStatus() {
    RebuildStatus dto = toRebuildStatusDto(graphRebuildService.getStatus());
    return ResponseEntity.ok(dto);
  }

  /**
   * Returns the graph store status including backend type, health, and sync assessment.
   *
   * @return current {@link GraphStatus}
   */
  @Override
  public ResponseEntity<GraphStatus> getGraphStatus() {
    GraphBackendType backendType = graphStore.getBackendType();
    boolean enabled = backendType != GraphBackendType.NONE;

    GraphStatus dto = new GraphStatus();
    dto.setBackend(backendType.name());
    dto.setEnabled(enabled);

    if (!enabled) {
      dto.setHealthy(false);
      dto.setActiveAssetCount(0L);
      dto.setClaimCountInGraph(0L);
      dto.setAssetCountInGraph(0L);
      dto.setSyncAssessment("disabled");
      return ResponseEntity.ok(dto);
    }

    dto.setHealthy(graphStore.isHealthy());

    AssetFilter filter = new AssetFilter();
    filter.setStatuses(List.of(AssetStatus.ACTIVE));
    filter.setLimit(0);
    filter.setOffset(0);
    long activeAssetCount = assetStore.getByFilter(filter, false, false).getTotalCount();
    long claimCount = graphStore.getClaimCount();
    long assetCountInGraph = graphStore.getRDFAssetCountInGraph();

    dto.setActiveAssetCount(activeAssetCount);
    dto.setClaimCountInGraph(claimCount);
    dto.setAssetCountInGraph(assetCountInGraph);

    String syncAssessment;
    if (assetCountInGraph == -1) {
      syncAssessment = "unknown";
    } else if (assetCountInGraph == 0 && activeAssetCount == 0) {
      syncAssessment = "empty";
    } else if (assetCountInGraph == activeAssetCount && activeAssetCount > 0) {
      syncAssessment = "in-sync";
    } else {
      syncAssessment = "out-of-sync";
    }
    dto.setSyncAssessment(syncAssessment);

    return ResponseEntity.ok(dto);
  }

  @Override
  public ResponseEntity<GraphDatabaseStatus> getGraphDatabaseStatus() {
    GraphDatabaseStatus status = new GraphDatabaseStatus();
    try {
      GraphBackendType backendType = graphStore.getBackendType();
      long rdfAssetCount = countActiveRdfAssets();
      status.setActiveBackend(backendType.name());
      status.setConnected(backendType != GraphBackendType.NONE && graphStore.isHealthy());
      status.setClaimCount(graphStore.getClaimCount());
      status.setVersion(buildVersionString(backendType));
      status.setRebuildNeeded(computeRebuildNeeded(backendType, status.getClaimCount(),
          rdfAssetCount));
      status.setRdfAssetCount(rdfAssetCount);
    } catch (RuntimeException ex) {
      log.warn("Failed to get graph database status", ex);
      status.setActiveBackend("UNKNOWN");
      status.setConnected(false);
      status.setClaimCount(-1L);
      status.setVersion("unavailable");
      status.setRebuildNeeded(false);
      status.setRdfAssetCount(0L);
    }
    return ResponseEntity.ok(status);
  }

  @Override
  public ResponseEntity<GraphDatabaseSwitchResult> switchGraphDatabase(
      SwitchGraphDatabaseRequest request) {
    GraphBackendType target = parseTarget(request.getBackend());

    GraphStoreProbe.Result probe = graphStoreProbe.probe(target);
    if (!probe.reachable()) {
      throw new ClientException(
          "Target backend " + target.name() + " is not reachable: " + probe.message()
              + ". Verify the backend container is up and the URI configuration is correct.");
    }

    // Swap the live adapter first; persist the preference only after a successful swap.
    // Reversing this order would leave a preference pointing at a backend the server
    // could not actually serve, trapping the next cold boot.
    routingGraphStore.setActive(target);

    AdminConfigEntry entry = adminConfigRepository.findById(RoutingGraphStore.KEY_PREFERRED_BACKEND)
        .orElse(new AdminConfigEntry(RoutingGraphStore.KEY_PREFERRED_BACKEND, null, null));
    entry.setConfigValue(target.name());
    adminConfigRepository.save(entry);

    GraphDatabaseSwitchResult result = new GraphDatabaseSwitchResult();
    result.setMessage("Graph database backend switched to " + target.name()
        + ". " + probe.message()
        + ". If the new graph store has no claims but RDF assets exist, the page will "
        + "offer a one-click rebuild.");
    return ResponseEntity.ok(result);
  }

  private GraphBackendType parseTarget(String backend) {
    if (backend == null || backend.isBlank()) {
      throw new ClientException("Missing 'backend' field. Valid options: "
          + List.of(GraphBackendType.values()));
    }
    try {
      return GraphBackendType.valueOf(backend.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      throw new ClientException("Invalid graph database backend: " + backend
          + ". Valid options: NEO4J, FUSEKI, NONE");
    }
  }

  private boolean computeRebuildNeeded(GraphBackendType backendType, long claimCount,
                                       long rdfAssetCount) {
    if (backendType == GraphBackendType.NONE || claimCount != 0L) {
      return false;
    }
    return rdfAssetCount > 0L;
  }

  private long countActiveRdfAssets() {
    // AssetFilter.setLimit(0) means "no limit" — would materialize every row on each
    // status load. Page size 1 still yields the full totalCount via the COUNT query.
    AssetFilter filter = new AssetFilter();
    filter.setStatuses(List.of(AssetStatus.ACTIVE));
    filter.setContentKinds(List.of(ContentKind.RDF));
    filter.setLimit(1);
    filter.setOffset(0);
    return assetStore.getByFilter(filter, false, false).getTotalCount();
  }

  private String buildVersionString(GraphBackendType backendType) {
    return backendType == GraphBackendType.NONE
        ? "(no graph database)"
        : backendType.name() + " (active)";
  }

  /**
   * Maps the internal rebuild status to the API DTO.
   *
   * @param internal the internal status from {@link GraphRebuildService}
   * @return the API-facing {@link RebuildStatus} DTO
   */
  private RebuildStatus toRebuildStatusDto(GraphRebuildProgress internal) {
    RebuildStatus dto = new RebuildStatus();
    dto.setTotal(internal.getTotal());
    dto.setProcessed(internal.getProcessedCount());
    dto.setPercentComplete(internal.getPercentComplete());
    dto.setRunning(graphRebuildService.isRunning());
    dto.setComplete(internal.isComplete());
    dto.setFailed(internal.isFailed());
    dto.setErrorMessage(internal.getErrorMessage());
    dto.setDurationMs(internal.getDurationMs());
    dto.setErrors(internal.getErrorCount());
    return dto;
  }
}
