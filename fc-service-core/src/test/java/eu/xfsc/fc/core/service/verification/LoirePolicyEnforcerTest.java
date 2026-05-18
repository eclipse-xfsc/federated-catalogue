package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEObjectType;
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
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class LoirePolicyEnforcerTest {

  private static final String PROFILE_ID = "gaia-x-2511";
  private static final String FAMILY = "gaia-x";
  private static final String TRUST_ANCHOR_URL = "https://registry.example.com/trust-anchors";
  private static final String DID_WEB_ISSUER = "did:web:example.com";
  private static final String DID_KEY_ISSUER = "did:key:z6MkExample";
  private static final String DID_ETHR_ISSUER = "did:ethr:0x1234";

  private static String TEST_CERT_PEM;
  private static JWSSigner signer;

  @Mock
  private TrustFrameworkRegistry trustFrameworkRegistry;

  @Mock
  private TrustFrameworkService trustFrameworkService;

  @Mock
  private RestTemplate restTemplate;

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private LoirePolicyEnforcer enforcer;

  @BeforeAll
  static void initKeys() throws Exception {
    OctetKeyPair jwk = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-key").generate();
    signer = new Ed25519Signer(jwk);
    TEST_CERT_PEM = Files.readString(
        Path.of(LoirePolicyEnforcerTest.class.getClassLoader().getResource("cert.ss.pem").toURI()));
  }

  @BeforeEach
  void setUp() {
    enforcer.setRest(restTemplate);
  }

  @Test
  void enforceIfApplicable_frameworkBundleNotRegistered_noOp() {
    when(trustFrameworkRegistry.getBundle(PROFILE_ID)).thenReturn(Optional.empty());

    assertDoesNotThrow(() -> enforcer.enforceIfApplicable(buildJwt(DID_KEY_ISSUER, null), null));

    verify(restTemplate, never()).getForObject(any(), any());
  }

  @Test
  void enforceIfApplicable_frameworkDisabled_noOp() throws Exception {
    stubEnabled(false);
    String invalidJwt = buildJwt(DID_KEY_ISSUER, null); // would fail DID check if enabled

    assertDoesNotThrow(() -> enforcer.enforceIfApplicable(invalidJwt, null));
  }

  @Test
  void enforceIfApplicable_issDidWeb_nullValidator_passes() {
    stubEnabled(true);

    assertDoesNotThrow(() -> enforcer.enforceIfApplicable(buildJwt(DID_WEB_ISSUER, null), null));
  }

  @Test
  void enforceIfApplicable_issDidKey_throwsVerificationException() {
    stubEnabled(true);

    assertThrows(VerificationException.class,
        () -> enforcer.enforceIfApplicable(buildJwt(DID_KEY_ISSUER, null), null));
  }

  @Test
  void enforceIfApplicable_issUnknownDidMethod_throwsVerificationException() {
    stubEnabled(true);

    assertThrows(VerificationException.class,
        () -> enforcer.enforceIfApplicable(buildJwt(DID_ETHR_ISSUER, null), null));
  }

  @Test
  void enforceIfApplicable_nullIssWithDidWebIssuerClaim_passes() {
    stubEnabled(true);

    assertDoesNotThrow(() -> enforcer.enforceIfApplicable(buildJwt(null, DID_WEB_ISSUER), null));
  }

  @Test
  void enforceIfApplicable_issAndIssuerClaimBothDidWeb_passes() {
    stubEnabled(true);

    assertDoesNotThrow(() -> enforcer.enforceIfApplicable(buildJwt(DID_WEB_ISSUER, DID_WEB_ISSUER), null));
  }

  @Test
  void enforceIfApplicable_issDidWebIssuerClaimDidKey_throwsVerificationException() {
    stubEnabled(true);

    assertThrows(VerificationException.class,
        () -> enforcer.enforceIfApplicable(buildJwt(DID_WEB_ISSUER, DID_KEY_ISSUER), null));
  }

  @Test
  void enforceIfApplicable_noIssuerAnywhere_throwsVerificationException() {
    stubEnabled(true);

    assertThrows(VerificationException.class,
        () -> enforcer.enforceIfApplicable(buildJwtNoIssuer(), null));
  }

  @Test
  void enforceIfApplicable_malformedJwt_throwsVerificationException() {
    stubEnabled(true);

    assertThrows(VerificationException.class,
        () -> enforcer.enforceIfApplicable("not.a.jwt", null));
  }

  @Test
  void enforceIfApplicable_validatorWithNullPublicKey_throwsVerificationException() {
    stubEnabled(true);
    var validator = new Validator(DID_WEB_ISSUER, null, null);

    assertThrows(VerificationException.class,
        () -> enforcer.enforceIfApplicable(buildJwt(DID_WEB_ISSUER, null), validator));
  }

  @Test
  void enforceIfApplicable_jwkWithX5cOnly_throwsClientException() {
    stubEnabled(true);
    String jwk = """
        {"kty":"EC","x5c":["MIIBxxx"]}""";
    var validator = new Validator(DID_WEB_ISSUER, jwk, null);

    assertThrows(ClientException.class,
        () -> enforcer.enforceIfApplicable(buildJwt(DID_WEB_ISSUER, null), validator));
  }

  @Test
  void enforceIfApplicable_jwkWithNoX5cOrX5u_throwsVerificationException() {
    stubEnabled(true);
    String jwk = """
        {"kty":"EC","crv":"P-256"}""";
    var validator = new Validator(DID_WEB_ISSUER, jwk, null);

    assertThrows(VerificationException.class,
        () -> enforcer.enforceIfApplicable(buildJwt(DID_WEB_ISSUER, null), validator));
  }

  @Test
  void enforceIfApplicable_jwkX5uTrustAnchorRegistered_passes() {
    stubEnabled(true);
    String x5uUrl = "https://example.com/chain.pem";
    String jwk = """
        {"kty":"EC","x5u":"%s"}""".formatted(x5uUrl);
    var validator = new Validator(DID_WEB_ISSUER, jwk, null);
    when(restTemplate.getForObject(x5uUrl, String.class)).thenReturn(TEST_CERT_PEM);
    when(restTemplate.postForEntity(eq(TRUST_ANCHOR_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.ok(Map.of()));

    assertDoesNotThrow(() -> enforcer.enforceIfApplicable(buildJwt(DID_WEB_ISSUER, null), validator));
  }

  @Test
  void enforceIfApplicable_jwkX5uTrustAnchorNotRegistered_throwsVerificationException() {
    stubEnabled(true);
    String x5uUrl = "https://example.com/chain.pem";
    String jwk = """
        {"kty":"EC","x5u":"%s"}""".formatted(x5uUrl);
    var validator = new Validator(DID_WEB_ISSUER, jwk, null);
    when(restTemplate.getForObject(x5uUrl, String.class)).thenReturn(TEST_CERT_PEM);
    when(restTemplate.postForEntity(eq(TRUST_ANCHOR_URL), any(), eq(Map.class)))
        .thenReturn(ResponseEntity.notFound().build());

    assertThrows(VerificationException.class,
        () -> enforcer.enforceIfApplicable(buildJwt(DID_WEB_ISSUER, null), validator));
  }

  private void stubEnabled(boolean enabled) {
    FrameworkBundleConfig config = new FrameworkBundleConfig(
        PROFILE_ID, FAMILY, "https://w3id.org/gaia-x/2511#",
        null, Map.of(), Map.of("trust_anchor_url", TRUST_ANCHOR_URL));
    TrustFrameworkBundle bundle = new TrustFrameworkBundle(config, null, null);
    when(trustFrameworkRegistry.getBundle(PROFILE_ID)).thenReturn(Optional.of(bundle));
    when(trustFrameworkService.isEnabled(FAMILY)).thenReturn(enabled);
  }

  private static String buildJwt(String iss, String issuerClaim) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .type(new JOSEObjectType("vc+jwt"))
        .keyID(DID_WEB_ISSUER + "#key-1")
        .build();
    JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder();
    if (iss != null) {
      claims.issuer(iss);
    }
    if (issuerClaim != null) {
      claims.claim("issuer", issuerClaim);
    }
    SignedJWT jwt = new SignedJWT(header, claims.build());
    jwt.sign(signer);
    return jwt.serialize();
  }

  private static String buildJwtNoIssuer() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .type(new JOSEObjectType("vc+jwt"))
        .keyID(DID_WEB_ISSUER + "#key-1")
        .build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("did:web:subject.example.com").build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }
}
