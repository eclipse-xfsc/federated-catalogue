package eu.xfsc.fc.demo.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import eu.xfsc.fc.client.SessionClient;

/**
 * Tests for the SessionController /ssn/me endpoint.
 */
@WebMvcTest(SessionController.class)
class SessionControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private SessionClient sessionClient;

  @MockitoBean
  private ClientRegistrationRepository clientRegistrationRepository;

  @Test
  void getCurrentUser_withPreferredUsername_returnsPreferredUsername() throws Exception {
    OidcUser oidcUser = buildOidcUser(Map.of(
        "preferred_username", "admin-user",
        "sub", "user-id-1"
    ));

    mockMvc.perform(get("/ssn/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("admin-user"));
  }

  @Test
  void getCurrentUser_withoutPreferredUsername_returnsFullName() throws Exception {
    OidcUser oidcUser = buildOidcUser(Map.of(
        "name", "Test User",
        "sub", "user-id-2"
    ));

    mockMvc.perform(get("/ssn/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("Test User"));
  }

  @Test
  void getCurrentUser_withoutUsernameOrName_returnsEmail() throws Exception {
    OidcUser oidcUser = buildOidcUser(Map.of(
        "email", "test@example.com",
        "sub", "user-id-3"
    ));

    mockMvc.perform(get("/ssn/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("test@example.com"));
  }

  @Test
  void getCurrentUser_withNoClaims_returnsFallback() throws Exception {
    OidcUser oidcUser = buildOidcUser(Map.of(
        "sub", "user-id-4"
    ));

    mockMvc.perform(get("/ssn/me").with(oidcLogin().oidcUser(oidcUser)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.displayName").value("User"));
  }

  @Test
  void getCurrentUser_unauthenticated_returns302() throws Exception {
    mockMvc.perform(get("/ssn/me"))
        .andExpect(status().is3xxRedirection());
  }

  private OidcUser buildOidcUser(Map<String, Object> claims) {
    OidcIdToken idToken = new OidcIdToken(
        "mock-token",
        Instant.now(),
        Instant.now().plusSeconds(3600),
        claims
    );
    return new DefaultOidcUser(Collections.emptyList(), idToken);
  }
}
