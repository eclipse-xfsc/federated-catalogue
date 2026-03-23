package eu.xfsc.fc.core.service.verification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.SignedJWT;

import eu.xfsc.fc.core.pojo.ContentAccessor;

import java.text.ParseException;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Routes incoming credential payloads to the correct processing path based on
 * JWT headers and payload structure.
 *
 * <p>Decision tree:
 * <ol>
 *   <li>Not a JWT ({@code eyJ} prefix absent) and has {@code proof} block → Path 1 (Tagus)</li>
 *   <li>JWT with {@code typ: vc+ld+json+jwt} or {@code vp+ld+jwt} and top-level {@code @context}
 *       (no {@code vc}/{@code vp} wrapper claim) → Path 2 (Loire)</li>
 *   <li>JWT with {@code vc} or {@code vp} wrapper claim → Path 3 (danubetech)</li>
 *   <li>Otherwise → UNKNOWN</li>
 * </ol>
 */
@Slf4j
@Component
public class FormatDetector {

  private static final String JWT_PREFIX = "eyJ";
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private static final String VC_11_CONTEXT = "https://www.w3.org/2018/credentials/v1";

  private static final Set<String> LOIRE_VC_TYP_VALUES = Set.of(
      "vc+ld+json+jwt"
  );
  private static final Set<String> LOIRE_VP_TYP_VALUES = Set.of(
      "vp+ld+jwt"
  );

  /**
   * Detects the credential format of the given payload.
   *
   * @param content the incoming credential content
   * @return the detected format; never null
   */
  public CredentialFormat detect(ContentAccessor content) {
    String body = content.getContentAsString().strip();

    if (!body.startsWith(JWT_PREFIX)) {
      return detectNonJwt(body);
    }
    return detectJwt(body);
  }

  private CredentialFormat detectNonJwt(String body) {
    try {
      JsonNode root = OBJECT_MAPPER.readTree(body);
      if (root.has("proof")) {
        JsonNode ctx = root.get("@context");
        if (ctx != null && containsValue(ctx, VC_11_CONTEXT)) {
          log.debug("detect; VC 1.1 context with proof block → GAIAX_V1_TAGUS");
          return CredentialFormat.GAIAX_V1_TAGUS;
        }
        // VC 2.0 with Linked Data Proof (LDP); future-proof; treat as unknown for now.
        // This would be a VC 2.0 credential secured with a Linked Data Proof instead of a JWT envelope
        // - a valid W3C format but not currently used in the Gaia-X ecosystem. We return UNKNOWN
        // as a future-proof slot; if this format becomes relevant, a fourth path can be added.
        log.debug("detect; proof block present but no VC 1.1 context → UNKNOWN");
        return CredentialFormat.UNKNOWN;
      }
      log.debug("detect; non-JWT without proof block → UNKNOWN");
      return CredentialFormat.UNKNOWN;
    } catch (JsonProcessingException ex) {
      log.debug("detect; body is not valid JSON → UNKNOWN");
      return CredentialFormat.UNKNOWN;
    }
  }

  private CredentialFormat detectJwt(String body) {
    JWSHeader header;
    JsonNode payload;
    try {
      SignedJWT signedJwt = SignedJWT.parse(body);
      header = signedJwt.getHeader();
      String payloadJson = signedJwt.getPayload().toString();
      payload = OBJECT_MAPPER.readTree(payloadJson);
    } catch (ParseException | JsonProcessingException ex) {
      log.debug("detect; JWT parse failed: {} → UNKNOWN", ex.getMessage());
      return CredentialFormat.UNKNOWN;
    }

    String typ = header.getType() != null ? header.getType().toString() : null;

    // Loire detection: typ header matches AND payload is top-level (no vc/vp wrapper)
    if (typ != null && isLoireTyp(typ)) {
      if (payload.has("@context") && !payload.has("vc") && !payload.has("vp")) {
        log.debug("detect; Loire typ header '{}' + top-level @context → GAIAX_V2_LOIRE", typ);
        return CredentialFormat.GAIAX_V2_LOIRE;
      }
      // typ says Loire but payload has vc/vp wrapper — contradictory
      log.debug("detect; typ '{}' but payload has vc/vp wrapper → UNKNOWN", typ);
      return CredentialFormat.UNKNOWN;
    }

    // danubetech detection: vc or vp wrapper claim present
    if (payload.has("vc") || payload.has("vp")) {
      log.debug("detect; JWT payload has vc/vp wrapper → VC2_DANUBETECH");
      return CredentialFormat.VC2_DANUBETECH;
    }

    // Fallback: top-level @context + type without standardized typ header (lenient Loire)
    if (payload.has("@context") && (payload.has("type") || payload.has("@type"))) {
      log.debug("detect; JWT with top-level @context+type but no Loire typ → GAIAX_V2_LOIRE");
      return CredentialFormat.GAIAX_V2_LOIRE;
    }

    log.debug("detect; unrecognizable JWT → UNKNOWN");
    return CredentialFormat.UNKNOWN;
  }

  private boolean isLoireTyp(String typ) {
    return LOIRE_VC_TYP_VALUES.contains(typ) || LOIRE_VP_TYP_VALUES.contains(typ);
  }

  private static boolean containsValue(JsonNode node, String value) {
    if (node.isArray()) {
      for (JsonNode element : node) {
        if (value.equals(element.asText())) {
          return true;
        }
      }
      return false;
    }
    return value.equals(node.asText());
  }
}
