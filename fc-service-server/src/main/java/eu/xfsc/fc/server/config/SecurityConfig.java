package eu.xfsc.fc.server.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import eu.xfsc.fc.api.generated.model.Error;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static eu.xfsc.fc.server.util.CommonConstants.ADMIN_ALL;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_CREATE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_DELETE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_READ;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_UPDATE;
import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.QUERY_EXECUTE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_CREATE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_DELETE;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_READ;
import static eu.xfsc.fc.server.util.CommonConstants.SCHEMA_UPDATE;
import static eu.xfsc.fc.server.util.CommonConstants.ASSET_ADMIN_ROLE;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Note: WebSecurity adapter is deprecated in spring security 5.7;
 * so we are using SecurityFilterChain for configuration security without extending deprecated adapter.
 */
@Configuration
@EnableWebSecurity //(debug = true)
//@EnableMethodSecurity
public class SecurityConfig {
  private static final ObjectMapper mapper = new ObjectMapper();

  private static final String COMMON_FORBIDDEN_ERROR_MESSAGE = "User does not have permission to execute this request.";

  private final String resourceId;

  public SecurityConfig(@Value("${keycloak.resource}") String resourceId) {
    this.resourceId = resourceId;
  }

  /**
   * Define security constraints for the application resources.
   */
  // TODO: 13.07.2022 Need to add access by scopes and by access to the participant.
  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

