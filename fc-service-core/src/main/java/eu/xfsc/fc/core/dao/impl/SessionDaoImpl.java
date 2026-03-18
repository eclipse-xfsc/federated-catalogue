package eu.xfsc.fc.core.dao.impl;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserSessionRepresentation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.api.generated.model.Session;
import eu.xfsc.fc.core.dao.SessionDao;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SessionDaoImpl implements SessionDao {
    
    @Value("${keycloak.realm}")
    private String realm;
    @Autowired
    private Keycloak keycloak;
    

    @Override
    public Session select(String id) {
        UserResource user = keycloak.realm(realm).users().get(id);
        List<UserSessionRepresentation> sessions = user.getUserSessions();
        log.debug("select; got sessions: {}", sessions);
        if (sessions != null && !sessions.isEmpty()) {
            UserSessionRepresentation ssn = sessions.getFirst();
            log.debug("select; session {} of {}, started at: {}, last accessed at: {}, from: {}", 
                    ssn.getId(), ssn.getUsername(), ssn.getStart(), ssn.getLastAccess(), ssn.getIpAddress());
            java.util.Date start = new java.util.Date(ssn.getStart());
            Instant started = start.toInstant(); 
            List<RoleRepresentation> realmMappings = user.roles().getAll().getRealmMappings();
            List<String> roles = realmMappings != null
                ? realmMappings.stream().map(RoleRepresentation::getName).collect(Collectors.toList())
                : Collections.emptyList();
            return new Session(ssn.getUserId(), started, "ACTIVE", roles);
        }
        return null;
    }

    @Override
    public void delete(String id) {
        // keycloak.realm(realm).deleteSession(id);
         keycloak.realm(realm).users().get(id).logout();
    }

}
