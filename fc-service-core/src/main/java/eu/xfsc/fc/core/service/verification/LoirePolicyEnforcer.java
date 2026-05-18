package eu.xfsc.fc.core.service.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import jakarta.annotation.PostConstruct;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Format-specific verification policies for Loire (Gaia-X ICAM 24.07) credentials.
 *
 * <p>Owns the rules that apply only when the Loire trust framework is enabled:
 * <ul>
 *   <li>DID method restriction — issuer DID MUST be {@code did:web} (not {@code did:key}).</li>
 *   <li>Trust chain — JWK MUST carry an {@code x5u} pointing at a certificate chain whose
 *       leaf is registered with the Loire trust anchor registry. Inline {@code x5c} is
 *       rejected because full chain building / revocation checking is not implemented.</li>
 * </ul>
 *
 * <p>Kept separate from the generic VP/VC pipeline so the main strategy is format-agnostic
 * and additional credential families can be plugged in as siblings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoirePolicyEnforcer {

  /**
   * Profile ID declared in {@code trustframeworks/gaia-x-2511/framework.yaml}.
   * Used to look up the governing trust framework family in the registry.
   */
  private static final String PROFILE_ID = "gaia-x-2511";

  /**
   * Property key inside the bundle's {@code properties} map carrying the trust anchor
   * registry URL. Read from the framework bundle at runtime so the value lives with the
   * rest of the bundle's configuration rather than in application-level properties.
   */
  private static final String PROPERTY_TRUST_ANCHOR_URL = "trust_anchor_url";

  private final TrustFrameworkRegistry trustFrameworkRegistry;
  private final TrustFrameworkService trustFrameworkService;
  private final ObjectMapper objectMapper;

  @Value("${federated-catalogue.verification.http-timeout:5000}")
  private int httpTimeout;

  private RestTemplate rest;

  @PostConstruct
  private void initialize() {
    HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
    factory.setConnectTimeout(httpTimeout);
    factory.setConnectionRequestTimeout(httpTimeout);
    rest = new RestTemplate(factory);
  }

  void setRest(RestTemplate rest) {
    this.rest = rest;
  }

  /**
   * Applies Loire policies to a JWT credential body when the Loire framework is enabled.
   * No-op otherwise. Safe to call for any credential format — callers do not need to
   * pre-check format or enablement.
   *
   * @param compactJwt   the original compact JWT body
   * @param jwtValidator validator produced by JWT signature verification; {@code null} when
   *                     signature verification was skipped
   */
  public void enforceIfApplicable(String compactJwt, Validator jwtValidator) {
    if (!isEnabled()) {
      return;
    }
    enforceDidWebRestriction(compactJwt);
    if (jwtValidator != null) {
      enforceTrustChain(jwtValidator);
    }
  }

  /**
   * Returns the trust framework family that governs Loire credentials (e.g. {@code "gaia-x"}),
   * derived from the bundle registry at runtime. Empty if the bundle is not loaded.
   */
  private Optional<String> frameworkFamily() {
    return trustFrameworkRegistry.getBundle(PROFILE_ID)
        .map(b -> b.config().family());
  }

  /**
   * Returns the trust anchor registry URL declared in the bundle's properties map.
   * Empty if the bundle is not loaded or the property is absent or blank.
   */
  private Optional<String> trustAnchorUrl() {
    return trustFrameworkRegistry.getBundle(PROFILE_ID)
        .map(b -> b.config().properties().get(PROPERTY_TRUST_ANCHOR_URL))
        .filter(s -> !s.isBlank());
  }

  /**
   * Returns true when the framework governing Loire credentials is both registered (its
   * bundle is loaded) and enabled in persistence.
   */
  private boolean isEnabled() {
    return frameworkFamily()
        .map(trustFrameworkService::isEnabled)
        .orElse(false);
  }

  private void enforceTrustChain(Validator jwtValidator) {
    String publicKeyJson = jwtValidator.getPublicKey();
    if (publicKeyJson == null) {
      throw new VerificationException(
          "Loire trust chain: publicKeyJwk is required but not available");
    }
    try {
      JsonNode jwkNode = objectMapper.readTree(publicKeyJson);
      boolean hasX5c = jwkNode.has("x5c") && !jwkNode.get("x5c").isEmpty();
      boolean hasX5u = jwkNode.has("x5u") && !jwkNode.get("x5u").asText().isBlank();
      if (!hasX5c && !hasX5u) {
        throw new VerificationException(
            "Loire trust chain: publicKeyJwk must contain x5c or x5u certificate chain "
                + "(ICAM 24.07 mandatory trust anchor). kid: " + jwtValidator.getDidURI());
      }
      if (hasX5c) {
        rejectX5cChain(jwtValidator.getDidURI());
      }
      String x5uUrl = jwkNode.get("x5u").asText();
      validateTrustAnchorChain(x5uUrl);

      log.debug("enforceTrustChain; trust chain validated for kid: {}",
          jwtValidator.getDidURI());
    } catch (VerificationException | ClientException ex) {
      throw ex;
    } catch (Exception ex) {
      throw new VerificationException(
          "Loire trust chain validation failed: " + ex.getMessage(), ex);
    }
  }

  /**
   * Inline {@code x5c} chains are not yet supported. ICAM 24.07 permits {@code x5c}, but
   * full trust chain verification (chain building, Trust Anchor Registry lookup, revocation)
   * is not implemented; accepting it with expiry-only checks would silently mask broken
   * trust chains. Callers must use {@code x5u} (Trust Anchor Registry URL) instead.
   */
  private void rejectX5cChain(String kid) {
    throw new ClientException(
        "x5c certificate chain validation is not supported. "
            + "Use x5u (Trust Anchor Registry URL) instead. kid: " + kid);
  }

  /**
   * Fetches the certificate chain from the given x5u URL, validates certificate expiry,
   * and checks the URI against the Loire trust anchor registry. The registry URL is read
   * from the active framework bundle's properties.
   *
   * <p>Note: does not perform PKIX path validation (RFC 5280) or revocation checking.
   * ICAM 24.07 requires x5c/x5u presence (MUST) and x5u trust anchor resolution (SHOULD).
   */
  @SuppressWarnings({"unchecked", "rawtypes"}) // RestTemplate#postForEntity returns raw Map
  private Instant validateTrustAnchorChain(String uri) throws VerificationException {
    log.debug("validateTrustAnchorChain.enter; got uri: {}", uri);
    String pem = rest.getForObject(uri, String.class);
    InputStream certStream = new ByteArrayInputStream(
        Objects.requireNonNull(pem).getBytes(StandardCharsets.UTF_8));
    List<X509Certificate> certs;
    try {
      CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
      certs = (List<X509Certificate>) certFactory.generateCertificates(certStream);
    } catch (CertificateException ex) {
      log.warn("validateTrustAnchorChain; certificate error: {}", ex.getMessage(), ex);
      throw new VerificationException("Signatures error; " + ex.getMessage(), ex);
    }

    X509Certificate relevant = null;
    for (X509Certificate cert : certs) {
      try {
        cert.checkValidity();
        if (relevant == null || relevant.getNotAfter().before(cert.getNotAfter())) {
          relevant = cert;
        }
      } catch (Exception ex) {
        log.warn("validateTrustAnchorChain; check validity error: {}", ex.getMessage());
        throw new VerificationException("Signatures error; " + ex.getMessage(), ex);
      }
    }

    String trustAnchorUrl = trustAnchorUrl().orElseThrow(() -> {
      log.warn("validateTrustAnchorChain; Loire framework enabled but bundle declares no trust_anchor_url");
      return new VerificationException(
          "Signatures error; Loire framework enabled but bundle declares no trust_anchor_url");
    });
    try {
      ResponseEntity<Map> resp = rest.postForEntity(trustAnchorUrl, Map.of("uri", uri), Map.class);
      if (!resp.getStatusCode().is2xxSuccessful()) {
        log.info("validateTrustAnchorChain; Trust anchor is not set in the registry. URI: {}", uri);
        throw new VerificationException(
            "Signatures error; trust anchor is not registered in the Loire registry. URI: " + uri);
      }
    } catch (VerificationException ex) {
      throw ex;
    } catch (Exception ex) {
      log.warn("validateTrustAnchorChain; trust anchor error: {}", ex.getMessage(), ex);
      throw new VerificationException("Signatures error; " + ex.getMessage(), ex);
    }
    Instant exp = relevant == null ? null : relevant.getNotAfter().toInstant();
    log.debug("validateTrustAnchorChain.exit; returning: {}", exp);
    return exp;
  }

  /**
   * Enforces the Loire DID method restriction: only {@code did:web} is accepted
   * (ICAM 24.07). Both JWT {@code iss} and payload {@code issuer} are validated when
   * present — prevents spoofing via an injected issuer claim.
   */
  private void enforceDidWebRestriction(String compactJwt) {
    try {
      JWTClaimsSet claims = SignedJWT.parse(compactJwt).getJWTClaimsSet();
      String iss = claims.getIssuer();
      String issuerClaim = claims.getStringClaim("issuer");
      if (iss != null) {
        validateDidWeb(iss);
      }
      if (issuerClaim != null && !issuerClaim.equals(iss)) {
        validateDidWeb(issuerClaim);
      }
      if (iss == null && issuerClaim == null) {
        throw new VerificationException(
            "Loire DID method restriction: no issuer found in JWT iss or payload issuer "
                + "(ICAM 24.07 requires did:web)");
      }
    } catch (ParseException ex) {
      throw new VerificationException(
          "Loire DID method restriction: JWT claims parse error: " + ex.getMessage(), ex);
    }
  }

  private void validateDidWeb(String did) {
    if (did.startsWith("did:key")) {
      throw new VerificationException(
          "Loire DID method restriction: did:key is not accepted "
              + "(ICAM 24.07 requires did:web with JWK + x5c/x5u). Issuer: " + did);
    }
    if (!did.startsWith("did:web:")) {
      throw new VerificationException(
          "Loire DID method restriction: only did:web is accepted "
              + "(ICAM 24.07). Found: " + did);
    }
  }
}
