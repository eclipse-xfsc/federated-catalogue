package eu.xfsc.fc.server.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

/**
 * Configuration for the human-readable companion endpoint.
 *
 * <p>The accepted-content-type rule is the intersection of two filters: the {@code
 * humanReadableContentTypes} allowlist (which accepts {@code text/*}-style family wildcards) and
 * the {@code humanReadableContentTypeDenylist} block list (exact match, no wildcards). The denylist
 * overrides the allowlist so that an entire family can be admitted while individual entries that
 * carry script-execution or active-content risk are still refused.</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties("federated-catalogue.assets")
public class AssetProperties {

  /**
   * Family-aware allowlist. Each entry is either an exact MIME type or a wildcard of the form
   * {@code type/*}; subtype-level wildcards ({@code text/x-*}) are not supported. The defaults
   * admit the inert document, structured-data, image, audio, and video families that can be served
   * back to a browser without the catalogue executing them server-side; entries that combine
   * those families with executable semantics live on the denylist below.
   */
  private Set<String> humanReadableContentTypes = new LinkedHashSet<>(Set.of(
      "text/*",
      "image/*",
      "audio/*",
      "video/*",
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/json",
      "application/ld+json",
      "application/xml",
      "application/yaml",
      "application/x-yaml"
  ));

  /**
   * Exact MIME types that are refused even if a wildcard on the allowlist would otherwise admit
   * them. Defaults block: scripting MIME types (which browsers will execute when echoed back as the
   * stored Content-Type), inline-scriptable SVG, and opaque/native-binary types.
   */
  private Set<String> humanReadableContentTypeDenylist = new LinkedHashSet<>(Set.of(
      "text/javascript",
      "text/ecmascript",
      "text/vbscript",
      "application/javascript",
      "application/ecmascript",
      "application/x-javascript",
      "image/svg+xml",
      "application/octet-stream",
      "application/x-msdownload",
      "application/x-executable",
      "application/x-sh",
      "application/x-shockwave-flash",
      "application/x-httpd-php"
  ));

}
