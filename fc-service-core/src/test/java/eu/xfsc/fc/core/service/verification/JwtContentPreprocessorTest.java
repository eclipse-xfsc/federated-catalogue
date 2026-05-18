package eu.xfsc.fc.core.service.verification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtContentPreprocessorTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String JWT_BODY = "eyJhbGciOiJFZERTQSJ9.payload.sig";
  private static final String JSON_LD_BODY = "{\"@context\": [\"https://www.w3.org/ns/credentials/v2\"]}";

  private static OctetKeyPair jwk;
  private static JWSSigner signer;

  private final JwtContentPreprocessor preprocessor = new JwtContentPreprocessor();

  @BeforeAll
  static void initKeys() throws Exception {
    jwk = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-key").generate();
    signer = new Ed25519Signer(jwk);
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vc+ld+json+jwt");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vpJwtContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vp+ld+json+jwt");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_w3cVcJwtContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vc+jwt");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_w3cVpJwtContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vp+jwt");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeWithCharsetWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vc+ld+json+jwt; charset=utf-8");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeUppercaseWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "Application/VC+LD+JSON+JWT");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeWithNonJwtBody_throwsClientException() {
    var content = new ContentAccessorDirect(JSON_LD_BODY, "application/vc+ld+json+jwt");

    assertThrows(ClientException.class, () -> preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcJwtContentTypeWithEmptyBody_throwsClientException() {
    var content = new ContentAccessorDirect("", "application/vc+ld+json+jwt");

    assertThrows(ClientException.class, () -> preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_vcLdJsonContentTypeWithJwtBody_throwsClientException() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vc+ld+json");

    ClientException ex = assertThrows(ClientException.class,
        () -> preprocessor.isJwtWrapped(content));

    assertTrue(ex.getMessage().contains("application/vc+jwt"), ex.getMessage());
  }

  @Test
  void isJwtWrapped_vpLdJsonContentTypeWithJwtBody_throwsClientException() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/vp+ld+json");

    assertThrows(ClientException.class, () -> preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_ldJsonContentTypeWithJwtBody_throwsClientException() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/ld+json");

    assertThrows(ClientException.class, () -> preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_jsonContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY, "application/json");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_nullContentTypeWithJwtBody_returnsTrue() {
    var content = new ContentAccessorDirect(JWT_BODY);

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void isJwtWrapped_malformedContentTypeWithJwtBody_returnsTrue() {
    // A header starting with ';' produces an empty type after split — falls through to body sniff
    var content = new ContentAccessorDirect(JWT_BODY, "; charset=utf-8");

    assertTrue(preprocessor.isJwtWrapped(content));
  }

  @Test
  void unwrap_plainJsonBody_returnsOriginalContentUnchanged() {
    ContentAccessor content = new ContentAccessorDirect(JSON_LD_BODY);

    ContentAccessor result = preprocessor.unwrap(content);

    assertSame(content, result);
  }

  @Test
  void unwrap_emptyBody_returnsOriginalContentUnchanged() {
    ContentAccessor content = new ContentAccessorDirect("");

    ContentAccessor result = preprocessor.unwrap(content);

    assertSame(content, result);
  }

  @Test
  void unwrap_whitespaceOnlyBody_returnsOriginalContentUnchanged() {
    ContentAccessor content = new ContentAccessorDirect("   ");

    ContentAccessor result = preprocessor.unwrap(content);

    assertSame(content, result);
  }

  @Test
  void unwrap_jwtWithVcClaim_returnsJsonLdWithCredentialFields() throws Exception {
    String jwt = buildVcJwt();
    ContentAccessor content = new ContentAccessorDirect(jwt);

    ContentAccessor result = preprocessor.unwrap(content);

    String json = result.getContentAsString();
    JsonNode root = MAPPER.readTree(json);
    assertNotNull(root.get("@context"), "unwrapped payload must have @context");
    assertNotNull(root.get("credentialSubject"), "unwrapped payload must have credentialSubject");
  }

  @Test
  void unwrap_jwtWithVcClaim_returnedContentTypeIsNull() throws Exception {
    ContentAccessor content = new ContentAccessorDirect(buildVcJwt());

    ContentAccessor result = preprocessor.unwrap(content);

    assertNull(result.getContentType());
  }

  @Test
  void unwrap_jwtWithVpClaimAndNoVcClaim_returnsJsonLdWithPresentationFields() throws Exception {
    String jwt = buildVpJwt();
    ContentAccessor content = new ContentAccessorDirect(jwt);

    ContentAccessor result = preprocessor.unwrap(content);

    String json = result.getContentAsString();
    JsonNode root = MAPPER.readTree(json);
    assertNotNull(root.get("@context"), "unwrapped payload must have @context");
    assertNotNull(root.get("verifiableCredential"), "unwrapped payload must have verifiableCredential");
  }

  @Test
  void unwrap_jwtBodyWithSurroundingWhitespace_stripsAndParsesAsVc() throws Exception {
    // strip() is applied before the eyJ check, so whitespace-padded JWTs must also be unwrapped
    String jwt = "  " + buildVcJwt() + "  ";
    ContentAccessor content = new ContentAccessorDirect(jwt);

    ContentAccessor result = preprocessor.unwrap(content);

    assertNotNull(MAPPER.readTree(result.getContentAsString()).get("@context"));
  }

  @Test
  void unwrap_jwtWithNoVcOrVpClaim_throwsClientException() throws Exception {
    // Both parsers return null (no vc/vp claim), NullPointerException is caught, ClientException thrown
    ContentAccessor content = new ContentAccessorDirect(buildPlainJwt());

    assertThrows(ClientException.class, () -> preprocessor.unwrap(content));
  }

  @Test
  void unwrap_jwtPrefixWithoutCompactSerializationDots_throwsClientException() {
    // Starts with eyJ but is not a three-part compact JWS — SignedJWT.parse() fails in both parsers
    ContentAccessor content = new ContentAccessorDirect("eyJhbGciOiJFZERTQSJ9");

    assertThrows(ClientException.class, () -> preprocessor.unwrap(content));
  }

  @Test
  void unwrap_loireStyleJwtWithRootLevelClaims_throwsClientException() throws Exception {
    // Loire JWTs embed VC fields directly at the payload root, not inside a 'vc' wrapper claim.
    // The danubetech library (used here) only recognises the wrapper-claim format, so both parsers
    // return null and unwrap() throws. Currently unreachable in production — Loire JWTs are routed
    // to LoireCredentialProcessor before reaching this preprocessor. This will need to change when
    // the two JWT processors are unified.
    ContentAccessor content = new ContentAccessorDirect(buildLoireStyleVcJwt());

    assertThrows(ClientException.class, () -> preprocessor.unwrap(content));
  }

  /**
   * Builds a signed JWT with a {@code vc} claim containing a minimal VC v2 credential.
   */
  private String buildVcJwt() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("did:web:example.com#test-key").build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("vc", Map.of(
            "@context", List.of(VC_20_CONTEXT),
            "type", List.of("VerifiableCredential"),
            "credentialSubject", Map.of("id", "did:web:example.com")
        ))
        .build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }

  /**
   * Builds a signed JWT with a {@code vp} claim but no {@code vc} claim.
   */
  private String buildVpJwt() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("did:web:example.com#test-key").build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("vp", Map.of(
            "@context", List.of(VC_20_CONTEXT),
            "type", List.of("VerifiablePresentation"),
            "holder", "did:web:example.com",
            "verifiableCredential", List.of()
        ))
        .build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }

  /**
   * Builds a signed JWT with neither a {@code vc} nor a {@code vp} claim.
   */
  private String buildPlainJwt() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("did:web:example.com#test-key").build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .subject("did:web:subject.example.com")
        .build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }

  /**
   * Builds a Loire-style JWT: VC fields at the payload root, no {@code vc} wrapper claim.
   * Mirrors the fixture structure used in {@link LoireJwtParserTest}.
   */
  private String buildLoireStyleVcJwt() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("did:web:example.com#test-key").build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("@context", List.of(VC_20_CONTEXT))
        .claim("type", List.of("VerifiableCredential"))
        .claim("credentialSubject", Map.of("id", "did:web:example.com"))
        .claim("validFrom", "2026-01-01T00:00:00Z")
        .build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }
}
