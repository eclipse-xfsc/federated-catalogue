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
	log.info("convert.enter; got JWT: {}", source);
    Collection<GrantedAuthority> authorities = jwtGrantedAuthoritiesConverter.convert(source);
	Collection<GrantedAuthority> roles = extractResourceRoles(source);
    roles.addAll(authorities);
	log.info("convert.exit; extracted roles: {}", roles);
    return new JwtAuthenticationToken(source, roles);
  }

  /**
   * Extract all user authorities.
   *
   * @param jwt User authentication token.
   * @param resourceId Keycloak client id.
   * @return Collection of user authorities.
   */
  @SuppressWarnings("unchecked")
  private Collection<GrantedAuthority> extractResourceRoles(final Jwt jwt) {
    Collection<GrantedAuthority> authorities = new HashSet<>(); 
    Collection<String> roles = null;
    Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
    if (resourceAccess != null) {
      Map<String, Object> resourceData = (Map<String, Object>) resourceAccess.get(resourceId);
      if (resourceData != null) {
        roles = (Collection<String>) resourceData.get("roles");
        if (roles != null) {
          roles.stream().forEach(x -> authorities.add(new SimpleGrantedAuthority(PREFIX + x)));
        } 
      }
    } else {
      roles = jwt.getClaim("roles");
      if (roles != null) {
        roles.stream().forEach(x -> {
        	if ("gaia-x-admin".equals(x)) {
              authorities.add(new SimpleGrantedAuthority(CATALOGUE_ADMIN_ROLE_WITH_PREFIX));
            } else if ("gaia-x-notar".equals(x)) {
              authorities.add(new SimpleGrantedAuthority(PARTICIPANT_ADMIN_ROLE_WITH_PREFIX));	
            } else if ("gaia-x-business-owner".equals(x)) {
        	  authorities.add(new SimpleGrantedAuthority(PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX));
            }
        });
      }
    }
    return authorities;
  }

}