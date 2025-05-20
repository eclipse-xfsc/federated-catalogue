package eu.xfsc.fc.core.exception;

/**
 * VerificationException is an exception that is thrown when the validation of a SD fails
 * Implementation of the {@link ServiceException} exception.
 */

public class VerificationException extends ServiceException {
  /**
   * Constructs a new Verification exception with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public VerificationException(String message) {
    super(message);
  }

  /**
   * Constructs a new Verification exception with the specified detail message and cause.
   *
   * @param message Detailed message about the thrown exception.
   * @param cause Case of the thrown exception. (A null value is permitted, and indicates that the cause is unknown.)
   */
  public VerificationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Constructs a new Verification exception with the specified cause and a detail message of case.
   *
   * @param cause Case of the thrown exception. (A null value is permitted, and indicates that the cause is unknown.)
   */
  public VerificationException(Throwable cause) {
    super(cause);
  }
}