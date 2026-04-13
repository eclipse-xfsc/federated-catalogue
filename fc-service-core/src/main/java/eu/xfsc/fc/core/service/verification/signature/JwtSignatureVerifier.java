package eu.xfsc.fc.core.service.verification.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.Ed25519Verifier;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.BadJWTException;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.VerificationMethod;
import java.security.PublicKey;
import java.text.ParseException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Verifies JWT-wrapped VC and VP credentials by resolving the issuer DID document
 * and checking the JWT signature against the assertionMethod verification method.
 *
 * <p>This component is purely cryptographic (ADR-3): it does not distinguish VC JWT
 * from VP JWT, and does not perform semantic checks (e.g., VP holder == iss).
 * All dispatch logic lives in {@code CredentialVerificationStrategy}.
 *
 * <p>Security constraints enforced:
 * <ul>
 *   <li>{@code alg:none} is rejected before any key lookup.</li>
 *   <li>Embedded key headers ({@code jwk}, {@code x5c}, {@code x5u}) are ignored — key
 *       material is sourced exclusively from the issuer DID document.</li>
 *   <li>Only {@code publicKeyJwk} format is supported; {@code publicKeyMultibase} /
 *       {@code publicKeyBase58} throw {@link VerificationException}.</li>
 *   <li>The matched key must appear in the {@code assertionMethod} relationship.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtSignatureVerifier {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final DefaultJWSVerifierFactory JWS_VERIFIER_FACTORY = new DefaultJWSVerifierFactory();
  private static final String W3C_DATA_URL_PREFIX = "data:application/vc+jwt,";
  private static final String ICAM_DATA_URL_PREFIX = "data:application/vc+ld+json+jwt,";

  private final DidDocumentResolver didResolver;

  /**
   * Verifies the signature of a compact JWT serialization.
   *
   * @param compactJwt the compact JWT to verify (header.payload.signature)
   * @return a {@link Validator} with the matched key URI and JWK JSON, expiration null
   * @throws ClientException if the JWT is malformed or has an invalid header
   * @throws VerificationException if signature verification fails or the key is not trusted
   */
  public Validator verify(String compactJwt) {
    SignedJWT signedJwt = parseJwt(compactJwt);
    JWSHeader header = signedJwt.getHeader();

    // alg guard — must be checked before any key lookup (DefaultJWTClaimsVerifier does not cover alg)
    if (header.getAlgorithm() == null) {
      throw new ClientException("JWT missing 'alg' header");
    }
    if ("none".equalsIgnoreCase(header.getAlgorithm().getName())) {
      throw new ClientException("JWT alg:none is not permitted");
    }

    // Embedded key headers (jwk, x5c, x5u) are intentionally ignored — key material from DID doc only.

    JWTClaimsSet claims = parseClaims(signedJwt);

    // iss guard
    if (claims.getIssuer() == null) {
      throw new ClientException("JWT missing 'iss' claim");
    }
    String iss = claims.getIssuer();

    // exp/nbf check (0s leeway, absent exp is valid per JWT spec)
    try {
      new DefaultJWTClaimsVerifier<>(null, null).verify(claims, null);
    } catch (BadJWTException ex) {
      throw new VerificationException("JWT claims validation failed: " + ex.getMessage(), ex);
    }

    // kid normalization: relative fragment → prepend iss
    String kid = header.getKeyID();
    if (kid != null && kid.startsWith("#")) {
      kid = iss + kid;
    }

    // DID resolution (Caffeine-cached; throws VerificationException on error)
    DIDDocument didDoc = didResolver.resolveDidDocument(iss);

    // assertionMethod lookup — only keys in assertionMethod are accepted
    List<VerificationMethod> methods = didDoc.getAssertionMethodVerificationMethodsDereferenced();

    if (kid != null) {
      return verifyWithKid(signedJwt, header, kid, iss, methods);
    } else {
      return verifyWithoutKid(signedJwt, header, iss, methods);
    }
  }

  /**
   * Extracts a compact JWT from an EnvelopedVerifiableCredential data: URL and verifies it.
   *
   * <p>Accepted prefixes:
   * <ul>
   *   <li>{@code data:application/vc+jwt,} (W3C VC-JOSE-COSE, IANA-registered, canonical)</li>
   *   <li>{@code data:application/vc+ld+json+jwt,} (Gaia-X ICAM 24.07, accepted for
   *       compatibility)</li>
   * </ul>
   *
   * @param dataUrl the data: URL containing the compact JWT
   * @return a Validator for the inner JWT
   * @throws ClientException if the URL format is invalid
   */
  public Validator verifyFromDataUrl(String dataUrl) {
    String compact;
    if (dataUrl.startsWith(W3C_DATA_URL_PREFIX)) {
      compact = dataUrl.substring(W3C_DATA_URL_PREFIX.length());
    } else if (dataUrl.startsWith(ICAM_DATA_URL_PREFIX)) {
      compact = dataUrl.substring(ICAM_DATA_URL_PREFIX.length());
    } else {
      throw new ClientException(
          "EnvelopedVerifiableCredential 'id' has invalid data: URL format; "
              + "expected prefix: data:application/vc+jwt, or data:application/vc+ld+json+jwt,");
    }
    return verify(compact);
  }

  // --- private helpers ---

  private Validator verifyWithKid(SignedJWT signedJwt, JWSHeader header,
      String kid, String iss, List<VerificationMethod> methods) {
    // find exact match by kid
    final String kidFinal = kid;
    VerificationMethod matched = methods.stream()
        .filter(vm -> kidFinal.equals(methodId(vm)))
        .findFirst()
        .orElse(null);

    if (matched == null) {
      List<String> available = methods.stream().map(this::methodId).toList();
      throw new VerificationException(
          "kid '" + kid + "' not found in assertionMethod; available: " + available);
    }

    JWK jwk = resolveJwk(matched, kid);
    JWSVerifier verifier = createVerifier(header, jwk);
    verifySignature(signedJwt, verifier, kid);

    log.debug("verify; verified kid '{}' for issuer '{}'", kid, iss);
    return new Validator(kid, jwk.toJSONString(), null);
  }

  private Validator verifyWithoutKid(SignedJWT signedJwt, JWSHeader header,
      String iss, List<VerificationMethod> methods) {
    if (methods.isEmpty()) {
      throw new VerificationException(
          "No assertionMethod entries in DID document for issuer: " + iss);
    }

    for (VerificationMethod method : methods) {
      String methodId = methodId(method);
      Object publicKeyJwkRaw = method.getPublicKeyJwk();
      if (publicKeyJwkRaw == null) {
        log.debug("verify; skipping method '{}' — no publicKeyJwk", methodId);
        continue;
      }
      if (!(publicKeyJwkRaw instanceof Map<?, ?> rawMap)) {
        log.debug("verify; skipping method '{}' — publicKeyJwk is not a map ({})",
            methodId, publicKeyJwkRaw.getClass().getSimpleName());
        continue;
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> jwkMap = (Map<String, Object>) rawMap;

      JWK jwk;
      try {
        jwk = parseJwkMap(jwkMap);
      } catch (VerificationException ex) {
        log.debug("verify; skipping method '{}' — {}", methodId, ex.getMessage());
        continue;
      }

      if (jwk.getKeyType() == null) {
        log.debug("verify; skipping method '{}' — missing kty", methodId);
        continue;
      }

      try {
        JWSVerifier jwtVerifier = createVerifier(header, jwk);
        if (signedJwt.verify(jwtVerifier)) {
          String resultKid = methodId != null ? methodId : iss;
          log.debug("verify; verified via method '{}' for issuer '{}'", methodId, iss);
          return new Validator(resultKid, jwk.toJSONString(), null);
        }
      } catch (VerificationException | JOSEException ex) {
        log.debug("verify; skipping method '{}' — {}", methodId, ex.getMessage());
      }
    }

    throw new VerificationException(
        "JWT signature verification failed — no matching assertionMethod key for issuer: " + iss);
  }

  private SignedJWT parseJwt(String compact) {
    try {
      return SignedJWT.parse(compact);
    } catch (ParseException ex) {
      throw new ClientException("malformed JWT: " + ex.getMessage(), ex);
    }
  }

  private JWTClaimsSet parseClaims(SignedJWT signedJwt) {
    try {
      return signedJwt.getJWTClaimsSet();
    } catch (ParseException ex) {
      throw new ClientException("malformed JWT claims: " + ex.getMessage(), ex);
    }
  }

  private JWK resolveJwk(VerificationMethod method, String kid) {
    Map<String, Object> jwkMap = (Map<String, Object>) method.getPublicKeyJwk();
    if (jwkMap == null) {
      throw new VerificationException("unsupported key format — only publicKeyJwk is supported");
    }
    JWK jwk = parseJwkMap(jwkMap);
    if (jwk.getKeyType() == null) {
      throw new ClientException("verification method missing 'kty' in publicKeyJwk");
    }
    log.trace("resolveJwk; kid='{}' resolved kty='{}'", kid, jwk.getKeyType());
    return jwk;
  }

  private JWK parseJwkMap(Map<String, Object> jwkMap) {
    try {
      String json = OBJECT_MAPPER.writeValueAsString(jwkMap);
      return JWK.parse(json);
    } catch (JsonProcessingException | ParseException ex) {
      throw new VerificationException("failed to parse publicKeyJwk: " + ex.getMessage(), ex);
    }
  }

  private JWSVerifier createVerifier(JWSHeader header, JWK jwk) {
    try {
      if (jwk instanceof OctetKeyPair okp) {
        // Ed25519Verifier accepts OctetKeyPair directly; toPublicKey() is not supported for OKP
        return new Ed25519Verifier(okp.toPublicJWK());
      }
      PublicKey publicKey;
      if (jwk instanceof RSAKey rsaKey) {
        publicKey = rsaKey.toPublicKey();
      } else if (jwk instanceof ECKey ecKey) {
        publicKey = ecKey.toPublicKey();
      } else {
        throw new VerificationException(
            "unsupported JWK key type for JWT verification: " + jwk.getKeyType());
      }
      return JWS_VERIFIER_FACTORY.createJWSVerifier(header, publicKey);
    } catch (JOSEException ex) {
      throw new VerificationException(
          "JWT alg incompatible with key type: " + ex.getMessage(), ex);
    }
  }

  private void verifySignature(SignedJWT signedJwt, JWSVerifier verifier, String kid) {
    try {
      if (!signedJwt.verify(verifier)) {
        throw new VerificationException(
            "JWT signature verification failed for kid '" + kid + "'");
      }
    } catch (VerificationException ex) {
      throw ex;
    } catch (JOSEException ex) {
      throw new VerificationException(
          "JWT signature verification error for kid '" + kid + "': " + ex.getMessage(), ex);
    }
  }

  private String methodId(VerificationMethod method) {
    return method.getId() != null ? method.getId().toString() : null;
  }
}
