package eu.xfsc.fc.demo.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import eu.xfsc.fc.client.AdminClient;
import eu.xfsc.fc.demo.config.SecurityConfig;

/**
 * Security tests for the AdminController proxy endpoints.
 * Verifies that /admin/** requires authentication. Role enforcement is done by the backend.
 */
@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
class AdminControllerTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private AdminClient adminClient;

  @MockitoBean
  private ClientRegistrationRepository clientRegistrationRepository;

  @Test
  void getAdminStats_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/admin/stats"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void getAdminHealth_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/admin/health"))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void getTrustFrameworks_unauthenticated_redirectsToLogin() throws Exception {
    mockMvc.perform(get("/admin/trust-frameworks"))
        .andExpect(status().is3xxRedirection());
  }
}
