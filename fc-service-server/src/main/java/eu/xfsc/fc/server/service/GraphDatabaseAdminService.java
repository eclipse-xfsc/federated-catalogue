package eu.xfsc.fc.server.service;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.GraphDatabaseStatus;
import eu.xfsc.fc.api.generated.model.GraphDatabaseSwitchResult;
import eu.xfsc.fc.api.generated.model.SwitchGraphDatabaseRequest;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigEntry;
import eu.xfsc.fc.core.dao.adminconfig.AdminConfigRepository;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.server.generated.controller.GraphDatabaseAdminApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for graph database administration endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GraphDatabaseAdminService implements GraphDatabaseAdminApiDelegate {

  private static final String KEY_PREFERRED_BACKEND = "graphstore.preferred.backend";

  private final GraphStore graphStore;
  private final AdminConfigRepository adminConfigRepository;

  @Override
  public ResponseEntity<GraphDatabaseStatus> getGraphDatabaseStatus() {
    GraphDatabaseStatus status = new GraphDatabaseStatus();
    try {
      GraphBackendType backendType = graphStore.getBackendType();
      status.setActiveBackend(backendType.name());
      status.setConnected(backendType != GraphBackendType.NONE && graphStore.isHealthy());
      status.setClaimCount(graphStore.getClaimCount());
      status.setVersion(backendType.name() + " (active)");
    } catch (Exception e) {
      log.warn("Failed to get graph database status", e);
      status.setActiveBackend("UNKNOWN");
      status.setConnected(false);
      status.setClaimCount(-1L);
      status.setVersion("unavailable");
    }
    return ResponseEntity.ok(status);
  }

  @Override
  public ResponseEntity<GraphDatabaseSwitchResult> switchGraphDatabase(
      SwitchGraphDatabaseRequest request) {
    String backend = request.getBackend();

    // Validate backend name
    try {
      GraphBackendType.valueOf(backend.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new ClientException("Invalid graph database backend: " + backend
          + ". Valid options: NEO4J, FUSEKI, NONE");
    }

    // Persist the preferred backend
    AdminConfigEntry entry = adminConfigRepository.findById(KEY_PREFERRED_BACKEND)
        .orElse(new AdminConfigEntry(KEY_PREFERRED_BACKEND, null, null));
    entry.setConfigValue(backend.toUpperCase());
    adminConfigRepository.save(entry);

    GraphDatabaseSwitchResult result = new GraphDatabaseSwitchResult();
    result.setRestartRequired(true);
    result.setMessage("Graph database backend set to " + backend.toUpperCase()
        + ". Restart the server for the change to take effect. "
        + "After restart, trigger a graph rebuild to re-index existing assets.");
    return ResponseEntity.ok(result);
  }
}
