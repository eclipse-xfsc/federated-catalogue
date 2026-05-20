package eu.xfsc.fc.core.service.trustframework.compliance;

/**
 * Resolved configuration for a single trust-framework profile, derived from the bundle
 * metadata and used when invoking a {@link TrustFrameworkClient}.
 *
 * @param frameworkProfileId unique identifier of the trust-framework profile
 * @param familyId           family identifier grouping related profiles
 * @param clientType         key selecting the {@link TrustFrameworkClient} implementation
 * @param serviceUrl         base URL of the compliance service endpoint
 * @param apiVersion         API version string sent to the compliance service
 * @param timeoutSeconds     per-request timeout in seconds
 */
public record TrustFrameworkProfileConfig(
    String frameworkProfileId,
    String familyId,
    String clientType,
    String serviceUrl,
    String apiVersion,
    int timeoutSeconds
) {
}
