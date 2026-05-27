package eu.xfsc.fc.server.service;

import java.util.List;
import java.util.Locale;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.GraphDatabaseStatus;
import eu.xfsc.fc.api.generated.model.GraphDatabaseSwitchResult;
import eu.xfsc.fc.api.generated.model.SwitchGraphDatabaseRequest;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigEntry;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.dao.assets.ContentKind;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.server.service.graphdb.RoutingGraphStore;
import eu.xfsc.fc.server.generated.controller.GraphDatabaseAdminApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for graph database administration endpoints.
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
@RequiredArgsConstructor
public class GraphDatabaseAdminService implements GraphDatabaseAdminApiDelegate {

  private final GraphStore graphStore;
  private final AssetStore assetStore;
  private final AdminConfigRepository adminConfigRepository;
  private final GraphStoreProbe graphStoreProbe;
  private final RoutingGraphStore routingGraphStore;

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
}
