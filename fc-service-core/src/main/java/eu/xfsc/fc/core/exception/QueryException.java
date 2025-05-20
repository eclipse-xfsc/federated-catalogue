package eu.xfsc.fc.core.exception;

/**
 * This exception is thrown whenever a claim is passed to the
 * graph store that has syntax errors w.r.t. its RDF
 * serialisation, e.g. broken URIs, invalid literals etc.
 */
public class QueryException extends ServiceException {
    
  public QueryException(String msg) {
    super(msg);
  }

  public QueryException(String message, Throwable cause) {
    super(message, cause);
  }

}
