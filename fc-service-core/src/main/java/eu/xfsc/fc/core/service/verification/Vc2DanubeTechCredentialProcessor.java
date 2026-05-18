package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.JWT_PREFIX;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.RDF_CONTEXT_KEY;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;

import com.fasterxml.jackson.databind.JsonNode;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Processor for danubetech VC 2.0 credentials — both JWT-wrapped and plain JSON-LD.
 *
 * <ul>
 *   <li>JWT with a {@code vc} or {@code vp} wrapper claim in the payload</li>
 *   <li>Non-JWT JSON-LD with a VC 2.0 {@code @context} ({@code https://www.w3.org/ns/credentials/v2})</li>
 * </ul>
 *
 * <p>Must run after {@link LoireCredentialProcessor} ({@code @Order(3)}) because a JWT
 * with a Loire {@code typ} header and a {@code vc}/{@code vp} wrapper must be rejected
 * by Loire, not routed here.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class Vc2DanubeTechCredentialProcessor implements CredentialFormatProcessor {

  private final JwtContentPreprocessor jwtPreprocessor;
  private final JwtSignatureVerifier jwtSignatureVerifier;

  @Override
  public CredentialFormat getFormat() {
    return CredentialFormat.VC2_DANUBETECH;
  }

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
    if (rdfContext != null && CredentialFormatProcessor.contextContains(rdfContext, VC_20_CONTEXT)) {
      log.debug("match; VC 2.0 context (non-JWT) → VC2_DANUBETECH");
      return Optional.of(CredentialFormat.VC2_DANUBETECH);
    }

    return Optional.empty();
  }

  /**
   * VC 2.0 may arrive as either JSON-LD or a compact JWT — only the JWT form needs signature
   * verification. No format-specific policies apply.
   */
  @Override
  public ProcessedEnvelope process(String body, ContentAccessor payload, boolean verifySigs) {
    boolean isJwt = body.startsWith(JWT_PREFIX);
    Validator jwtValidator = (isJwt && verifySigs) ? jwtSignatureVerifier.verify(body) : null;
    return new ProcessedEnvelope(jwtPreprocessor.unwrap(payload), isJwt, jwtValidator);
  }

  @Override
  public ContentAccessor unwrapNested(ContentAccessor payload) {
    return jwtPreprocessor.unwrap(payload);
  }
}
