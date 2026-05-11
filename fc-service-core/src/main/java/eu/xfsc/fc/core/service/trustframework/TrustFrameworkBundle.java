package eu.xfsc.fc.core.service.trustframework;

import eu.xfsc.fc.core.pojo.ContentAccessor;

/**
 * Aggregate that groups a trust-framework's config, ontology, and SHACL shapes
 * into a single loadable unit.
 *
 * <p>{@code ontology} and {@code shapes} are nullable: a bundle declared with
 * {@code validationType: json-schema} carries no SHACL shapes.
 */
public record TrustFrameworkBundle(
    FrameworkBundleConfig config,
    ContentAccessor ontology,
    ContentAccessor shapes
) {
}
