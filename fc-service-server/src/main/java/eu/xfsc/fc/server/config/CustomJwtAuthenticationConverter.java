package eu.xfsc.fc.server.config;

import static eu.xfsc.fc.server.util.CommonConstants.*;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import lombok.extern.slf4j.Slf4j;

/**
 * Converter provides type conversion for custom jwt claim values.
 */
@Slf4j
public class CustomJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
	
  private final String resourceId;
  private final JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

  /**
   * Constructs a new Converter with the specified resourceId.
   *
   * @param resourceId Keycloak client id.
   */
  public CustomJwtAuthenticationConverter(String resourceId) {
    this.resourceId = resourceId;
  }

  /**
   * Convert user jwt token to JwtAuthenticationToken with all user authorities.
   *
   * @param source User authentication token.
   * @return JwtAuthenticationToken with all user authorities.
   */
  @Override
  public AbstractAuthenticationToken convert(final Jwt source) {
    log.debug("convert.enter; subject: {}", source.getSubject());
    Collection<GrantedAuthority> authorities = jwtGrantedAuthoritiesConverter.convert(source);
    Collection<GrantedAuthority> roles = extractResourceRoles(source);
    roles.addAll(authorities);
    if (log.isDebugEnabled()) {
      log.debug("convert.exit; subject: {}, authorities: {}", source.getSubject(),
          roles.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toList()));
    }
    return new JwtAuthenticationToken(source, roles);
  }

  /**
   * Extract all user authorities.
   *
   * @param jwt User authentication token.
   * @return Collection of user authorities.
   */
  @SuppressWarnings("unchecked")
  private Collection<GrantedAuthority> extractResourceRoles(final Jwt jwt) {
    Collection<GrantedAuthority> authorities = new HashSet<>();
    Map<String, Object> resourceAccess = jwt.getClaim("resource_access");

    if (resourceAccess != null) {
      Map<String, Object> resourceData = (Map<String, Object>) resourceAccess.get(resourceId);
      if (resourceData == null) {
        // resource_access is present but carries no entry for our configured client id: this is a
        // misconfiguration (wrong keycloak.resource, or the client/audience mapping is off), not a
        // "no roles granted" case. Every role-gated endpoint would otherwise 403 with no diagnostic
        // anywhere, so warn loudly instead of silently dropping to an empty authority set. We do NOT
        // fall back to the legacy top-level "roles" claim here — resource_access being present at all
        // means the issuer is on the token-scoped-client-roles model, and reading a legacy realm-level
        // claim in that case would widen who can obtain catalogue-admin authority for no benefit.
        log.warn("extractResourceRoles: resource_access claim present but has no entry for configured "
                + "resource id '{}'; clients present in token: {}.", resourceId, resourceAccess.keySet());
        return authorities;
      }
      Collection<String> roles = (Collection<String>) resourceData.get("roles");
      if (roles != null) {
        roles.forEach(x -> authorities.add(new SimpleGrantedAuthority(PREFIX + x)));
      }
      return authorities;
    }

    // resource_access entirely absent — fall back to the legacy realm-level "roles" claim.
    Collection<String> legacyRoles = jwt.getClaim("roles");
    if (legacyRoles != null) {
      legacyRoles.forEach(x -> {
        if ("gaia-x-admin".equals(x)) {
          authorities.add(new SimpleGrantedAuthority(CATALOGUE_ADMIN_ROLE_WITH_PREFIX));
        } else if ("gaia-x-notar".equals(x)) {
          authorities.add(new SimpleGrantedAuthority(PARTICIPANT_ADMIN_ROLE_WITH_PREFIX));
        } else if ("gaia-x-business-owner".equals(x)) {
          authorities.add(new SimpleGrantedAuthority(PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX));
        }
      });
    }
    return authorities;
  }

}