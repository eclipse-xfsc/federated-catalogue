package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.RDF_CONTEXT_KEY;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_11_CONTEXT;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Matches Tagus (Gaia-X VC 1.1) credentials.
 *
 * <p>A credential is Tagus if it is not a JWT and its {@code @context} contains the
 * VC 1.1 context URL ({@code https://www.w3.org/2018/credentials/v1}).
 */
@Slf4j
@Component
@Order(2)
public class TagusMatcher implements FormatMatcher {

  @Override
  public Optional<CredentialFormat> match(DetectionContext ctx) {
    if (ctx.isJwt()) {
      return Optional.empty();
    }

    JsonNode root = ctx.parsedJson();
    if (root == null) {
      return Optional.empty();
    }

    JsonNode rdfContext = root.get(RDF_CONTEXT_KEY);
    if (rdfContext != null && FormatMatcher.contextContains(rdfContext, VC_11_CONTEXT)) {
      log.debug("match; VC 1.1 context → GAIAX_V1_TAGUS");
      return Optional.of(CredentialFormat.GAIAX_V1_TAGUS);
    }

    return Optional.empty();
  }
}
