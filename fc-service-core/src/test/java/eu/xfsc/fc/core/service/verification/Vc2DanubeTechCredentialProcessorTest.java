package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.JWT_PREFIX;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;

import java.util.List;
import java.util.Optional;

import net.minidev.json.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class Vc2DanubeTechCredentialProcessorTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static JWSSigner signer;

  @Mock
  private JwtContentPreprocessor jwtPreprocessor;

  @Mock
  private JwtSignatureVerifier jwtSignatureVerifier;

  @InjectMocks
  private Vc2DanubeTechCredentialProcessor processor;

  @BeforeAll
  static void initKeys() throws Exception {
    OctetKeyPair jwk = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-key").generate();
    signer = new Ed25519Signer(jwk);
  }

  @Test
  void getFormat_returnsDanubeTechFormat() {
    assertEquals(CredentialFormat.VC2_DANUBETECH, processor.getFormat());
  }


  @Test
  void match_nullParsedJson_returnsEmpty() {
    var ctx = new DetectionContext("{}", null, null);

    assertEquals(Optional.empty(), processor.match(ctx));
  }


  @Test
  void match_jwtWithVcWrapper_returnsDanubeTech() throws Exception {
    var ctx = buildJwtContextWithWrapper("vc");

    assertEquals(Optional.of(CredentialFormat.VC2_DANUBETECH), processor.match(ctx));
  }

  @Test
  void match_jwtWithVpWrapper_returnsDanubeTech() throws Exception {
    var ctx = buildJwtContextWithWrapper("vp");

    assertEquals(Optional.of(CredentialFormat.VC2_DANUBETECH), processor.match(ctx));
  }

  @Test
  void match_jwtWithoutVcOrVpWrapper_returnsEmpty() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("k1").build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("arbitrary", "data")
        .build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    JsonNode parsedJson = OBJECT_MAPPER.readTree(jwt.getPayload().toString());
    var ctx = new DetectionContext(jwt.serialize(), jwt, parsedJson);

    assertEquals(Optional.empty(), processor.match(ctx));
  }


  @Test
  void match_nonJwtWithVc20ContextString_returnsDanubeTech() throws Exception {
    JsonNode parsedJson = OBJECT_MAPPER.readTree("""
        {"@context":"%s"}""".formatted(VC_20_CONTEXT));
    var ctx = new DetectionContext("{}", null, parsedJson);

    assertEquals(Optional.of(CredentialFormat.VC2_DANUBETECH), processor.match(ctx));
  }

  @Test
  void match_nonJwtWithVc20ContextInArray_returnsDanubeTech() throws Exception {
    JsonNode parsedJson = OBJECT_MAPPER.readTree("""
        {"@context":["%s","https://schema.org"]}""".formatted(VC_20_CONTEXT));
    var ctx = new DetectionContext("{}", null, parsedJson);

    assertEquals(Optional.of(CredentialFormat.VC2_DANUBETECH), processor.match(ctx));
  }

  @Test
  void match_nonJwtWithoutVc20Context_returnsEmpty() throws Exception {
    JsonNode parsedJson = OBJECT_MAPPER.readTree("{\"@context\":\"https://example.org/custom\"}");
    var ctx = new DetectionContext("{}", null, parsedJson);

    assertEquals(Optional.empty(), processor.match(ctx));
  }


  @Test
  void process_jwtBodyVerifySigsTrue_callsVerifyAndIsJwtTrue() {
    var body = JWT_PREFIX + "fake.jwt.token";
    var payload = new ContentAccessorDirect(body);
    var unwrapped = new ContentAccessorDirect("{}");
    var validator = new Validator("did:web:example.com", null, null);
    when(jwtSignatureVerifier.verify(body)).thenReturn(validator);
    when(jwtPreprocessor.unwrap(payload)).thenReturn(unwrapped);

    ProcessedEnvelope result = processor.process(body, payload, true);

    verify(jwtSignatureVerifier).verify(body);
    assertTrue(result.wasJwt());
    assertEquals(validator, result.jwtValidator());
  }

  @Test
  void process_jwtBodyVerifySigsFalse_skipsVerifyAndIsJwtTrue() {
    var body = JWT_PREFIX + "fake.jwt.token";
    var payload = new ContentAccessorDirect(body);
    var unwrapped = new ContentAccessorDirect("{}");
    when(jwtPreprocessor.unwrap(payload)).thenReturn(unwrapped);

    ProcessedEnvelope result = processor.process(body, payload, false);

    verify(jwtSignatureVerifier, never()).verify(any());
    assertTrue(result.wasJwt());
    assertNull(result.jwtValidator());
  }

  @Test
  void process_nonJwtBody_skipsVerifyAndIsJwtFalse() {
    var body = """
        {"@context":"%s"}""".formatted(VC_20_CONTEXT);
    var payload = new ContentAccessorDirect(body);
    var unwrapped = new ContentAccessorDirect(body);
    when(jwtPreprocessor.unwrap(payload)).thenReturn(unwrapped);

    ProcessedEnvelope result = processor.process(body, payload, true);

    verify(jwtSignatureVerifier, never()).verify(any());
    assertFalse(result.wasJwt());
    assertNull(result.jwtValidator());
  }


  private DetectionContext buildJwtContextWithWrapper(String wrapperClaim) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("k1").build();
    JSONObject wrapperObj = new JSONObject();
    wrapperObj.put("@context", List.of(VC_20_CONTEXT));
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim(wrapperClaim, wrapperObj)
        .build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    JsonNode parsedJson = OBJECT_MAPPER.readTree(jwt.getPayload().toString());
    return new DetectionContext(jwt.serialize(), jwt, parsedJson);
  }
}
