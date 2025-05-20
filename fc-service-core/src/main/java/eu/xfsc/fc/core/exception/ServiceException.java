package eu.xfsc.fc.core.exception;

/**
 * ServiceException is the main exception that can be thrown during the operations of Federated Catalogue
 * server application.
 * Implementation of the {@link RuntimeException} exception.
 */
public class ServiceException extends RuntimeException {
  /**
   * Constructs a new Service Exception with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public ServiceException(String message) {
    super(message);
  }

  /**
   * Constructs a new Service exception with the specified detail message and cause.
   *
   * @param message Detailed message about the thrown exception.
   * @param cause Case of the thrown exception. (A null value is permitted, and indicates that the cause is unknown.)
   */
  public ServiceException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new Service exception with the specified cause and a detail message of case.
   *
   * @param cause Case of the thrown exception. (A null value is permitted, and indicates that the cause is unknown.)
   */
  public ServiceException(Throwable cause) {
    super(cause);
  }
}
