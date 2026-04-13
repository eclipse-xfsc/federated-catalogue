package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.RDF_CONTEXT_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSHeader;

import java.util.Optional;
import java.util.Set;

import com.nimbusds.jwt.SignedJWT;
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
public class LoireMatcher implements FormatMatcher {

  private static final Set<String> LOIRE_TYP_VALUES = Set.of(
      "vc+jwt",         // W3C VC-JOSE-COSE (IANA-registered, canonical)
      "vc+ld+json+jwt", // Gaia-X ICAM 24.07 (accepted for compatibility)
      "vp+jwt",         // W3C VC-JOSE-COSE (IANA-registered, canonical)
      "vp+ld+jwt"       // Gaia-X ICAM 24.07 (accepted for compatibility)
  );

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
}
