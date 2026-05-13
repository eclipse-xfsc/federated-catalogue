package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.RDF_CONTEXT_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSHeader;

import java.util.Optional;
import java.util.Set;

import com.nimbusds.jwt.SignedJWT;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Matches Loire (ICAM 24.07 / VC 2.0 Gaia-X) credentials.
 *
 * <p>A credential is Loire if its JWT {@code typ} header is a Loire value
 * ({@code vc+ld+json+jwt} or {@code vp+ld+jwt}) and the payload contains a top-level
 * {@code @context} without a {@code vc} or {@code vp} wrapper claim.
 *
 * <p>If the {@code typ} matches but the payload structure is contradictory (has a wrapper
 * claim), this matcher returns {@link CredentialFormat#UNKNOWN} to stop evaluation —
 * routing such a credential to a less-specific matcher would be incorrect.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LoireMatcher implements FormatMatcher {

  /**
   * Profile ID declared in {@code trustframeworks/gaia-x-2511/framework.yaml}.
   * Used to look up the governing trust framework family in the registry.
   */
  private static final String PROFILE_ID = "gaia-x-2511";

  /**
   * Property key inside the bundle's {@code properties} map carrying the trust anchor
   * registry URL. Read from the framework bundle at runtime so the value lives with the
   * rest of the bundle's configuration rather than in application-level properties.
   */
  private static final String PROPERTY_TRUST_ANCHOR_URL = "trust_anchor_url";

  private static final Set<String> LOIRE_TYP_VALUES = Set.of(
      "vc+jwt",         // W3C VC-JOSE-COSE (IANA-registered, canonical)
      "vc+ld+json+jwt", // Gaia-X ICAM 24.07 (accepted for compatibility)
      "vp+jwt",         // W3C VC-JOSE-COSE (IANA-registered, canonical)
      "vp+ld+jwt"       // Gaia-X ICAM 24.07 (accepted for compatibility)
  );

  private final TrustFrameworkRegistry trustFrameworkRegistry;
  private final TrustFrameworkService trustFrameworkService;

  @Override
  public Optional<CredentialFormat> match(DetectionContext ctx) {
    final SignedJWT jwt = ctx.jwt();
    if (jwt == null) {
      return Optional.empty();
    }

    final JWSHeader header = jwt.getHeader();
    String typ = header.getType() != null ? header.getType().toString() : null;
    if (typ == null || !LOIRE_TYP_VALUES.contains(typ)) {
      return Optional.empty();
    }

    // Loire typ confirmed — validate payload structure
    final JsonNode payload = ctx.parsedJson();
    if (payload != null && payload.has(RDF_CONTEXT_KEY) && !payload.has("vc") && !payload.has("vp")) {
      log.debug("match; Loire typ '{}' + top-level @context → GAIAX_V2_LOIRE", typ);
      return Optional.of(CredentialFormat.GAIAX_V2_LOIRE);
    }

    // typ says Loire but payload structure is contradictory — explicit rejection
    log.debug("match; Loire typ '{}' but payload structure is contradictory → UNKNOWN", typ);
    return Optional.of(CredentialFormat.UNKNOWN);
  }

  /**
   * Returns the trust framework family that governs Loire credentials (e.g. {@code "gaia-x"}),
   * derived from the bundle registry at runtime. Returns empty if the bundle is not loaded.
   */
  public Optional<String> frameworkFamily() {
    return trustFrameworkRegistry.getBundle(PROFILE_ID)
        .map(b -> b.config().family());
  }

  /**
   * Returns the trust anchor registry URL declared in the bundle's properties map.
   * Empty if the bundle is not loaded or the property is absent or blank.
   */
  public Optional<String> trustAnchorUrl() {
    return trustFrameworkRegistry.getBundle(PROFILE_ID)
        .map(b -> b.config().properties().get(PROPERTY_TRUST_ANCHOR_URL))
        .filter(s -> !s.isBlank());
  }

  /**
   * Returns true when the framework governing Loire credentials is both registered (its
   * bundle is loaded) and enabled in persistence.
   */
  public boolean isEnabled() {
    return frameworkFamily()
        .map(trustFrameworkService::isEnabled)
        .orElse(false);
  }
}
