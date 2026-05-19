package eu.xfsc.fc.core.service.trustframework.compliance;

import com.nimbusds.jwt.JWTParser;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.ServiceUnavailableException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Orchestrates a single compliance check: resolves the profile, verifies the family is enabled,
 * dispatches to the appropriate {@link TrustFrameworkClient}, and maps transport failures to
 * HTTP-appropriate exceptions.
 *
 * <p>Unknown profile IDs throw {@link ClientException}. Disabled families throw
 * {@link ConflictException}. Network timeouts bubble as {@link TimeoutException} (→ 504);
 * other I/O failures bubble as {@link ServiceUnavailableException} (→ 503).
 * {@link UnverifiableAttestation} is a first-class outcome produced by the client when the
 * service returned a credential that could not be locally verified — it is not an error.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComplianceCheckOrchestrator {

  // MDC keys written for the duration of each compliance check so that all log lines
  // from this thread — including nested calls into RestTemplate and client implementations —
  // automatically carry asset and profile context without explicit parameter threading.
  // With a structured/JSON log appender (e.g. Logstash encoder) these become first-class
  // fields in every log event. To add more context: MDC.put(key, value) + MDC.remove(key)
  // in a finally block; no log-pattern change needed for structured output.
  private static final String MDC_ASSET_ID = "assetId";
  private static final String MDC_PROFILE_ID = "frameworkProfileId";

  private final TrustFrameworkRegistry registry;
  private final TrustFrameworkService tfService;
  private final TrustFrameworkClientRegistry clientRegistry;

  /**
   * Runs a compliance check for the given asset against the named trust-framework profile.
   *
   * @param assetId            identifier of the asset being checked
   * @param frameworkProfileId profile to use for the check; must not be {@code null}
   * @param assetPayload       raw credential payload forwarded to the compliance service; must not be {@code null}
   * @return the compliance outcome; never {@code null}. Returns {@link UnverifiableAttestation}
   *         when {@code assetPayload} is a parseable JWT whose {@code id} claim is non-blank and
   *         does not equal {@code assetId}, without contacting the compliance service.
   * @throws ClientException              when inputs are null, the profile is not registered,
   *                                      or the resolved client type is unknown
   * @throws ConflictException            when the profile's family is disabled
   * @throws TimeoutException             when the compliance service did not respond in time
   * @throws ServiceUnavailableException  when the compliance service could not be reached
   */
  public ComplianceCheckOutcome check(String assetId, String frameworkProfileId, String assetPayload) {
    if (frameworkProfileId == null) {
      throw new ClientException("frameworkProfileId must not be null");
    }
    if (assetPayload == null) {
      throw new ClientException("assetPayload must not be null");
    }

    String vpId = extractVpId(assetPayload);
    if (!vpId.isBlank() && !vpId.equals(assetId)) {
      return new UnverifiableAttestation(
          FailureCategory.UNVERIFIABLE_ATTESTATION,
          assetPayload,
          "VP id '%s' does not match requested asset id '%s'".formatted(vpId, assetId)
      );
    }

    MDC.put(MDC_ASSET_ID, assetId);
    MDC.put(MDC_PROFILE_ID, frameworkProfileId);
    try {
      return doCheck(frameworkProfileId, assetPayload);
    } finally {
      MDC.remove(MDC_ASSET_ID);
      MDC.remove(MDC_PROFILE_ID);
    }
  }

  private String extractVpId(String payload) {
    try {
      String value = JWTParser.parse(payload).getJWTClaimsSet().getStringClaim("id");
      return value != null ? value : "";
    } catch (Exception e) {
      return "";
    }
  }

  private ComplianceCheckOutcome doCheck(String frameworkProfileId, String assetPayload) {
    TrustFrameworkProfileConfig config = registry.getProfileConfig(frameworkProfileId)
        .orElseThrow(() -> new ClientException("Unknown trust-framework profile: " + frameworkProfileId));

    if (!tfService.isEnabled(config.familyId())) {
      throw new ConflictException("Trust-framework family is disabled: " + config.familyId());
    }

    TrustFrameworkClient client;
    try {
      client = clientRegistry.resolve(config.clientType());
    } catch (IllegalArgumentException e) {
      throw new ClientException("Unknown clientType: " + config.clientType(), e);
    }

    var credential = new ContentAccessorDirect(assetPayload);
    ComplianceCheckOutcome result;
    try {
      result = client.check(credential, config);
    } catch (ClientException | TimeoutException | ServiceUnavailableException e) {
      throw e;
    } catch (RuntimeException e) {
      log.error("Unexpected exception from compliance client", e);
      throw new ServiceUnavailableException("Compliance client error: " + e.getMessage(), e);
    }
    if (result == null) {
      throw new ServiceUnavailableException("Compliance client returned null for profile: " + frameworkProfileId);
    }
    return result;
  }
}
