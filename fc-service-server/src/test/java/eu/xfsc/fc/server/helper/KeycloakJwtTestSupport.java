package eu.xfsc.fc.server.helper;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static eu.xfsc.fc.server.helper.FileReaderHelper.getMockFileDataAsString;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.lang.JoseException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.github.tomakehurst.wiremock.client.WireMock;

import eu.xfsc.fc.api.generated.model.User;

/**
  * Shared test support for WireMock-backed OIDC discovery + real, RSA-signed JWT minting.
  *
  * <p>Provides helpers to register OIDC discovery and JWKS stubs via WireMock, and to mint
  * real JWT tokens signed with a test RSA key. This allows tests to exercise the production
  * {@code CustomJwtAuthenticationConverter} pipeline, not a test double.</p>
  */
public class KeycloakJwtTestSupport {

  private static final String CLAIM_TYP = "typ";
  private static final String CLAIM_AZP = "azp";
  private static final String CLAIM_RESOURCE_ACCESS = "resource_access";
  private static final String CLAIM_ROLES = "roles";
  private static final String CLAIM_SCOPE = "scope";
  private static final String CLAIM_PARTICIPANT_ID = "participant_id";

  private RsaJsonWebKey rsaKey;
  private String keycloakBaseUrl;

  /**
    * Creates a new support instance for the given Keycloak base URL.
    *
    * <p>The instance maintains a single RSA key pair across all JWT minting calls for
    * this Keycloak issuer, so that the stub JWKS endpoint serves the same key that
    * signs all tokens minted by this instance.</p>
    */
  public KeycloakJwtTestSupport(String keycloakBaseUrl) {
    this.keycloakBaseUrl = keycloakBaseUrl;
  }

  /**
    * Registers WireMock stubs for OIDC discovery and JWKS endpoints, and initializes
    * the RSA key pair used to sign JWTs. The key ID is set to the provided {@code keyId}.
    *
    * <p>After calling this method, the instance is ready to mint signed JWTs via
    * {@link #mintToken(String, String, String, Map)} or convenience methods.</p>
    *
    * @param keyId the JWK Key ID to use in the JWKS stub (e.g. "k1", "rbac-test-k1")
    * @throws JoseException if RSA key generation fails
    * @throws IOException if the openid-configs.json fixture cannot be read
    */
  public void setUpOidcAndJwks(String keyId) throws JoseException, IOException {
    try {
      rsaKey = RsaJwkGenerator.generateJwk(2048);
    } catch (JoseException ex) {
      throw new JoseException("Failed to generate RSA test signing key", ex);
    }
    rsaKey.setKeyId(keyId);
    rsaKey.setAlgorithm(AlgorithmIdentifiers.RSA_USING_SHA256);
    rsaKey.setUse("sig");

    String openidConfig = getMockFileDataAsString("openid-configs.json")
        .replace("keycloakBaseUrl", keycloakBaseUrl);

    stubFor(WireMock.get(urlEqualTo("/auth/realms/gaia-x/.well-known/openid-configuration"))
        .willReturn(aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(openidConfig)));
    stubFor(WireMock.get(urlEqualTo("/auth/realms/gaia-x/protocol/openid-connect/certs"))
        .willReturn(aResponse()
            .withHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
            .withBody(new JsonWebKeySet(rsaKey).toJson())));
  }

  /**
    * Mints a signed JWT with the given base claims, issuer, subject, and audience.
    *
    * <p>The returned JWT is signed with the RSA key initialized by {@link #setUpOidcAndJwks},
    * and will be successfully decoded by the production {@code CustomJwtAuthenticationConverter}
    * when routed through the WireMock JWKS stub.</p>
    *
    * @param resourceId the resource/client ID to use in the resource_access claim key
    * @param roles the roles to grant in resource_access.<resourceId>.roles
    * @param participantId optional participant_id claim (may be null or empty)
    * @param additionalClaims optional additional claims to merge into the JWT (may be null)
    * @return a signed JWT string
    * @throws JoseException if JWT signing fails
    */
  public String mintToken(String resourceId, List<String> roles, String participantId,
      Map<String, Object> additionalClaims) throws JoseException {
    if (rsaKey == null) {
      throw new IllegalStateException(
          "RSA key not initialized; call setUpOidcAndJwks(...) first");
    }

    JwtClaims claims = new JwtClaims();
    claims.setJwtId(UUID.randomUUID().toString());
    claims.setExpirationTimeMinutesInTheFuture(10);
    claims.setNotBeforeMinutesInThePast(0);
    claims.setIssuedAtToNow();
    claims.setAudience("account");
    claims.setIssuer(String.format("%s/auth/realms/gaia-x", keycloakBaseUrl));
    claims.setSubject(UUID.randomUUID().toString());
    claims.setClaim(CLAIM_TYP, "Bearer");
    claims.setClaim(CLAIM_AZP, resourceId);
    claims.setClaim(CLAIM_RESOURCE_ACCESS, Map.of(resourceId, Map.of(CLAIM_ROLES, roles)));
    claims.setClaim(CLAIM_SCOPE, "openid gaia-x");

    if (participantId != null && !participantId.isEmpty()) {
      claims.setClaim(CLAIM_PARTICIPANT_ID, participantId);
    }

    if (additionalClaims != null) {
      additionalClaims.forEach(claims::setClaim);
    }

    JsonWebSignature jws = new JsonWebSignature();
    jws.setPayload(claims.toJson());
    jws.setKey(rsaKey.getPrivateKey());
    jws.setKeyIdHeaderValue(rsaKey.getKeyId());
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256);
    jws.setHeader("typ", "JWT");
    return jws.getCompactSerialization();
  }

  /**
    * Convenience method: mints a token with the given roles and participant_id, no additional claims.
    *
    * @param resourceId the resource/client ID to use in the resource_access claim key
    * @param roles the roles to grant
    * @param participantId the participant_id claim
    * @return a signed JWT string
    * @throws JoseException if JWT signing fails
    */
  public String mintToken(String resourceId, List<String> roles, String participantId)
      throws JoseException {
    return mintToken(resourceId, roles, participantId, null);
  }

  /**
    * Convenience method: mints a token for a User with profile claims (preferred_username,
    * given_name, family_name) and the given roles.
    *
    * <p>Suitable for UsersControllerTest-style tokens that carry user profile info.</p>
    *
    * @param resourceId the resource/client ID to use in the resource_access claim key
    * @param roles the roles to grant
    * @param user the User object to extract email, firstName, lastName from
    * @return a signed JWT string
    * @throws JoseException if JWT signing fails
    */
  public String mintTokenForUser(String resourceId, List<String> roles, User user)
      throws JoseException {
    Map<String, Object> profileClaims = Map.of(
        "email_verified", true,
        "preferred_username", user.getEmail(),
        "given_name", user.getFirstName(),
        "family_name", user.getLastName()
    );
    return mintToken(resourceId, roles, null, profileClaims);
  }

}
