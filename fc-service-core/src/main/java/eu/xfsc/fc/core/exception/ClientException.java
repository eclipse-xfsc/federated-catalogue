package eu.xfsc.fc.core.exception;

import lombok.Getter;
import lombok.Setter;

/**
 * ClientException is an exception that can be thrown to customize the Bad request error of the Federated
 * Catalogue server application.
 * Implementation of the {@link ServiceException} exception.
 */
public class ClientException extends ServiceException {
  /**
   * Constructs a new Client Exception with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public ClientException(String message) {
    super(message);
  }
  
  public ClientException(String message, Throwable cause) {
    super(message, cause);
  }
  
}