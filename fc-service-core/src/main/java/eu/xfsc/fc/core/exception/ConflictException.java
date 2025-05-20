package eu.xfsc.fc.core.exception;

/**
 * ConflictException is an exception that can be thrown to customize the KeyCloak conflicts error of the Federated
 * Catalogue server application.
 * Implementation of the {@link ServiceException} exception.
 */
public class ConflictException extends ServiceException {
  /**
   * Constructs a new Conflict Exception with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public ConflictException(String message) {
    super(message);
  }

  /**
   * Constructs a new Conflict exception with the specified detail message and cause.
   *
   * @param message Detailed message about the thrown exception.
   * @param cause Case of the thrown exception. (A null value is permitted, and indicates that the cause is unknown.)
   */
  public ConflictException(String message, Throwable cause) {
    super(message, cause);
  }
}
