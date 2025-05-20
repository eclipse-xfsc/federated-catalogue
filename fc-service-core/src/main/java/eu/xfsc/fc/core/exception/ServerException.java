package eu.xfsc.fc.core.exception;

/**
 * ServerException is an exception that can be thrown to customize the Internal server error of the Federated
 * Catalogue server application.
 * Implementation of the {@link ServiceException} exception.
 */
public class ServerException extends ServiceException {
  /**
   * Constructs a new Server Exception with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public ServerException(String message) {
    super(message);
  }

  public ServerException(Throwable cause) {
    super(cause);
  }

  /**
   * Constructs a new Server exception with the specified detail message and cause.
   *
   * @param message Detailed message about the thrown exception.
   * @param cause Case of the thrown exception. (A null value is permitted, and indicates that the cause is unknown.)
   */
  public ServerException(String message, Throwable cause) {
    super(message, cause);
  }
}