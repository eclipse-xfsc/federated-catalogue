package eu.xfsc.fc.server.service;

import java.time.Duration;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.TrustFrameworkConfigUpdate;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEntry;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.server.config.AdminDashboardConfig;
import eu.xfsc.fc.server.generated.controller.TrustFrameworkAdminApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP delegate for the trust framework admin endpoints. Delegates persistence operations
 * to {@link TrustFrameworkService}; only HTTP-shape concerns (status codes, DTO mapping,
 * connectivity probing) live here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustFrameworkAdminService implements TrustFrameworkAdminApiDelegate {

  private static final Duration CONNECTIVITY_TIMEOUT = Duration.ofSeconds(5);
  private static final int DEFAULT_TIMEOUT_SECONDS = 30;

  private final TrustFrameworkService trustFrameworkService;
  private final AdminDashboardConfig adminDashboardConfig;

  @Value("${federated-catalogue.enabled-trust-frameworks:}")
  private List<String> enabledTrustFrameworkFamilies;

  /**
   * Flips DB rows to {@code enabled=true} for each trust framework family listed in
   * {@code federated-catalogue.enabled-trust-frameworks} (env:
   * {@code FEDERATED_CATALOGUE_ENABLED_TRUST_FRAMEWORKS}). Unknown family IDs are ignored.
   * This is the deployment-time override path; runtime changes go through the admin API.
   */
  @PostConstruct
  private void seedEnabledFrameworksFromConfig() {
    for (String family : enabledTrustFrameworkFamilies) {
      if (family == null || family.isBlank()) {
        continue;
      }
      String id = family.trim();
      try {
        trustFrameworkService.setEnabled(id, true);
        log.info("seedEnabledFrameworksFromConfig; enabled trust framework '{}' from config", id);
      } catch (NotFoundException e) {
        log.warn("seedEnabledFrameworksFromConfig; trust framework '{}' not registered — override ignored", id);
      }
    }
  }

  @Override
  public ResponseEntity<List<TrustFrameworkEntry>> getTrustFrameworks() {
    List<TrustFrameworkEntry> entries = trustFrameworkService.findAll().stream()
        .map(this::toEntry)
        .toList();
    return ResponseEntity.ok(entries);
  }

  @Override
  public ResponseEntity<Void> setTrustFrameworkEnabled(String id, Boolean enabled) {
    trustFrameworkService.setEnabled(id, enabled);
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> updateTrustFrameworkConfig(String id,
      TrustFrameworkConfigUpdate config) {
    int timeoutSeconds = config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
    if (trustFrameworkService.updateConfig(id, config.getServiceUrl(), config.getApiVersion(), timeoutSeconds)
        .isEmpty()) {
      throw new NotFoundException("Trust framework not found: " + id);
    }
    return ResponseEntity.ok().build();
  }

  private TrustFrameworkEntry toEntry(TrustFrameworkConfig config) {
    TrustFrameworkEntry entry = new TrustFrameworkEntry();
    entry.setId(config.id());
    entry.setName(config.name());
    entry.setServiceUrl(config.serviceUrl());
    entry.setApiVersion(config.apiVersion());
    entry.setTimeoutSeconds(config.timeoutSeconds());
    entry.setEnabled(config.enabled());
    entry.setConnected(checkConnectivity(config));
    return entry;
  }

  private boolean checkConnectivity(TrustFrameworkConfig config) {
    if (config.serviceUrl() == null || config.serviceUrl().isBlank()) {
      return false;
    }
    try {
      adminDashboardConfig.getWebClient().get().uri(config.serviceUrl())
          .retrieve().toBodilessEntity()
          .timeout(Duration.ofSeconds(config.timeoutSeconds() > 0
              ? Math.min(config.timeoutSeconds(), CONNECTIVITY_TIMEOUT.getSeconds())
              : CONNECTIVITY_TIMEOUT.getSeconds()))
          .block();
      return true;
    } catch (Exception e) {
      log.warn("Trust framework connectivity check failed for {}", config.id(), e);
      return false;
    }
  }
}
