package eu.xfsc.fc.core.pojo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Accessor class for passing asset or schema content. Implementations
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

  /**
   * Returns the content as a byte array. Default implementation
   * converts from string, which is correct for text-based content.
   * Binary implementations should override this.
   *
   * @return the content as a byte array
   */
  default byte[] getContentAsBytes() {
    return getContentAsString().getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Returns the HTTP Content-Type associated with this content, or {@code null} if unknown.
   * Implementations that carry content-type information should override this method.
   */
  default String getContentType() {
    return null;
  }

}
