package eu.xfsc.fc.core.service.verification;

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

import java.util.List;
import java.util.Map;

import net.minidev.json.JSONObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatDetectorTest {

  private static final String VC2_CONTEXT = "https://www.w3.org/ns/credentials/v2";
  private static final String VC11_CONTEXT = "https://www.w3.org/2018/credentials/v1";
  private static final String GAIAX_2511_CONTEXT = "https://w3id.org/gaia-x/2511#";

  private final FormatDetector detector = new FormatDetector();
    private static JWSSigner signer;

  @BeforeAll
  static void initKeys() throws Exception {
    OctetKeyPair jwk = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-key").generate();
    signer = new Ed25519Signer(jwk);
  }

  // --- Path 1: Gaia-X v1 (Tagus) ---

  @Test
  void detect_vc11WithProofBlock_returnsTagus() {
    String body = """
        {
          "@context": ["https://www.w3.org/2018/credentials/v1"],
          "type": ["VerifiableCredential"],
          "proof": { "type": "JsonWebSignature2020" }
        }
        """;

    CredentialFormat result = detector.detect(new ContentAccessorDirect(body));

    assertEquals(CredentialFormat.GAIAX_V1_TAGUS, result);
  }

  @Test
  void detect_vc11WithProofBlockAndGaiaxContext_returnsTagus() {
    String body = """
        {
          "@context": [
            "https://www.w3.org/2018/credentials/v1",
            "https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#"
          ],
          "type": ["VerifiableCredential", "LegalParticipant"],
          "proof": { "type": "JsonWebSignature2020" }
        }
        """;

    CredentialFormat result = detector.detect(new ContentAccessorDirect(body));

    assertEquals(CredentialFormat.GAIAX_V1_TAGUS, result);
  }

  @Test
  void detect_vc11WithoutProofBlock_returnsTagus() {
    String body = """
        {
          "@context": ["https://www.w3.org/2018/credentials/v1"],
          "type": ["VerifiablePresentation"],
          "verifiableCredential": {}
        }
        """;

    CredentialFormat result = detector.detect(new ContentAccessorDirect(body));

    assertEquals(CredentialFormat.GAIAX_V1_TAGUS, result);
  }

  // --- Path 2: Gaia-X v2 (Loire) ---

  @Test
  void detect_loireVcJwt_returnsLoire() throws Exception {
    String jwt = buildLoireVcJwt();

    CredentialFormat result = detector.detect(new ContentAccessorDirect(jwt));

    assertEquals(CredentialFormat.GAIAX_V2_LOIRE, result);
  }

  @Test
  void detect_loireVpJwt_returnsLoire() throws Exception {
    String jwt = buildLoireVpJwt();

    CredentialFormat result = detector.detect(new ContentAccessorDirect(jwt));

    assertEquals(CredentialFormat.GAIAX_V2_LOIRE, result);
  }

  @Test
  void detect_jwtWithTopLevelContextButNoTypHeader_returnsUnknown() throws Exception {
    // Missing typ header → UNKNOWN (no lenient fallback)
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key")
        .build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("@context", List.of(VC2_CONTEXT, GAIAX_2511_CONTEXT))
        .claim("type", List.of("VerifiableCredential", "gx:LegalPerson"))
        .claim("credentialSubject", Map.of("id", "did:web:example.com"))
        .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(signer);

    CredentialFormat result = detector.detect(new ContentAccessorDirect(signedJwt.serialize()));

    assertEquals(CredentialFormat.UNKNOWN, result);
  }

  // --- Path 3: Non-Gaia-X VC 2.0 (danubetech) ---

  @Test
  void detect_danubetechVcJwtWithVcWrapper_returnsDanubetech() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key")
        .build();
    JSONObject vcObj = new JSONObject();
    vcObj.put("@context", List.of(VC2_CONTEXT));
    vcObj.put("type", List.of("VerifiableCredential"));
    vcObj.put("credentialSubject", Map.of("id", "did:web:example.com"));
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .subject("did:web:example.com")
        .claim("vc", vcObj)
        .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(signer);

    CredentialFormat result = detector.detect(new ContentAccessorDirect(signedJwt.serialize()));

    assertEquals(CredentialFormat.VC2_DANUBETECH, result);
  }

  @Test
  void detect_danubetechVpJwtWithVpWrapper_returnsDanubetech() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key")
        .build();
    JSONObject vpObj = new JSONObject();
    vpObj.put("@context", List.of(VC2_CONTEXT));
    vpObj.put("type", List.of("VerifiablePresentation"));
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("vp", vpObj)
        .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(signer);

    CredentialFormat result = detector.detect(new ContentAccessorDirect(signedJwt.serialize()));

    assertEquals(CredentialFormat.VC2_DANUBETECH, result);
  }

  // --- UNKNOWN / rejection cases ---

  @Test
  void detect_loireTypButVcWrapperPresent_returnsUnknown() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key")
        .type(new JOSEObjectType("vc+ld+json+jwt"))
        .build();
    JSONObject vcObj = new JSONObject();
    vcObj.put("@context", List.of(VC2_CONTEXT));
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("@context", List.of(VC2_CONTEXT))
        .claim("vc", vcObj)
        .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(signer);

    CredentialFormat result = detector.detect(new ContentAccessorDirect(signedJwt.serialize()));

    assertEquals(CredentialFormat.UNKNOWN, result);
  }

  @Test
  void detect_vc2NonJwtWithoutProof_returnsDanubetech() {
    String body = """
        {
          "@context": ["https://www.w3.org/ns/credentials/v2"],
          "type": ["VerifiableCredential"]
        }
        """;

    CredentialFormat result = detector.detect(new ContentAccessorDirect(body));

    assertEquals(CredentialFormat.VC2_DANUBETECH, result);
  }

  @Test
  void detect_vc2WithProofButNoVc11Context_returnsDanubetech() {
    String body = """
        {
          "@context": ["https://www.w3.org/ns/credentials/v2"],
          "proof": { "type": "DataIntegrityProof" }
        }
        """;

    CredentialFormat result = detector.detect(new ContentAccessorDirect(body));

    assertEquals(CredentialFormat.VC2_DANUBETECH, result);
  }

  @Test
  void detect_nonJwtWithoutRecognizedContext_returnsUnknown() {
    String body = """
        {
          "@context": ["https://example.org/custom"],
          "type": ["SomethingElse"]
        }
        """;

    CredentialFormat result = detector.detect(new ContentAccessorDirect(body));

    assertEquals(CredentialFormat.UNKNOWN, result);
  }

  @Test
  void detect_malformedJson_returnsUnknown() {
    String body = "not json at all";

    CredentialFormat result = detector.detect(new ContentAccessorDirect(body));

    assertEquals(CredentialFormat.UNKNOWN, result);
  }

  @Test
  void detect_emptyBody_returnsUnknown() {
    CredentialFormat result = detector.detect(new ContentAccessorDirect(""));

    assertEquals(CredentialFormat.UNKNOWN, result);
  }

  @Test
  void detect_jwtWithNoRecognizablePayload_returnsUnknown() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key")
        .build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("arbitrary", "data")
        .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(signer);

    CredentialFormat result = detector.detect(new ContentAccessorDirect(signedJwt.serialize()));

    assertEquals(CredentialFormat.UNKNOWN, result);
  }

  // --- helpers ---

  private String buildLoireVcJwt() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key")
        .type(new JOSEObjectType("vc+ld+json+jwt"))
        .contentType("vc+ld+json")
        .build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("@context", List.of(VC2_CONTEXT, GAIAX_2511_CONTEXT))
        .claim("type", List.of("VerifiableCredential", "gx:LegalPerson"))
        .claim("credentialSubject", Map.of("id", "did:web:example.com"))
        .claim("validFrom", "2026-01-01T00:00:00Z")
        .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(signer);
    return signedJwt.serialize();
  }

  private String buildLoireVpJwt() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key")
        .type(new JOSEObjectType("vp+ld+jwt"))
        .contentType("vp+ld+json")
        .build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("@context", List.of(VC2_CONTEXT))
        .claim("type", List.of("VerifiablePresentation"))
        .claim("holder", "did:web:example.com")
        .claim("verifiableCredential", List.of())
        .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(signer);
    return signedJwt.serialize();
  }
}
