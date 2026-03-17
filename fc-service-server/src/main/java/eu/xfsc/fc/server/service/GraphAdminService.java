package eu.xfsc.fc.server.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.GraphStatus;
import eu.xfsc.fc.api.generated.model.RebuildStatus;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildProgress;
import eu.xfsc.fc.core.service.graphdb.GraphRebuildService;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.server.generated.controller.GraphAdminApiDelegate;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegate implementation for Graph Admin API endpoints.
 */
@Slf4j
@Service
public class GraphAdminService implements GraphAdminApiDelegate {

  private static final int DEFAULT_CHUNK_COUNT = 1;
  private static final int DEFAULT_CHUNK_ID = 0;

  private final GraphRebuildService graphRebuildService;
  private final GraphStore graphStore;
  private final AssetStore assetStore;
  private final int rebuildThreads;
  private final int rebuildBatchSize;

  public GraphAdminService(GraphRebuildService graphRebuildService, GraphStore graphStore,
                           AssetStore assetStore,
                           @Value("${graphstore.rebuild-threads:4}") int rebuildThreads,
                           @Value("${graphstore.rebuild-batch-size:100}") int rebuildBatchSize) {
    this.graphRebuildService = graphRebuildService;
    this.graphStore = graphStore;
    this.assetStore = assetStore;
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
