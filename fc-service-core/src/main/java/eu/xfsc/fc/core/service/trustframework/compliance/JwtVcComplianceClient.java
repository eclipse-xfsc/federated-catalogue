package eu.xfsc.fc.core.service.trustframework.compliance;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import eu.xfsc.fc.core.exception.ServiceUnavailableException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.ContentAccessor;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * Generic {@link TrustFrameworkClient} for compliance services that follow the REST + JWT-VC
 * exchange shape: the catalogue POSTs a Verifiable Presentation JWT as the request body and the
 * service returns either {@code HTTP 201} with a Verifiable Credential JWT (compliant) or
 * {@code HTTP 400} with an error body (non-compliant).
 *
 * <p>What is generic and what is framework-specific:
 * <ul>
 *   <li><strong>Generic (this class):</strong> the wire shape itself — POST a VP JWT, expect a
 *       201/400 outcome, parse the response as a JWT, map {@code exp} to validity.</li>
 *   <li><strong>Framework-specific (bundle):</strong> the base URL ({@code service_url}) and the
 *       endpoint path ({@code compliance_path}) live in the bundle's {@code properties} block, so
 *       a new framework that shares this wire shape needs no Java code — only a new bundle.</li>
 * </ul>
 *
 * <p>The Gaia-X Loire / GXDCH compliance endpoint is the reference deployment for this shape;
 * additional bundles that exercise this client are expected as more frameworks standardise on
 * the same exchange.
 *
 * <p>The {@code vcid} query-parameter name is currently hard-wired because every known bundle
 * uses the same convention; lifting it into the bundle properties is straightforward when a
 * framework adopts a different name.
 *
 * <p>Note on signature verification: the returned compliance credential JWT is not signature-checked
 * here. It arrives over HTTPS directly from the compliance service, so the transport layer
 * provides authenticity. The raw JWT is stored verbatim so downstream relying parties can verify
 * it independently if required.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtVcComplianceClient implements TrustFrameworkClient {

  private static final String CLIENT_TYPE = "jwt-vc-compliance";
  private static final String VCID_QUERY_PARAM = "vcid";

  private final ConcurrentHashMap<Integer, RestTemplate> restTemplateCache = new ConcurrentHashMap<>();

  @Override
  public String clientType() {
    return CLIENT_TYPE;
  }

  /**
   * Submits the VP JWT to the configured compliance endpoint and returns the outcome.
   *
   * <p>Short-circuits to {@link UnverifiableAttestation} with
   * {@link FailureCategory#UNVERIFIABLE_ATTESTATION} when the VP JWT payload has no {@code id}
   * claim, without sending any HTTP request.
   *
   * <p>On HTTP 201, the response body is a compliance credential JWT mapped to
   * {@link IssuedAttestation}. A 201 body that is not a parseable JWT maps to
   * {@link UnverifiableAttestation} rather than returning null-field attestation fields.
   * On HTTP 400, the asset is non-compliant and an {@link UnverifiableAttestation} is returned.
   * HTTP 5xx and I/O exceptions bubble to the orchestrator, which maps them to
   * {@link eu.xfsc.fc.core.exception.ServiceUnavailableException} /
   * {@link eu.xfsc.fc.core.exception.TimeoutException}.
   *
   * @param credential the VP JWT to submit
   * @param config     profile configuration providing the service URL, compliance path, and timeout
   * @return the compliance check outcome; never {@code null}
   */
  @Override
  public ComplianceCheckOutcome check(ContentAccessor credential, TrustFrameworkProfileConfig config) {
    String vpJwt = credential.getContentAsString();
    String assetId = extractJwtClaim(vpJwt, "id");
    if (assetId.isBlank()) {
      return new UnverifiableAttestation(
          FailureCategory.UNVERIFIABLE_ATTESTATION,
          vpJwt,
          "VP JWT has no 'id' claim"
      );
    }

    String serviceUrl = config.serviceUrl().replaceAll("/+$", "");
    // URI.create() preserves existing percent-encoding; Apache HttpClient uses raw form as-is.
    URI uri = URI.create(serviceUrl + config.compliancePath()
        + "?" + VCID_QUERY_PARAM + "=" + URLEncoder.encode(assetId, StandardCharsets.UTF_8));

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_PLAIN);
    HttpEntity<String> request = new HttpEntity<>(vpJwt, headers);

    RestTemplate rest = buildRestTemplate(config.timeoutSeconds());
    try {
      var response = rest.postForEntity(uri, request, String.class);
      return parseComplianceJwt(response.getBody());
    } catch (HttpClientErrorException.BadRequest e) {
      return new UnverifiableAttestation(
          FailureCategory.UNVERIFIABLE_ATTESTATION,
          e.getResponseBodyAsString(),
          e.getStatusText()
      );
    } catch (ResourceAccessException e) {
      if (e.getCause() instanceof SocketTimeoutException) {
        throw new TimeoutException("Compliance service timed out: " + e.getMessage());
      }
      log.error("Compliance service unreachable", e);
      throw new ServiceUnavailableException("Compliance service unreachable: " + e.getMessage(), e);
    } catch (HttpServerErrorException e) {
      log.error("Compliance service returned server error", e);
      throw new ServiceUnavailableException("Compliance service error: " + e.getStatusCode(), e);
    }
  }

  private RestTemplate buildRestTemplate(int timeoutSeconds) {
    return restTemplateCache.computeIfAbsent(timeoutSeconds, t -> {
      var timeout = Duration.ofSeconds(t);
      var factory = new HttpComponentsClientHttpRequestFactory();
      factory.setConnectTimeout(timeout);
      factory.setReadTimeout(timeout);
      return new RestTemplate(factory);
    });
  }

  private String extractJwtClaim(String jwt, String claim) {
    try {
      JWTClaimsSet claims = readJwtPayload(jwt);
      String value = claims.getStringClaim(claim);
      return value != null ? value : "";
    } catch (Exception e) {
      log.error("Failed to extract claim '{}' from JWT", claim, e);
      return "";
    }
  }

  private ComplianceCheckOutcome parseComplianceJwt(String jwt) {
    try {
      JWTClaimsSet claims = readJwtPayload(jwt);
      Instant validUntil = claims.getExpirationTime() != null
          ? claims.getExpirationTime().toInstant()
          : null;
      return new IssuedAttestation(jwt, validUntil);
    } catch (Exception e) {
      log.warn("Failed to parse compliance credential JWT", e);
      return new UnverifiableAttestation(
          FailureCategory.UNVERIFIABLE_ATTESTATION,
          jwt,
          "Compliance credential is not a parseable JWT"
      );
    }
  }

  private JWTClaimsSet readJwtPayload(String jwt) throws ParseException {
    return JWTParser.parse(jwt).getJWTClaimsSet();
  }
}
