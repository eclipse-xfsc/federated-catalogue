package eu.xfsc.fc.core.exception;

/**
 * Thrown when an external service required to fulfil a request was reached but responded with
 * an error, as opposed to {@link ServiceUnavailableException}'s connection-level meaning of
 * "could not be reached at all". A subtype of {@link ServiceUnavailableException} so it still
 * maps to HTTP 503 Service Unavailable without a new exception handler; callers that need the
 * finer distinction (e.g. audit-trail categorisation) can match on this type specifically.
 */
public class ServiceErrorException extends ServiceUnavailableException {

  public ServiceErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
