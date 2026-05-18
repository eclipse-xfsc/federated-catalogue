package eu.xfsc.fc.server.service;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

import eu.xfsc.fc.api.generated.model.VerificationResult;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.server.generated.controller.VerificationApiDelegate;
import lombok.extern.slf4j.Slf4j;

/**
 * Implementation of the {@link eu.xfsc.fc.server.generated.controller.VerificationApiDelegate} interface.
 */
@Slf4j
@Service
public class VerificationService implements VerificationApiDelegate {

  @Autowired
  private eu.xfsc.fc.core.service.verification.VerificationService verificationService;

  @Autowired
  private ResourceLoader resourceLoader;


  /**
   * Service method for POST /verification: Send a JSON-LD document to verify with the information
   * from the Catalogue.
   *
   * @param body JSON-LD credential to be verified.
   * @param framework optional trust-framework bundle identifier for disambiguation; ignored when
   *                  exactly one bundle is active.
   * @return Verification result (status code 200)
   *       or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *       or Query Timeout: the query took longer than the configured timeout interval.
   *       The client needs to rewrite the query so it can be processed faster. (status code 408)
   *       or May contain hints how to solve the error or indicate what went wrong at the server.
   *       Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<VerificationResult> verify(Boolean verifySemantics, Boolean verifySchema,
                                                   Boolean verifyVPSignature, Boolean verifyVCSignature,
                                                   String framework, String body) {
    log.debug(
        "verify.enter; got body of length: {}; verify semantics: {}, schema: {}, vp-signature: {}, vc-signature: {}, framework: {}",
        body.length(), verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature, framework);
    VerificationResult verificationResult = verificationService.verifyCredential(new ContentAccessorDirect(body),
            verifySemantics, verifySchema, verifyVPSignature, verifyVCSignature);
    log.debug("verify.exit; returning result: {}", verificationResult);
    return ResponseEntity.ok(verificationResult);

  }

  /**
   * Service method for GET /verification: Show HTML page to verify (portions of) a signed credential.
   *
   * @return HTML document that contains a query field to verify credentials. (status code 200)
   *        or May contain hints how to solve the error or indicate what was wrong in the request. (status code 400)
   *        or May contain hints how to solve the error or indicate what went wrong at the server.
   *        Must not outline any information about the internal structure of the server. (status code 500)
   */
  @Override
  public ResponseEntity<String> verifyPage() {
    log.debug("verifyPage.enter");

    final Resource resource = resourceLoader.getResource("classpath:static/verification.html");
    String page;
    try {
      Reader reader = new InputStreamReader(resource.getInputStream());
      page = FileCopyUtils.copyToString(reader);
    } catch (IOException e) {
      log.error("error in getting file: {}", e);
      throw new ServerException(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase());
    }
    HttpHeaders responseHeaders = new HttpHeaders();
    responseHeaders.set("Content-Type", "text/html");
    log.debug("verifyPage.exit; returning page");
    return ResponseEntity.ok()
        .headers(responseHeaders)
        .body(page);

  }

}
