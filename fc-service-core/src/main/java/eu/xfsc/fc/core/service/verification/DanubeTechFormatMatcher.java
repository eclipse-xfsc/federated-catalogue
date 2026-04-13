package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.RDF_CONTEXT_KEY;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Matches danubetech VC 2.0 credentials — both JWT-wrapped and plain JSON-LD.
 * Note: Don't confuse danubetech with the Gaia-X Danube release, which uses Loire-format JWTs (without wrapper claims!).
 *
 * <ul>
 *   <li>JWT with a {@code vc} or {@code vp} wrapper claim in the payload</li>
 *   <li>Non-JWT JSON-LD with a VC 2.0 {@code @context} ({@code https://www.w3.org/ns/credentials/v2})</li>
 * </ul>
 *
 * <p>Must run after {@link LoireMatcher} ({@code @Order(3)}) because a JWT with a Loire {@code typ}
 * header and a {@code vc}/{@code vp} wrapper must be rejected by Loire, not routed here.
 */
@Slf4j
@Component
@Order(3)
public class DanubeTechFormatMatcher implements FormatMatcher {

  @Override
  public Optional<CredentialFormat> match(DetectionContext ctx) {
      final JsonNode parsedJson = ctx.parsedJson();
      if (parsedJson == null) {
      return Optional.empty();
    }

    if (ctx.isJwt()) {
      if (parsedJson.has("vc") || parsedJson.has("vp")) {
        log.debug("match; JWT payload has vc/vp wrapper → VC2_DANUBETECH");
        return Optional.of(CredentialFormat.VC2_DANUBETECH);
      }
      return Optional.empty();
    }

    JsonNode rdfContext = parsedJson.get(RDF_CONTEXT_KEY);
    if (rdfContext != null && FormatMatcher.contextContains(rdfContext, VC_20_CONTEXT)) {
      log.debug("match; VC 2.0 context (non-JWT) → VC2_DANUBETECH");
      return Optional.of(CredentialFormat.VC2_DANUBETECH);
    }

    return Optional.empty();
  }
}
