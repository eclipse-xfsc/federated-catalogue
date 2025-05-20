package eu.xfsc.fc.core.util;

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Utilities for hashing strings.
 */
public class HashUtils {

  public static final String HASH_REGEX = "^[0-9a-h]{64}$";
  public static final Pattern HASH_PATTERN = Pattern.compile(HASH_REGEX);

  private HashUtils() {
    // Utility class.
  }

  /**
   * Calculates the Sha256 hash of the given data String and returns it as a
   * Hex-String.
   *
   * Example: f60409e0271867824617a5cea2893787d3030be27b01cd172e8fa03a366b1aeb
   *
   *
   * @param data The data to hash.
   * @return The hash of the data as Hex-String: ^[0-9a-f]{64}$ .)
   */
  public static String calculateSha256AsHex(String data) {
    return Hashing.sha256().hashString(data, StandardCharsets.UTF_8).toString();
  }

}
