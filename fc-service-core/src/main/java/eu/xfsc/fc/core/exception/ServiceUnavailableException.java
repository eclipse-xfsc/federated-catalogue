package eu.xfsc.fc.core.exception;

/**
 * Thrown when an external service required to fulfil a request is currently unreachable.
 * Maps to HTTP 503 Service Unavailable.
 */
public class ServiceUnavailableException extends ServiceException {

  public ServiceUnavailableException(String message) {
    super(message);
  }

  public ServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
