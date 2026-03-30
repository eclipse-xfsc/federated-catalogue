package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.JWT_PREFIX;

import com.danubetech.verifiablecredentials.jwt.JwtVerifiableCredentialV2;
import com.danubetech.verifiablecredentials.jwt.JwtVerifiablePresentationV2;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Detects JWT-wrapped Verifiable Credentials and Presentations and unwraps them
 * to JSON-LD before the main verification pipeline processes them.
 *
 * <p>JWT detection is based on the content body: payloads starting with {@code eyJ}
 * (the base64url-encoded JWT header) are treated as compact serializations.
 * The preprocessor first attempts to parse as a VC 2.0 JWT, then as a VP 2.0 JWT.
 * Non-JWT content is returned unchanged.
 *
 * <p>Signature verification is out of scope here — see Issue #12.
 */
@Slf4j
@Component
public class JwtContentPreprocessor {

  private static final Set<String> JWT_CONTENT_TYPES = Set.of(
      "application/vc+ld+json+jwt",
      "application/vp+ld+json+jwt"
  );

  private static final Set<String> JSON_LD_CONTENT_TYPES = Set.of(
      "application/ld+json",
      "application/vc+ld+json",
      "application/vp+ld+json"
  );

  private static String normalizeContentType(String raw) {
    if (raw == null) {
      return null;
    }
    return raw.split(";")[0].strip().toLowerCase();
  }

  /**
   * Returns true if the content is JWT-wrapped, using two-signal detection: content-type as
   * primary signal and body prefix as fallback. Throws {@link ClientException} on conflict.
   *
   * @param content the incoming content accessor
   * @return true if the content is a compact JWT serialization
   * @throws ClientException if content-type and body contradict each other
   */
  public boolean isJwtWrapped(ContentAccessor content) {
    String body = content.getContentAsString().strip();
    boolean bodyIsJwt = body.startsWith(JWT_PREFIX);
    String ct = normalizeContentType(content.getContentType());

    if (ct != null && JWT_CONTENT_TYPES.contains(ct)) {
      if (!bodyIsJwt) {
        throw new ClientException(
            "Content-Type declares JWT (" + ct + ") but body is not JWT-encoded");
      }
      return true;
    }

    if (ct != null && JSON_LD_CONTENT_TYPES.contains(ct) && bodyIsJwt) {
      throw new ClientException(
          "Content-Type declares JSON-LD (" + ct + ") but body is JWT-encoded; "
          + "use application/vc+ld+json+jwt for JWT-wrapped credentials");
    }

    // Fallback: body sniff (application/json, null, or unknown content-type)
    return bodyIsJwt;
  }

  /**
   * Unwraps a JWT-wrapped credential or presentation to JSON-LD.
   *
   * @param content the incoming content accessor
   * @return unwrapped JSON-LD content, or the original content if not JWT-wrapped
   */
  public ContentAccessor unwrap(ContentAccessor content) {
    String body = content.getContentAsString().strip();
    // Body-only check here is intentional: callers invoke isJwtWrapped() first (which enforces
    // content-type conflict detection), so by the time unwrap() is called the body is known to
    // be JWT. The unwrapped JSON-LD payload carries no meaningful HTTP content-type.
    if (!body.startsWith(JWT_PREFIX)) {
      return content;
    }
    log.debug("unwrap; detected JWT-like content, attempting unwrap");

    try {
      String json = JwtVerifiableCredentialV2.fromCompactSerialization(body)
          .getPayloadObject().toJson();
      log.debug("unwrap; successfully unwrapped as VC 2.0 JWT");
      return new ContentAccessorDirect(json);
    } catch (Exception vcEx) {
      log.debug("unwrap; not a VC 2.0 JWT ({}), trying VP 2.0 JWT", vcEx.getMessage());
    }

    try {
      String json = JwtVerifiablePresentationV2.fromCompactSerialization(body)
          .getPayloadObject().toJson();
      log.debug("unwrap; successfully unwrapped as VP 2.0 JWT");
      return new ContentAccessorDirect(json);
    } catch (Exception vpEx) {
      log.debug("unwrap; not a VP 2.0 JWT either ({}), rejecting", vpEx.getMessage());
    }

    throw new ClientException("Content appears to be JWT-wrapped but could not be parsed as VC 2.0 or VP 2.0");
  }
}
