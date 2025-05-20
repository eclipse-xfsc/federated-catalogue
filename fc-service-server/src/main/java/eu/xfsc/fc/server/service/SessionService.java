package eu.xfsc.fc.server.service;

import static eu.xfsc.fc.server.util.SessionUtils.getSessionUserId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import eu.xfsc.fc.api.generated.model.Session;
import eu.xfsc.fc.core.dao.SessionDao;
import eu.xfsc.fc.server.generated.controller.SessionApiDelegate;
import eu.xfsc.fc.server.util.SessionUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the {@link eu.xfsc.fc.server.generated.controller.SessionApiDelegate} interface.
 */
@Slf4j
@Service
public class SessionService implements SessionApiDelegate {

  @Autowired
  private SessionDao ssnDao;

  /**
   * GET /session : Get current User session details.
   *
   * @return current User session (status code 200)
   *         or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *         or May contain hints how to solve the error or indicate what went wrong at the server.
   *         Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<Session> getCurrentSession() {
    String userId = getSessionUserId();
    log.debug("getCurrentSession.enter; got userId: {}", userId);
    Session ssn = ssnDao.select(userId);
    log.debug("getCurrentSession.exit; returning session: {}", ssn);
    return ResponseEntity.ok(ssn);
  }
  
  /**
   * DELETE /session : Logout for current session.
   */
  @Override
  public ResponseEntity<Void> logoutCurrentSession() {
    String userId = getSessionUserId();
    log.debug("logoutCurrentSession.enter; got userId: {}", userId);
    ssnDao.delete(userId);
    SessionUtils.logoutSessionUser();
    log.debug("logoutCurrentSession.exit;");
    return ResponseEntity.ok(null);
  }
}
