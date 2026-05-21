package eu.xfsc.fc.server.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.TrustFrameworkBundleEntry;
import eu.xfsc.fc.api.generated.model.TrustFrameworkConfigUpdate;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEnabledRequest;
import eu.xfsc.fc.api.generated.model.TrustFrameworkEntry;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
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
  private final TrustFrameworkRegistry trustFrameworkRegistry;
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
  public ResponseEntity<Void> setTrustFrameworkEnabled(String id,
      TrustFrameworkEnabledRequest request) {
    trustFrameworkService.setEnabled(id, request.getEnabled());
    return ResponseEntity.ok().build();
  }

  @Override
  public ResponseEntity<Void> setTrustFrameworkRoleEnabled(String bundleId, String roleName, Boolean enabled) {
    trustFrameworkService.setRoleEnabled(bundleId, roleName, enabled);
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
    entry.setBundles(buildBundleEntries(config.id()));
    return entry;
  }

  /**
   * Builds a list of {@link TrustFrameworkBundleEntry} for each bundle belonging to the given
   * family, carrying the per-bundle role enabled states so the UI can address role-toggle PUTs
   * by the correct bundle profile ID.
   *
   * @param familyId trust framework family identifier (e.g. {@code gaia-x})
   * @return list of bundle entries in registry order; empty if no bundles belong to the family
   */
  private List<TrustFrameworkBundleEntry> buildBundleEntries(String familyId) {
    List<TrustFrameworkBundleEntry> result = new ArrayList<>();
    for (TrustFrameworkBundle bundle : trustFrameworkRegistry.getAllBundles()) {
      if (!familyId.equals(bundle.config().family())) {
        continue;
      }
      TrustFrameworkBundleEntry bundleEntry = new TrustFrameworkBundleEntry();
      bundleEntry.setId(bundle.config().id());
      bundleEntry.setRoles(trustFrameworkService.getRoleStates(bundle.config().id()));
      result.add(bundleEntry);
    }
    return result;
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
