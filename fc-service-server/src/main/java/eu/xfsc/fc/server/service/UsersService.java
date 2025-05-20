package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX;
import static eu.xfsc.fc.server.util.CommonConstants.SD_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.SessionUtils.checkParticipantAccess;
import static eu.xfsc.fc.server.util.SessionUtils.getSessionUserId;
import static eu.xfsc.fc.server.util.SessionUtils.getSessionUserRoles;

import java.net.URI;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import eu.xfsc.fc.api.generated.model.User;
import eu.xfsc.fc.api.generated.model.UserProfile;
import eu.xfsc.fc.api.generated.model.UserProfiles;
import eu.xfsc.fc.core.dao.ParticipantDao;
import eu.xfsc.fc.core.dao.UserDao;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.server.generated.controller.UsersApiDelegate;
import eu.xfsc.fc.server.util.SessionUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for the crud operation of the user . Implementation of the {@link UsersApiDelegate }.
 */
@Slf4j
@Service
public class UsersService implements UsersApiDelegate {

  @Autowired
  private UserDao userDao;

  @Autowired
  private ParticipantDao partDao;

  /**
   * Service method for  register a new user to the associated participant in the catalogue.
   *
   * @param user User entity to be added {@link User}
   * @return Created User profile (status code 201)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *        or HTTP Conflict 409 (status code 409)
   *        or May contain hints how to solve the error or indicate what went wrong at the server. Must not outline
   *        any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<UserProfile> addUser(User user) {
    log.debug("addUser.enter; got user: {}", user);
    if (ObjectUtils.isEmpty(user) || hasEmptyRequiredFields(user)) {
      throw new ClientException("User cannot be empty or have empty field values, except for the role!");
    }
    checkParticipantAccess(user.getParticipantId());
    checkRoleAssignmentAccess(user.getRoleIds(), null);
    UserProfile profile = userDao.create(user);
    log.debug("addUser.exit; returning: {}", profile);
    return ResponseEntity.created(URI.create("/users/" + profile.getId())).body(profile);
  }

  /**
   * Service method for update the user profile.
   *
   * @param userId user id of the user(required)
   * @param user   User entity to be updated {@link User}
   * @return Updated user profile (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *        or The specified resource was not found (status code 404)
   *        or May contain hints how to solve the error or indicate what went wrong at the server. Must not outline
   *        any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<UserProfile> updateUser(String userId, User user) {
    log.debug("updateUser.enter; got userId: {}", userId);
    if (ObjectUtils.isEmpty(user) || hasEmptyRequiredFields(user)) {
      throw new ClientException("User cannot be empty or have empty field values, except for the role!");
    }
    UserProfile profile = userDao.select(userId);
    checkParticipantAccess(profile.getParticipantId());
    checkRoleAssignmentAccess(user.getRoleIds(), userId);
    profile = userDao.update(userId, user);
    log.debug("updateUser.exit; returning: {}", profile);
    return ResponseEntity.ok(profile);
  }

  /**
   * Service method for delete a user.
   *
   * @param userId user id of the user(required)
   * @return Deleted user profile (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *        or The specified resource was not found (status code 404)
   *        or HTTP Conflict 409 (status code 409)
   *        or May contain hints how to solve the error or indicate what went wrong at the server. Must not outline
   *        any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<UserProfile> deleteUser(String userId) {
    log.debug("deleteUser.enter; got userId: {}", userId);
    UserProfile profile = userDao.select(userId);
    checkParticipantAccess(profile.getParticipantId());

    //last participant-admin-user cannot deleted
    // weird code, con't understand how it works..
    PaginatedResults<UserProfile> profiles = userDao.search(profile.getParticipantId(), 0, 100);
    Long participantAdminCount = profiles.getResults().stream()
        .filter(userProfile -> userProfile.getRoleIds().contains(PARTICIPANT_ADMIN_ROLE)).count();
    log.debug("deleteUser; total count of participant  admin is : {}", participantAdminCount);
    if (participantAdminCount == 1) {
        throw new ConflictException("Last participant admin cannot be deleted");
    }
    profile = userDao.delete(userId);
    log.debug("deleteUser.exit; returning: {}", profile);
    return ResponseEntity.ok(profile);
  }

  /**
   * Service method for get the user profile.
   *
   * @param userId user id of the user(required)
   * @return User profile (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *        or The specified resource was not found (status code 404)
   *        or May contain hints how to solve the error or indicate what went wrong at the server. Must not outline
   *        any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<UserProfile> getUser(String userId) {
    log.debug("getUser.enter; got userId: {}", userId);
    UserProfile profile = userDao.select(userId);
    checkParticipantAccess(profile.getParticipantId());
    log.debug("getUser.exit; returning: {}", profile);
    return ResponseEntity.ok(profile);
  }

  /**
   * Service method for list the registered users.
   *
   * @param offset    The number of items to skip before starting to collect the result set. (optional, default to 0)
   * @param limit     The number of items to return. (optional, default to 100)
   * @return List of usernames (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or May contain hints how to solve the error or indicate what went wrong at the server. Must not outline
   *        any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<UserProfiles> getUsers(Integer offset, Integer limit) { //String orderBy, Boolean ascending) {
    // sorting is not supported yet by keycloak admin API
    log.debug("getUsers.enter; got offset: {}, limit: {}", offset, limit);
    PaginatedResults<UserProfile> profiles ;
    if (SessionUtils.sessionUserHasRole(CATALOGUE_ADMIN_ROLE_WITH_PREFIX)) {
       profiles = userDao.search(null, offset, limit);
    } else {
     profiles = partDao.selectUsers(SessionUtils.getSessionParticipantId(), offset, limit).get();
    }
    log.debug("getUsers.exit; returning: {}", profiles);
    return ResponseEntity.ok(new UserProfiles((int) profiles.getTotalCount(), profiles.getResults()));
  }

  /**
   * Service method for the  get roles of the user.
   *
   * @param userId user id of the user(required)
   * @return User roles (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *        or The specified resource was not found (status code 404)
   *        or May contain hints how to solve the error or indicate what went wrong at the server. Must not outline
   *        any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<List<String>> getUserRoles(String userId) {
    log.debug("getUserRoles.enter; got userId: {}", userId);
    UserProfile profile = userDao.select(userId);
    checkParticipantAccess(profile.getParticipantId());
    log.debug("getUserRoles.exit; returning: {}", profile.getRoleIds());
    return ResponseEntity.ok(profile.getRoleIds());
  }

  /**
   * Service method for the update roles of the user.
   *
   * @param userId user id of the user (required)
   * @param roles  List of roles which should be assigned to the user
   * @return All assigned roles of the user (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or Forbidden. The user does not have the permission to execute this request. (status code 403)
   *        or The specified resource was not found (status code 404)
   *        or May contain hints how to solve the error or indicate what went wrong at the server. Must not outline
   *        any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<UserProfile> updateUserRoles(String userId, List<String> roles) {
    log.debug("updateUserRoles.enter; got userId: {}, roles: {}", userId, roles);
    UserProfile profile = userDao.select(userId);
    checkParticipantAccess(profile.getParticipantId());
    checkRoleAssignmentAccess(roles, userId);
    profile = userDao.updateRoles(userId, roles);
    log.debug("updateUserRoles.exit; returning: {}", profile);
    return ResponseEntity.ok(profile);
  }

  private boolean hasEmptyRequiredFields(User user) {
    return StringUtils.isBlank(user.getParticipantId()) || StringUtils.isBlank(user.getEmail())
        || StringUtils.isBlank(user.getFirstName()) || StringUtils.isBlank(user.getLastName());
  }

  /**
   * Check user role Assignment.
   *
   * @param newRoles updated new roles
   * @param userId user of the user for which the roles are updated
   */
  private void  checkRoleAssignmentAccess(List<String> newRoles, String userId) {
    log.debug("checkRoleAssignmentAccess.enter; got  newRoles: {}, userId :{}", newRoles, userId);
    List<String> sessionUserRoles = getSessionUserRoles();
    newRoles.stream().forEach(roleId -> doCheckRoleAssignmentRule(sessionUserRoles, roleId, userId));
    log.debug("checkRoleAssignmentAccess.exit;");
  }

