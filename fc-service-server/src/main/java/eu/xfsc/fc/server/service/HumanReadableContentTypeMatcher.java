package eu.xfsc.fc.server.service;

import eu.xfsc.fc.server.config.AssetProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.TreeSet;

/**
 * Decides whether a raw {@code Content-Type} value is admissible for the human-readable
 * companion endpoint. The decision is the intersection of two filters from {@link AssetProperties}:
 * a family-aware allowlist (supporting {@code type/*} wildcards) and an exact-match denylist that
 * overrides the allowlist for entries with script-execution or active-content risk. MIME-parameter
 * suffixes such as {@code charset=…} are stripped before matching.
 */
@Component
@RequiredArgsConstructor
public class HumanReadableContentTypeMatcher {

  private final AssetProperties assetProperties;

  /**
   * @param rawContentType the value of the {@code Content-Type} header on the upload part
   * @return {@code true} when the type passes the allowlist and is not on the denylist
   */
  public boolean isAccepted(String rawContentType) {
    if (rawContentType == null) {
      return false;
    }
    String baseType;
    try {
      MediaType parsed = MediaType.parseMediaType(rawContentType);
      baseType = parsed.getType() + "/" + parsed.getSubtype();
    } catch (InvalidMediaTypeException ex) {
      return false;
    }
    if (assetProperties.getHumanReadableContentTypeDenylist().contains(baseType)) {
      return false;
    }
    return assetProperties.getHumanReadableContentTypes().stream()
        .anyMatch(pattern -> matchesAllowlistEntry(pattern, baseType));
  }

  /**
   * @return a human-readable description of the configured allowlist and denylist, intended for
   *     client-facing error messages that explain why a content type was refused
   */
  public String describeAllowlist() {
    return String.format("Accepted families: %s. Always refused: %s",
        String.join(", ", new TreeSet<>(assetProperties.getHumanReadableContentTypes())),
        String.join(", ", new TreeSet<>(assetProperties.getHumanReadableContentTypeDenylist())));
  }

  private static boolean matchesAllowlistEntry(String pattern, String baseType) {
    if (pattern.endsWith("/*")) {
      return baseType.startsWith(pattern.substring(0, pattern.length() - 1));
    }
    return pattern.equals(baseType);
  }
}
