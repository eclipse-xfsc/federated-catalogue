package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.RDF_CONTEXT_KEY;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.SignedJWT;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;

import java.util.Optional;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Processor for Loire (Gaia-X ICAM 24.07 / VC 2.0 Gaia-X) credentials. Owns detection,
 * JWT-only declaration, JSON-LD unwrap, and policy enforcement for this format.
 *
 * <p>A credential is Loire if its JWT {@code typ} header is a Loire value
 * ({@code vc+jwt}, {@code vc+ld+json+jwt}, {@code vp+jwt}, {@code vp+ld+jwt}) and the
 * payload contains a top-level {@code @context} without a {@code vc} or {@code vp}
 * wrapper claim. A contradictory payload (Loire {@code typ} plus a wrapper claim) is
 * explicitly rejected as {@link CredentialFormat#UNKNOWN} so it is not silently routed
 * to a less-specific processor.
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class LoireCredentialProcessor implements CredentialFormatProcessor {

  private static final Set<String> LOIRE_TYP_VALUES = Set.of(
      "vc+jwt",         // W3C VC-JOSE-COSE (IANA-registered, canonical)
      "vc+ld+json+jwt", // Gaia-X ICAM 24.07 (accepted for compatibility)
      "vp+jwt",         // W3C VC-JOSE-COSE (IANA-registered, canonical)
      "vp+ld+jwt"       // Gaia-X ICAM 24.07 (accepted for compatibility)
  );

  private final LoireJwtParser loireJwtParser;
  private final LoirePolicyEnforcer loirePolicyEnforcer;
  private final JwtSignatureVerifier jwtSignatureVerifier;

  @Override
  public CredentialFormat getFormat() {
    return CredentialFormat.GAIAX_V2_LOIRE;
  }

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

    final JsonNode payload = ctx.parsedJson();
    if (payload != null && payload.has(RDF_CONTEXT_KEY) && !payload.has("vc") && !payload.has("vp")) {
      log.debug("match; Loire typ '{}' + top-level @context → GAIAX_V2_LOIRE", typ);
      return Optional.of(CredentialFormat.GAIAX_V2_LOIRE);
    }

    log.debug("match; Loire typ '{}' but payload structure is contradictory → UNKNOWN", typ);
    return Optional.of(CredentialFormat.UNKNOWN);
  }

  @Override
  public ProcessedEnvelope process(String body, ContentAccessor payload, boolean verifySigs) {
    // Loire credentials are always compact JWTs.
    Validator jwtValidator = verifySigs ? jwtSignatureVerifier.verify(body) : null;
    loirePolicyEnforcer.enforceIfApplicable(body, jwtValidator);
    return new ProcessedEnvelope(loireJwtParser.unwrap(payload), true, jwtValidator);
  }

  @Override
  public ContentAccessor unwrapNested(ContentAccessor payload) {
    return loireJwtParser.unwrap(payload);
  }
}
