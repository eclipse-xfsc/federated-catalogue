package eu.xfsc.fc.core.service.verification;

import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import java.text.ParseException;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

/**
 * Parses Loire-format JWTs (ICAM 24.07 / VC-JOSE-COSE) where the credential
 * is the top-level JWT payload — no {@code vc}/{@code vp} wrapper claims.
 *
 * <p>This parser bypasses the danubetech library which expects wrapper claims.
 * After unwrapping, the payload is a standard JSON-LD credential that can be
 * processed by the existing claim extractors and SHACL validators.
 *
 * <p>Header validation enforced per ICAM 24.07:
 * <ul>
 *   <li>VC: {@code typ: vc+ld+json+jwt}, {@code cty: vc+ld+json}</li>
 *   <li>VP: {@code typ: vp+ld+jwt}, {@code cty: vp+ld+json}</li>
 * </ul>
 */
@Slf4j
@Component
public class LoireJwtParser {

  private static final Set<String> VALID_VC_TYP = Set.of("vc+ld+json+jwt");
  private static final Set<String> VALID_VP_TYP = Set.of("vp+ld+jwt");
  private static final Set<String> VALID_VC_CTY = Set.of("vc+ld+json");
  private static final Set<String> VALID_VP_CTY = Set.of("vp+ld+json");

  /**
   * Unwraps a Loire-format JWT to JSON-LD.
   *
   * <p>Validates ICAM 24.07 headers and rejects payloads that contain
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
   * @return true if {@code typ} is {@code vp+ld+jwt}
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
              + "'; expected one of: vc+ld+json+jwt, vp+ld+jwt");
    }

    if (cty == null) {
      log.debug("validateHeaders; Loire JWT missing optional 'cty' header (typ='{}')", typ);
      return;
    }

    if (isVc && !VALID_VC_CTY.contains(cty)) {
      throw new ClientException(
          "Loire VC JWT has invalid 'cty' header: '" + cty + "'; expected: vc+ld+json");
    }
    if (isVp && !VALID_VP_CTY.contains(cty)) {
      throw new ClientException(
          "Loire VP JWT has invalid 'cty' header: '" + cty + "'; expected: vp+ld+json");
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