    http
      //.csrf().disable()
      .authorizeHttpRequests(authorization -> authorization
          .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
          .requestMatchers(HttpMethod.GET, "/api/**").permitAll()
          .requestMatchers(HttpMethod.GET, "/swagger-ui/**").permitAll()
          .requestMatchers(HttpMethod.GET, "/actuator", "/actuator/**").permitAll()
          .requestMatchers(HttpMethod.GET, "/js/**", "/css/**").permitAll()

          // Schema APIs
          .requestMatchers(HttpMethod.POST, "/schemas").hasAnyRole(SCHEMA_CREATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.DELETE, "/schemas/**").hasAnyRole(SCHEMA_DELETE, ADMIN_ALL)
          .requestMatchers(HttpMethod.PUT, "/schemas", "/schemas/**").hasAnyRole(SCHEMA_UPDATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.GET, "/schemas", "/schemas/**").hasAnyRole(SCHEMA_READ, ADMIN_ALL)

          // Query APIs
          .requestMatchers("/query", "/query/**").hasAnyRole(QUERY_EXECUTE, ADMIN_ALL)

          // Verification APIs
          .requestMatchers("/verification").permitAll()
          
          // Asset APIs
          .requestMatchers(HttpMethod.PUT, "/assets/*").hasAnyRole(ASSET_UPDATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.POST, "/assets/*/versions/*/revoke").hasAnyRole(ASSET_UPDATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.GET, "/assets/*/versions").hasAnyRole(ASSET_READ, ADMIN_ALL)
          .requestMatchers(HttpMethod.POST, "/assets/*/revoke").hasAnyRole(ASSET_UPDATE, ADMIN_ALL)
          // Asset-linking sub-resource endpoints — must appear before the broader /assets/* GET matcher
          .requestMatchers(HttpMethod.POST, "/assets/*/human-readable").hasAnyRole(ASSET_CREATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.PUT, "/assets/*/human-readable").hasAnyRole(ASSET_UPDATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.GET, "/assets/*/human-readable").hasAnyRole(ASSET_READ, ADMIN_ALL)
          .requestMatchers(HttpMethod.GET, "/assets/*/machine-readable").hasAnyRole(ASSET_READ, ADMIN_ALL)
          .requestMatchers(HttpMethod.GET, "/assets/*/validations").hasAnyRole(ASSET_READ, ADMIN_ALL)
          .requestMatchers(HttpMethod.POST, "/assets/*/provenance").hasAnyRole(ASSET_UPDATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.GET, "/assets/*/provenance", "/assets/*/provenance/*").hasAnyRole(ASSET_READ, ADMIN_ALL)
          .requestMatchers(HttpMethod.POST, "/assets/*/provenance/*/verify", "/assets/*/provenance/verify").hasAnyRole(ASSET_UPDATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.GET, "/assets", "/assets/*").hasAnyRole(ASSET_READ, ADMIN_ALL)
          .requestMatchers(HttpMethod.POST, "/assets").hasAnyRole(ASSET_CREATE, ADMIN_ALL)
          .requestMatchers(HttpMethod.DELETE, "/assets/*").hasAnyRole(ASSET_DELETE, ADMIN_ALL)

          // Validation result read APIs
          .requestMatchers(HttpMethod.GET, "/validations/**").hasAnyRole(ASSET_READ, ADMIN_ALL)

          // Participants API
          .requestMatchers(HttpMethod.POST, "/participants").hasRole(CATALOGUE_ADMIN_ROLE)
          .requestMatchers(HttpMethod.GET, "/participants").hasRole(CATALOGUE_ADMIN_ROLE)
          .requestMatchers(HttpMethod.PUT, "/participants/*").hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE)
          .requestMatchers(HttpMethod.DELETE, "/participants/*").hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE)
          .requestMatchers(HttpMethod.GET, "/participants/*")
            	.hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE, ASSET_ADMIN_ROLE, ASSET_READ)
          .requestMatchers(HttpMethod.GET, "/participants/*/users")
            	.hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE)

          // User APIs
          .requestMatchers("/users", "/users/*")
              .hasAnyRole(CATALOGUE_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE)

          // Roles APIs
          .requestMatchers("/roles").authenticated()

          // Session APIs
          .requestMatchers("/session").authenticated()

          // Admin Dashboard APIs
          .requestMatchers(HttpMethod.GET, "/admin/me", "/admin/stats", "/admin/health", "/admin/keycloak-url").hasRole(ADMIN_ALL)

          // Trust Framework Admin APIs
          .requestMatchers(HttpMethod.GET, "/admin/trust-frameworks").hasRole(ADMIN_ALL)
          .requestMatchers(HttpMethod.PUT, "/admin/trust-frameworks/**").hasRole(ADMIN_ALL)

          // Schema Validation Admin APIs
          .requestMatchers(HttpMethod.GET, "/admin/schema-validation").hasRole(ADMIN_ALL)
          .requestMatchers(HttpMethod.PUT, "/admin/schema-validation/**").hasRole(ADMIN_ALL)

          // Graph Database Admin APIs
          .requestMatchers(HttpMethod.GET, "/admin/graph-database").hasRole(ADMIN_ALL)
          .requestMatchers(HttpMethod.POST, "/admin/graph-database/switch").hasRole(ADMIN_ALL)

          // Graph Admin APIs
          .requestMatchers(HttpMethod.POST, "/admin/graph/rebuild").hasRole(ADMIN_ALL)
          .requestMatchers(HttpMethod.GET, "/admin/graph/rebuild/status", "/admin/graph/status").hasRole(ADMIN_ALL)

          // Actuator graph-rebuild
          .requestMatchers(HttpMethod.POST, "/actuator/graph-rebuild").hasRole(ADMIN_ALL)

          .anyRequest().authenticated()
        )
        .exceptionHandling(c -> c.accessDeniedHandler(accessDeniedHandler()))
        .oauth2ResourceServer(c -> c.jwt(jc -> jc.jwtAuthenticationConverter(new CustomJwtAuthenticationConverter(resourceId))));

    return http.build();
  }

  /**
   * Customize Access Denied application exception.
   */
  private static AccessDeniedHandler accessDeniedHandler() {
    return (HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) -> {
      response.setStatus(HttpStatus.FORBIDDEN.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      Error forbiddenError =
          new Error("forbidden_error", accessDeniedException.getMessage().contains("Access is denied")
              ? accessDeniedException.getMessage() : COMMON_FORBIDDEN_ERROR_MESSAGE);
      ObjectWriter ow = mapper.writer().withDefaultPrettyPrinter();
      response.getWriter().write(ow.writeValueAsString(forbiddenError));
    };
  }
}
