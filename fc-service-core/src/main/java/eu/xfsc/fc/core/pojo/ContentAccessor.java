package eu.xfsc.fc.core.pojo;

import java.io.InputStream;

/**
 * Accessor class for passing SelfDescription or Schema content. Implementations
 * may use lazy-loading to improve memory use.
 */
public interface ContentAccessor {

  /**
   * Returns the content as a string.
   *
   * @return the content as a string.
   */
  String getContentAsString();

  /**
   * Returns the content as a stream.
   *
   * @return the content as a stream
   */
  InputStream getContentAsStream();

}
