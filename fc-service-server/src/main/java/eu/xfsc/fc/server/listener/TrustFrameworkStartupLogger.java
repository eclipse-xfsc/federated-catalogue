package eu.xfsc.fc.server.listener;

import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

/**
 * Emits one INFO log line per registered trust-framework family that is disabled at startup,
 * listing both activation paths (Admin API and environment variable).
 *
 * <p>The log line is intentionally verbose so that an operator can copy-paste the activation
 * command from the server log without consulting external documentation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TrustFrameworkStartupLogger implements ApplicationListener<ApplicationReadyEvent> {

  private static final String DISABLED_FAMILY_MSG =
      "trust-framework '{}' registered (profiles: {}) — disabled."
          + " Enable via PATCH /admin/trust-frameworks/{} with body {\"enabled\":true}"
          + " or env FEDERATED_CATALOGUE_ENABLED_TRUST_FRAMEWORKS={}";

  private final TrustFrameworkRegistry trustFrameworkRegistry;
  private final TrustFrameworkService trustFrameworkService;

  /**
   * Logs a discoverability hint for every registered trust-framework family that is currently
   * disabled. Families that are already enabled are not logged (noise reduction).
   *
   * @param event the application ready event
   */
  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    Map<String, List<String>> profilesByFamily = trustFrameworkRegistry.getAllBundles()
        .stream()
        .collect(Collectors.groupingBy(
            bundle -> bundle.config().family(),
            Collectors.mapping(bundle -> bundle.config().id(), Collectors.toList())
        ));

    for (Map.Entry<String, List<String>> entry : profilesByFamily.entrySet()) {
      String family = entry.getKey();
      String profiles = String.join(", ", entry.getValue());
      if (!trustFrameworkService.isEnabled(family)) {
        log.info(DISABLED_FAMILY_MSG, family, profiles, family, family);
      }
    }
  }
}
