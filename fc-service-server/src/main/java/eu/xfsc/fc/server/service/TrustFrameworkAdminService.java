package eu.xfsc.fc.server.service;

import java.time.Duration;
import java.util.List;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.api.generated.model.TrustFrameworkConfigUpdate;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEntry;
import eu.xfsc.fc.core.dao.trustframework.TrustFramework;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkMapper;
import eu.xfsc.fc.core.dao.trustframework.TrustFrameworkRepository;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.server.config.AdminDashboardConfig;
import eu.xfsc.fc.server.generated.controller.TrustFrameworkAdminApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for trust framework administration endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustFrameworkAdminService implements TrustFrameworkAdminApiDelegate {

  private static final Duration CONNECTIVITY_TIMEOUT = Duration.ofSeconds(5);

  private final TrustFrameworkRepository trustFrameworkRepository;
  private final AdminDashboardConfig adminDashboardConfig;

  @Value("${federated-catalogue.verification.trust-framework.gaiax.enabled:#{null}}")
  private Boolean gaiaxEnabledEnvVar;

  /**
   * Seeds the Gaia-X trust framework enabled state from the environment variable on startup.
   * If {@code FEDERATED_CATALOGUE_VERIFICATION_TRUST_FRAMEWORK_GAIAX_ENABLED=true} is set,
   * the DB row is updated to enabled=true, overriding the seeded default of false.
   * This preserves backward compatibility with existing Docker Compose test workflows.
   */
  @PostConstruct
  private void seedGaiaxEnabledFromEnv() {
    if (Boolean.TRUE.equals(gaiaxEnabledEnvVar)) {
      trustFrameworkRepository.findById("gaia-x").ifPresent(entity -> {
        entity.setEnabled(true);
        trustFrameworkRepository.save(entity);
      });
      log.info("seedGaiaxEnabledFromEnv; enabled gaia-x trust framework from environment variable");
    }
  }

  @Override
  public ResponseEntity<List<TrustFrameworkEntry>> getTrustFrameworks() {
    List<TrustFrameworkEntry> entries = trustFrameworkRepository.findAll().parallelStream()
        .map(entity -> toEntry(TrustFrameworkMapper.toConfig(entity)))
        .toList();
    return ResponseEntity.ok(entries);
  }

  @Override
  @Transactional
  public ResponseEntity<Void> setTrustFrameworkEnabled(String id, Boolean enabled) {
    TrustFramework entity = trustFrameworkRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Trust framework not found: " + id));
    entity.setEnabled(enabled);
    trustFrameworkRepository.save(entity);
    return ResponseEntity.ok().build();
  }

  @Override
  @Transactional
  public ResponseEntity<Void> updateTrustFrameworkConfig(String id,
      TrustFrameworkConfigUpdate config) {
    TrustFramework entity = trustFrameworkRepository.findById(id)
        .orElseThrow(() -> new NotFoundException("Trust framework not found: " + id));
    entity.setServiceUrl(config.getServiceUrl());
    entity.setApiVersion(config.getApiVersion());
    entity.setTimeoutSeconds(config.getTimeoutSeconds() != null ? config.getTimeoutSeconds() : 30);
    trustFrameworkRepository.save(entity);
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
