package eu.xfsc.fc.server.service;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.sql.DataSource;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.AdminHealthStatus;
import eu.xfsc.fc.api.generated.model.AdminHealthStatusGraphDbStatus;
import eu.xfsc.fc.api.generated.model.AdminStats;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.KeycloakAdminUrl;
import eu.xfsc.fc.core.dao.ParticipantDao;
import eu.xfsc.fc.core.dao.UserDao;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRepository;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.server.config.AdminDashboardConfig;
import eu.xfsc.fc.server.generated.controller.AdminApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Delegate implementation for Admin Dashboard API endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminDashboardService implements AdminApiDelegate {

  private static final Duration KEYCLOAK_TIMEOUT = Duration.ofSeconds(5);

  private final AssetStore assetStore;
  private final SchemaStore schemaStore;
  private final GraphStore graphStore;
  private final ParticipantDao participantDao;
  private final TrustFrameworkRepository trustFrameworkRepository;
  private final UserDao userDao;
  private final DataSource dataSource;
  private final AdminDashboardConfig config;

  @Override
  public ResponseEntity<Void> checkAdminAccess() {
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<AdminStats> getAdminStats() {
    AdminStats stats = new AdminStats();

    AssetFilter allFilter = new AssetFilter();
    allFilter.setLimit(0);
    allFilter.setOffset(0);
    stats.setTotalAssets(safeGet(
        () -> assetStore.getByFilter(allFilter, false, false).getTotalCount(), -1L, "total asset count"));

    AssetFilter activeFilter = new AssetFilter();
    activeFilter.setStatuses(List.of(AssetStatus.ACTIVE));
    activeFilter.setLimit(0);
    activeFilter.setOffset(0);
    stats.setActiveAssets(safeGet(
        () -> assetStore.getByFilter(activeFilter, false, false).getTotalCount(), -1L, "active asset count"));

    stats.setTotalSchemas(safeGet(() -> {
      Map<SchemaStore.SchemaType, List<String>> schemas = schemaStore.getSchemaList();
      return schemas.values().stream().mapToLong(List::size).sum();
    }, -1L, "schema count"));

    stats.setTotalParticipants(safeGet(
        () -> participantDao.search(0, 0).getTotalCount(), -1L, "participant count"));

    stats.setTotalUsers(safeGet(
        () -> userDao.search(null, 0, 0).getTotalCount(), -1L, "user count"));

    stats.setActiveTrustFrameworks(safeGet(
        trustFrameworkRepository::countByEnabledTrue, -1L, "trust framework count"));

    try {
      stats.setGraphClaimCount(graphStore.getClaimCount());
      stats.setGraphBackend(graphStore.getBackendType().name());
    } catch (RuntimeException e) {
      log.warn("Failed to get graph info", e);
      stats.setGraphClaimCount(-1L);
      stats.setGraphBackend("UNKNOWN");
    }

    return ResponseEntity.ok(stats);
  }

  private <T> T safeGet(Supplier<T> supplier, T fallback, String context) {
    try {
      return supplier.get();
    } catch (RuntimeException e) {
      log.warn("Failed to get {}", context, e);
      return fallback;
    }
  }

  @Override
  public ResponseEntity<AdminHealthStatus> getAdminHealth() {
    AdminHealthStatus health = new AdminHealthStatus();

    health.setCatalogueStatus("UP");

    try {
      GraphBackendType backendType = graphStore.getBackendType();
      boolean connected = backendType != GraphBackendType.NONE && graphStore.isHealthy();
      AdminHealthStatusGraphDbStatus graphStatus = new AdminHealthStatusGraphDbStatus();
      graphStatus.setConnected(connected);
      graphStatus.setBackend(backendType.name());
      graphStatus.setClaimCount(graphStore.getClaimCount());
      health.setGraphDbStatus(graphStatus);
    } catch (RuntimeException e) {
      log.warn("Failed to check graph DB health", e);
      AdminHealthStatusGraphDbStatus graphStatus = new AdminHealthStatusGraphDbStatus();
      graphStatus.setConnected(false);
      graphStatus.setBackend("UNKNOWN");
      graphStatus.setClaimCount(-1L);
      health.setGraphDbStatus(graphStatus);
    }

    health.setKeycloakUrl(config.getKeycloakIssuerUrl());
    try {
      config.getWebClient().get().uri(config.getKeycloakIssuerUrl()).retrieve()
          .toBodilessEntity().timeout(KEYCLOAK_TIMEOUT).block();
      health.setKeycloakStatus("UP");
    } catch (RuntimeException e) {
      log.warn("Keycloak health check failed", e);
      health.setKeycloakStatus("DOWN");
    }

    health.setFileStorePath(config.getFileStorePath());
    try {
      health.setFileStoreStatus(Files.isWritable(Paths.get(config.getFileStorePath())) ? "UP" : "DOWN");
    } catch (RuntimeException e) {
      log.warn("File store health check failed", e);
      health.setFileStoreStatus("DOWN");
    }

    try (var conn = dataSource.getConnection()) {
      health.setDatabaseStatus(conn.isValid(5) ? "UP" : "DOWN");
    } catch (SQLException e) {
      log.warn("Database health check failed", e);
      health.setDatabaseStatus("DOWN");
    }

    return ResponseEntity.ok(health);
  }

  @Override
  public ResponseEntity<KeycloakAdminUrl> getKeycloakAdminUrl() {
    KeycloakAdminUrl result = new KeycloakAdminUrl();
    result.setUrl(config.getKeycloakAdminConsoleUrl());
    return ResponseEntity.ok(result);
  }
}
