package eu.xfsc.fc.core.service.trustframework;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Per-role configuration entry from a bundle's {@code framework.yaml}.
 *
 * <p>{@code additionalRoots} enables SHACL sibling-class grouping: explicit URIs that
 * resolve to this role even though they are not OWL subclasses of the role's primary root.
 * Use when an external ontology genuinely declares siblings without subsumption — for example,
 * {@code gx:DigitalServiceOffering} in gx-2511 lacks {@code rdfs:subClassOf gx:ServiceOffering}
 * in the ontology, so this field is the correct mapping mechanism for that case.
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
