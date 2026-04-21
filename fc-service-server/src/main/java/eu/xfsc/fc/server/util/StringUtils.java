package eu.xfsc.fc.server.util;

import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

/**
 * General-purpose string utilities.
 */
@UtilityClass
public class StringUtils {

  /**
   * Sanitize an untrusted filename from a multipart upload.
   *
   * <p>Strips path separators ({@code /} and {@code \}) to prevent path traversal,
   * removes ASCII control characters that could enable log injection,
   * and truncates to a safe maximum length of 255 characters.</p>
   *
   * @param filename raw value from {@link MultipartFile#getOriginalFilename()}
   * @return sanitized filename, or {@code null} if input is null
   */
  public String sanitizeFilename(String filename) {
    if (filename == null) {
      return null;
    }
    String name = filename.replaceAll("[/\\\\]", "").replaceAll("[\\x00-\\x1F\\x7F]", "");
    return name.length() > 255 ? name.substring(0, 255) : name;
  }
}

