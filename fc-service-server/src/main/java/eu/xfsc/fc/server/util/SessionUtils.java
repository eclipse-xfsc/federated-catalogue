package eu.xfsc.fc.server.util;

import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Utility class with static methods for getting data from the active user session.
 */
@Slf4j
public class SessionUtils {
  /**
   * Public static method to get Participant ID from the active user session.
   *
   * @return Returns either the Participant ID or null if the user session doesn't contain Participant ID attribute.
   */
  public static String getSessionParticipantId() {
    String participantId = null;
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof Jwt) {
      participantId = ((Jwt) principal).getClaim("participant_id");
    }
    log.debug("getSessionParticipantId.exit; got participant id {} from principal: {}", participantId, principal);
    //log.debug("getSessionParticipantId.exit; got roles: {}", ((Jwt) principal).getClaimAsStringList("roles"));
    return participantId;
  }

  /**
   * Public static method to get User ID from the active user session.
   *
   * @return String user Id.
   */
  public static String getSessionUserId() {
    String userId = null;
    Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    if (principal instanceof Jwt) {
      userId = ((Jwt) principal).getSubject();
    }
    log.debug("getSessionUserId.exit; got user id {} from principal: {}", userId, principal);
    return userId;
  }

  /**
   * Public static method to check if a user has a role.
   *
   * @param role role to be checked.
   * @return boolean status.
   */
  public static boolean sessionUserHasRole(String role) {
    Collection<GrantedAuthority> authorities = (Collection<GrantedAuthority>)
        SecurityContextHolder.getContext().getAuthentication().getAuthorities();
    return authorities.stream().anyMatch(authority -> authority.getAuthority().equals(role));
  }


  /**
   * Public static method to session user all roles .
   *
   * @return List<String> roles.
   */
  public static List<String> getSessionUserRoles() {
    List<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities()
                                .stream()
                                .map(authority -> authority.getAuthority()).collect( Collectors.toList());
    return authorities;
  }
  /**
   * Internal service method for checking user access to a particular Participant.
   *
   * @param participantId The Participant issuer of SD (required).
   */
  public static void checkParticipantAccess(String participantId) {
    String sessionParticipantId = SessionUtils.getSessionParticipantId();
    if (!SessionUtils.sessionUserHasRole(CATALOGUE_ADMIN_ROLE_WITH_PREFIX) && (Objects.isNull(participantId)
        || Objects.isNull(sessionParticipantId) || !participantId.equals(sessionParticipantId))) {
      log.debug("checkParticipantAccess; The user does not have access to the specified participant."
          + " User incoming participant id = {}, session participant id = {}.", sessionParticipantId, participantId);
      throw new AccessDeniedException("The user does not have access to the specified participant.");
    }
  }

  /**
   * Clear spring security context.
   *
   */
  public static void logoutSessionUser() {
    SecurityContextHolder.getContext().setAuthentication(null);
    SecurityContextHolder.clearContext();
    log.debug("logoutSessionUser.exit;");
  }
}
