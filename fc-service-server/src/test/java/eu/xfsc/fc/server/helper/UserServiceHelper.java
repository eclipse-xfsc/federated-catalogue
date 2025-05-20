package eu.xfsc.fc.server.helper;

import static eu.xfsc.fc.server.util.CommonConstants.CATALOGUE_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.PARTICIPANT_USER_ADMIN_ROLE;
import static eu.xfsc.fc.server.util.CommonConstants.SD_ADMIN_ROLE;

import java.util.ArrayList;
import java.util.List;
import org.keycloak.representations.idm.RoleRepresentation;

public class UserServiceHelper {
  public static List<RoleRepresentation> getAllRoles() {
    List<RoleRepresentation> roles = new ArrayList<>();
    roles.add(new RoleRepresentation(SD_ADMIN_ROLE, SD_ADMIN_ROLE, false));
    roles.add(new RoleRepresentation(CATALOGUE_ADMIN_ROLE, CATALOGUE_ADMIN_ROLE, false));
    roles.add(new RoleRepresentation(PARTICIPANT_ADMIN_ROLE, PARTICIPANT_ADMIN_ROLE, false));
    roles.add(new RoleRepresentation(PARTICIPANT_USER_ADMIN_ROLE, PARTICIPANT_USER_ADMIN_ROLE, false));
    return roles;
  }
}
