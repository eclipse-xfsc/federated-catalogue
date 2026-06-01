package eu.xfsc.fc.server.service;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.TrustFrameworkPublicEntry;
import eu.xfsc.fc.core.pojo.TrustFrameworkConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.server.generated.controller.TrustFrameworksApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP delegate for the public trust-framework discovery endpoint.
 * Lists enabled trust frameworks and their active profile IDs for unauthenticated
 * (or any-token) consumers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustFrameworksService implements TrustFrameworksApiDelegate {

  private final TrustFrameworkService trustFrameworkService;
  private final TrustFrameworkRegistry registry;

  @Override
  public ResponseEntity<List<TrustFrameworkPublicEntry>> getTrustFrameworksPublic() {
    log.debug("getTrustFrameworksPublic");

    var activeBundles = registry.getActiveBundles();
    List<TrustFrameworkPublicEntry> entries = trustFrameworkService.findAll().stream()
        .filter(TrustFrameworkConfig::enabled)
        .map(cfg -> {
          List<String> profiles = activeBundles.stream()
              .filter(bundle -> cfg.id().equals(bundle.config().family()))
              .map(bundle -> bundle.config().id())
              .toList();
          return new TrustFrameworkPublicEntry()
              .id(cfg.id())
              .name(cfg.name())
              .profiles(profiles);
        })
        .toList();

    return ResponseEntity.ok(entries);
  }
}
