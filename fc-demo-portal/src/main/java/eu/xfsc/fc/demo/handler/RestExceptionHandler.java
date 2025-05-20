package eu.xfsc.fc.demo.handler;

import eu.xfsc.fc.api.generated.model.Error;
import java.net.ConnectException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * RestExceptionHandler translates RestExceptions to error responses according to the status
 * that is set in the application exception. Incoming response content format:
 * {"code" : "ExceptionType", "message" : "some exception message"}.
 * Implementation of the {@link ResponseEntityExceptionHandler} exception.
 */
@ControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {
  /**
   * Method handles the Connection Exception.
   *
   * @param request Provides access to request parameters methods.
   * @return The custom Federated Catalogue Demo application error with status code 500.
   */
  @ExceptionHandler({ConnectException.class})
  public ResponseEntity<Error> connectionRefused(HttpServletRequest request) {
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new Error(request.getRequestURI(), "Connection error. Please, try later"));
  }

  /**
   * Method handles the Connection Exception.
   *
   * @param ex Web Client Response Exception.
   * @param request Provides access to request parameters methods.
   * @return The custom Federated Catalogue application error with incoming status code.
   */
  @ExceptionHandler({WebClientResponseException.class})
  public ResponseEntity<Object> handleWebClientResponseException(WebClientResponseException ex, WebRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return handleExceptionInternal(ex, ex.getResponseBodyAsString(), headers, ex.getStatusCode(), request);
  }
}
