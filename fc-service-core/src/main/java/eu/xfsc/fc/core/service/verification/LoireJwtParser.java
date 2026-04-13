package eu.xfsc.fc.core.service.verification;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import java.text.ParseException;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Parses Loire-format JWTs (W3C VC-JOSE-COSE / ICAM 24.07) where the credential
 * is the top-level JWT payload — no {@code vc}/{@code vp} wrapper claims.
 *
 * <p>This parser bypasses the danubetech library which expects wrapper claims.
 * After unwrapping, the payload is a standard JSON-LD credential that can be
 * processed by the existing claim extractors and SHACL validators.
 *
 * <p>Accepted header values (W3C VC-JOSE-COSE values are canonical and IANA-registered;
 * ICAM 24.07 values are accepted for backward compatibility):
 * <ul>
 *   <li>VC: {@code typ: vc+jwt} or {@code vc+ld+json+jwt},
 *       {@code cty: vc} or {@code vc+ld+json}</li>
 *   <li>VP: {@code typ: vp+jwt} or {@code vp+ld+jwt},
 *       {@code cty: vp} or {@code vp+ld+json}</li>
 * </ul>
 */
@Slf4j
@Component
public class LoireJwtParser {

  private static final Set<String> VALID_VC_TYP = Set.of("vc+jwt", "vc+ld+json+jwt");
  private static final Set<String> VALID_VP_TYP = Set.of("vp+jwt", "vp+ld+jwt");
  private static final Set<String> VALID_VC_CTY = Set.of("vc", "vc+ld+json");
  private static final Set<String> VALID_VP_CTY = Set.of("vp", "vp+ld+json");

  private static final String VALID_TYP_DISPLAY =
      Stream.concat(VALID_VC_TYP.stream(), VALID_VP_TYP.stream())
            .sorted().collect(Collectors.joining(", "));
  private static final String VALID_VC_CTY_DISPLAY =
      VALID_VC_CTY.stream().sorted().collect(Collectors.joining(", "));
  private static final String VALID_VP_CTY_DISPLAY =
      VALID_VP_CTY.stream().sorted().collect(Collectors.joining(", "));

  /**
   * Unwraps a Loire-format JWT to JSON-LD.
   *
   * <p>Validates W3C VC-JOSE-COSE / ICAM 24.07 headers and rejects payloads that contain
   * {@code vc} or {@code vp} wrapper claims (these belong to the danubetech path).
   *
   * @param content the JWT-wrapped content
   * @return the unwrapped JSON-LD payload
   * @throws ClientException if the JWT is malformed, headers are invalid,
   *     or wrapper claims are present
   */
  public ContentAccessor unwrap(ContentAccessor content) {
    String body = content.getContentAsString().strip();
    SignedJWT signedJwt = parseJwt(body);
    JWSHeader header = signedJwt.getHeader();

    validateHeaders(header);
    JWTClaimsSet claims = parseClaims(signedJwt);
    rejectWrapperClaims(claims);

    String payloadJson = signedJwt.getPayload().toString();
    log.debug("unwrap; Loire JWT unwrapped successfully");
    return new ContentAccessorDirect(payloadJson);
  }

  /**
   * Returns true if the JWT header indicates a Verifiable Presentation.
   *
   * @param content the JWT-wrapped content (must start with {@code eyJ})
   * @return true if {@code typ} is {@code vp+jwt} (W3C) or {@code vp+ld+jwt} (ICAM 24.07)
   */
  public boolean isVpJwt(ContentAccessor content) {
    String body = content.getContentAsString().strip();
    try {
      SignedJWT signedJwt = SignedJWT.parse(body);
      String typ = signedJwt.getHeader().getType() != null
          ? signedJwt.getHeader().getType().toString() : null;
      return typ != null && VALID_VP_TYP.contains(typ);
    } catch (ParseException ex) {
      return false;
    }
  }

  private void validateHeaders(JWSHeader header) {
    String typ = header.getType() != null ? header.getType().toString() : null;
    String cty = header.getContentType();

    if (typ == null) {
      throw new ClientException("Loire JWT missing required 'typ' header");
    }

    boolean isVc = VALID_VC_TYP.contains(typ);
    boolean isVp = VALID_VP_TYP.contains(typ);
    if (!isVc && !isVp) {
      throw new ClientException(
          "Loire JWT has invalid 'typ' header: '" + typ
              + "'; expected one of: " + VALID_TYP_DISPLAY);
    }

    if (cty == null) {
      throw new ClientException(
          "Loire JWT missing required 'cty' header; "
              + "expected: " + (isVc ? VALID_VC_CTY_DISPLAY : VALID_VP_CTY_DISPLAY));
    }

    if (isVc && !VALID_VC_CTY.contains(cty)) {
      throw new ClientException(
          "Loire VC JWT has invalid 'cty' header: '" + cty + "'; expected: " + VALID_VC_CTY_DISPLAY);
    }
    if (isVp && !VALID_VP_CTY.contains(cty)) {
      throw new ClientException(
          "Loire VP JWT has invalid 'cty' header: '" + cty + "'; expected: " + VALID_VP_CTY_DISPLAY);
    }
  }

  private void rejectWrapperClaims(JWTClaimsSet claims) {
    if (claims.getClaim("vc") != null) {
      throw new ClientException(
          "Loire JWT must not contain 'vc' wrapper claim — "
              + "credential fields must be top-level in the JWT payload (ICAM 24.07)");
    }
    if (claims.getClaim("vp") != null) {
      throw new ClientException(
          "Loire JWT must not contain 'vp' wrapper claim — "
              + "presentation fields must be top-level in the JWT payload (ICAM 24.07)");
    }
  }

  private SignedJWT parseJwt(String compact) {
    try {
      return SignedJWT.parse(compact);
    } catch (ParseException ex) {
      throw new ClientException("Loire JWT is malformed: " + ex.getMessage(), ex);
    }
  }

  private JWTClaimsSet parseClaims(SignedJWT signedJwt) {
    try {
      return signedJwt.getJWTClaimsSet();
    } catch (ParseException ex) {
      throw new ClientException("Loire JWT claims are malformed: " + ex.getMessage(), ex);
    }
  }
}
