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
    @JsonProperty("base_classes") Map<String, BaseClassConfig> baseClasses,
    @JsonProperty("properties") Map<String, String> properties
) {

  public FrameworkBundleConfig {
    baseClasses = baseClasses != null ? baseClasses : Map.of();
    properties = properties != null ? properties : Map.of();
  }
}