  /**
   * Logic to check role assignment.
   *
   * @param  sessionUserRoles session user roles
   * @param roleToUpdate new role to add
   * @param userId user of the user for which the roles are updated
   */
  private void doCheckRoleAssignmentRule(List<String> sessionUserRoles, String roleToUpdate,  String userId) {

    switch (roleToUpdate) {

      case CATALOGUE_ADMIN_ROLE:

        if (!sessionUserRoles.contains(CATALOGUE_ADMIN_ROLE_WITH_PREFIX)) {
          log.debug("doCheckRoleAssignmentRule.fails for assigning role :{};",CATALOGUE_ADMIN_ROLE );
          throwAccessDeniedException(CATALOGUE_ADMIN_ROLE);
        }

        break;

      case PARTICIPANT_ADMIN_ROLE:

        if (!sessionUserRoles.stream()
            .anyMatch(List.of(CATALOGUE_ADMIN_ROLE_WITH_PREFIX, PARTICIPANT_ADMIN_ROLE_WITH_PREFIX)::contains)) {
          log.debug("doCheckRoleAssignmentRule.fails for assigning role :{};",PARTICIPANT_ADMIN_ROLE );
          throwAccessDeniedException(PARTICIPANT_ADMIN_ROLE);
        }
        break;

      case SD_ADMIN_ROLE:

        if (!(sessionUserRoles.stream()
            .anyMatch(List.of(CATALOGUE_ADMIN_ROLE_WITH_PREFIX, PARTICIPANT_ADMIN_ROLE_WITH_PREFIX,
                PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX)::contains))
            || !(sessionUserRoles.stream()
            .anyMatch(List.of(CATALOGUE_ADMIN_ROLE_WITH_PREFIX, PARTICIPANT_ADMIN_ROLE_WITH_PREFIX)::contains))
            && (sessionUserRoles.contains(PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX) && (userId == null || userId.equals(getSessionUserId())))
        ) {
          log.debug("doCheckRoleAssignmentRule.fails for assigning role :{};",SD_ADMIN_ROLE );
          throwAccessDeniedException(SD_ADMIN_ROLE);
        }

        break;

      case PARTICIPANT_USER_ADMIN_ROLE:

        if (!sessionUserRoles.stream()
            .anyMatch(List.of(CATALOGUE_ADMIN_ROLE_WITH_PREFIX, PARTICIPANT_ADMIN_ROLE_WITH_PREFIX,
                PARTICIPANT_USER_ADMIN_ROLE_WITH_PREFIX)::contains)) {
          log.debug("doCheckRoleAssignmentRule.fails for assigning role :{};",PARTICIPANT_USER_ADMIN_ROLE );
          throwAccessDeniedException(PARTICIPANT_USER_ADMIN_ROLE);
        }

    }

  }

  /**
   * Throws exception for invalid user role access.
   */
  private void throwAccessDeniedException(String role){
    throw new AccessDeniedException("The user does not have permission to add "+role+" role to user.");
  }
}
