package eu.xfsc.fc.core.service.trustframework;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Per-role configuration entry from a bundle's {@code framework.yaml}.
 *
 * <p>{@code additionalRoots} enables SHACL sibling-class grouping (e.g. the gx-2511
 * DigitalServiceOffering workaround that was required when migrating to Loire from Tagus) to map DSO to ServiceOffering.
 * {@code types} is defined for forward-compatibility with JSON Schema validation engines
 * and is intentionally unused in the SHACL resolver until other trust frameworks with different validations are integrated.
 */
public record RoleConfig(
    @JsonProperty("additional_roots") List<String> additionalRoots,
    @JsonProperty("types") List<String> types
) {

  public RoleConfig {
    additionalRoots = additionalRoots != null ? additionalRoots : List.of();
    types = types != null ? types : List.of();
  }
}
