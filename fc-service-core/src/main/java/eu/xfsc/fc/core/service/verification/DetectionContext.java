package eu.xfsc.fc.core.service.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.lang.Nullable;

/**
 * Pre-parsed credential payload, built once by {@link CredentialFormatDetector} and passed to each
 * {@link FormatMatcher}. Avoids reparsing the JWT or body JSON per matcher.
 *
 * <p>{@code parsedJson} holds:
 * <ul>
 *   <li>For JWT credentials — the decoded JWT payload as {@link JsonNode}</li>
 *   <li>For non-JWT credentials — the raw body as {@link JsonNode}</li>
 *   <li>{@code Optional.empty()} if parsing failed</li>
 * </ul>
 */
public record DetectionContext(
    String body,
    @Nullable SignedJWT jwt,
    @Nullable JsonNode parsedJson
) {

  /** Returns {@code true} if the credential was successfully parsed as a signed JWT. */
  public boolean isJwt() {
    return jwt != null;
  }
}
