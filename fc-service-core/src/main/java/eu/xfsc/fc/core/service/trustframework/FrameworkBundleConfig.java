package eu.xfsc.fc.core.service.trustframework;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Deserialisation model for a trust-framework bundle's {@code framework.yaml} file.
 *
 * <p>Load with:
 * <pre>{@code new YAMLMapper().readValue(inputStream, FrameworkBundleConfig.class)}</pre>
 */
public record FrameworkBundleConfig(
    @JsonProperty("id") String id,
    @JsonProperty("family") String family,
    @JsonProperty("namespace") String namespace,
    @JsonProperty("validation_type") ValidationType validationType,
    @JsonProperty("roles") Map<String, RoleConfig> roles,
    @JsonProperty("properties") Map<String, String> properties
) {

  public FrameworkBundleConfig {
    roles = roles != null ? roles : Map.of();
    properties = properties != null ? properties : Map.of();
  }
}
