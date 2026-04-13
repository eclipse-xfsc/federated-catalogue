package eu.xfsc.fc.demo.controller;

import java.util.Map;
import java.util.stream.Stream;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import eu.xfsc.fc.api.generated.model.Session;
import eu.xfsc.fc.client.SessionClient;
import lombok.RequiredArgsConstructor;

/**
 * Controller for session management and current user info.
 */
@RestController
@RequestMapping("ssn")
@RequiredArgsConstructor
public class SessionController {

  private final SessionClient ssnClient;

  @GetMapping
  public Session getSession() {
    return ssnClient.getCurrentSession();
  }

  /**
   * Get current user display name from OIDC principal.
   *
   * @param principal OIDC user from the security context.
   * @return Map with displayName.
   */
  @GetMapping("/me")
  public Map<String, String> getCurrentUser(@AuthenticationPrincipal OidcUser principal) {
    String name = Stream.of(
            principal.getPreferredUsername(),
            principal.getFullName(),
            principal.getEmail())
        .filter(s -> s != null && !s.isBlank())
        .findFirst()
        .orElse("User");
    return Map.of("displayName", name);
  }

  @DeleteMapping
  public void dropSession() {
    ssnClient.deleteCurrentSession();
  }
}
