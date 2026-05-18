package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
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
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoireCredentialProcessorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static JWSSigner signer;

  @Mock
  private LoireJwtParser loireJwtParser;

  @Mock
  private LoirePolicyEnforcer loirePolicyEnforcer;

  @Mock
  private JwtSignatureVerifier jwtSignatureVerifier;

  @InjectMocks
  private LoireCredentialProcessor processor;

  @BeforeAll
  static void initKeys() throws Exception {
    OctetKeyPair jwk = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-key").generate();
    signer = new Ed25519Signer(jwk);
  }

  @Test
  void getFormat_returnsLoireFormat() {
    assertEquals(CredentialFormat.GAIAX_V2_LOIRE, processor.getFormat());
  }

  @Test
  void match_noJwt_returnsEmpty() {
    var ctx = new DetectionContext("{}", null, null);

    assertEquals(Optional.empty(), processor.match(ctx));
  }

  @Test
  void match_vcJwtTypWithTopLevelContext_returnsLoire() throws Exception {
    var ctx = buildLoireJwtContext("vc+jwt");

    assertEquals(Optional.of(CredentialFormat.GAIAX_V2_LOIRE), processor.match(ctx));
  }

  @Test
  void match_vcLdJsonJwtTypWithTopLevelContext_returnsLoire() throws Exception {
    var ctx = buildLoireJwtContext("vc+ld+json+jwt");

    assertEquals(Optional.of(CredentialFormat.GAIAX_V2_LOIRE), processor.match(ctx));
  }

  @Test
  void match_vpJwtTypWithTopLevelContext_returnsLoire() throws Exception {
    var ctx = buildLoireJwtContext("vp+jwt");

    assertEquals(Optional.of(CredentialFormat.GAIAX_V2_LOIRE), processor.match(ctx));
  }

  @Test
  void match_vpLdJwtTypWithTopLevelContext_returnsLoire() throws Exception {
    var ctx = buildLoireJwtContext("vp+ld+jwt");

    assertEquals(Optional.of(CredentialFormat.GAIAX_V2_LOIRE), processor.match(ctx));
  }

  @Test
  void match_loireTypPlusVcWrapper_returnsUnknown() throws Exception {
    var ctx = buildJwtContext("vc+jwt", Map.of("@context", VC_20_CONTEXT, "vc", Map.of()));

    assertEquals(Optional.of(CredentialFormat.UNKNOWN), processor.match(ctx));
  }

  @Test
  void match_loireTypPlusVpWrapper_returnsUnknown() throws Exception {
    var ctx = buildJwtContext("vp+jwt", Map.of("@context", VC_20_CONTEXT, "vp", Map.of()));

    assertEquals(Optional.of(CredentialFormat.UNKNOWN), processor.match(ctx));
  }

  @Test
  void match_nullTypHeader_returnsEmpty() throws Exception {
    var ctx = buildJwtContext(null, Map.of("@context", VC_20_CONTEXT));

    assertEquals(Optional.empty(), processor.match(ctx));
  }

  @Test
  void match_unknownTypValue_returnsEmpty() throws Exception {
    var ctx = buildJwtContext("application/json", Map.of("@context", VC_20_CONTEXT));

    assertEquals(Optional.empty(), processor.match(ctx));
  }

  @Test
  void process_verifySigsTrue_callsVerifyAndEnforcesWithValidator() {
    var body = "eyJfake.jwt.token";
    var payload = new ContentAccessorDirect(body);
    var unwrapped = new ContentAccessorDirect("{}");
    var validator = new Validator("did:web:example.com", null, null);
    when(jwtSignatureVerifier.verify(body)).thenReturn(validator);
    when(loireJwtParser.unwrap(payload)).thenReturn(unwrapped);

    ProcessedEnvelope result = processor.process(body, payload, true);

    verify(jwtSignatureVerifier).verify(body);
    verify(loirePolicyEnforcer).enforceIfApplicable(body, validator);
    assertTrue(result.wasJwt());
    assertEquals(validator, result.jwtValidator());
  }

  @Test
  void process_verifySigsFalse_skipsVerifyAndPassesNullValidator() {
    var body = "eyJfake.jwt.token";
    var payload = new ContentAccessorDirect(body);
    var unwrapped = new ContentAccessorDirect("{}");
    when(loireJwtParser.unwrap(payload)).thenReturn(unwrapped);

    ProcessedEnvelope result = processor.process(body, payload, false);

    verify(jwtSignatureVerifier, never()).verify(any());
    verify(loirePolicyEnforcer).enforceIfApplicable(body, null);
    assertTrue(result.wasJwt());
    assertNull(result.jwtValidator());
  }

  private DetectionContext buildLoireJwtContext(String typValue) throws Exception {
    return buildJwtContext(typValue, Map.of("@context", VC_20_CONTEXT, "type", "VerifiableCredential"));
  }

  private DetectionContext buildJwtContext(String typValue, Map<String, Object> claims) throws Exception {
    JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("k1");
    if (typValue != null) {
      headerBuilder.type(new JOSEObjectType(typValue));
    }
    JWTClaimsSet.Builder claimsBuilder = new JWTClaimsSet.Builder();
    claims.forEach(claimsBuilder::claim);
    SignedJWT jwt = new SignedJWT(headerBuilder.build(), claimsBuilder.build());
    jwt.sign(signer);
    JsonNode parsedJson = OBJECT_MAPPER.readTree(jwt.getPayload().toString());
    return new DetectionContext(jwt.serialize(), jwt, parsedJson);
  }
}
