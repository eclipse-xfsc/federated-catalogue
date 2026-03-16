package eu.xfsc.fc.core.exception;

/**
 * Exception thrown when query operations are attempted against a disabled graph store.
 */
public class GraphStoreDisabledException extends ServiceException {

  /**
   * Constructs a new GraphStoreDisabledException with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public GraphStoreDisabledException(String message) {
    super(message);
  }
}