package eu.xfsc.fc.core.service.verification.signature;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.Ed25519Signer;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.OctetKeyPair;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.OctetKeyPairGenerator;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import foundation.identity.did.DIDDocument;
import foundation.identity.did.VerificationMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtSignatureVerifierTest {

  private static final String ISS = "did:web:example.com";
  private static final String KID = "did:web:example.com#key-1";
  private static final String KID_B = "did:web:example.com#key-2";

  @Mock
  private DidDocumentResolver didResolver;

  @InjectMocks
  private JwtSignatureVerifier verifier;

  private OctetKeyPair keyPair;
  private OctetKeyPair attackerKey;

  @BeforeEach
  void setup() throws Exception {
    keyPair = new OctetKeyPairGenerator(Curve.Ed25519).keyID(KID).generate();
    attackerKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID(KID).generate();
  }

  // --- T4: JwtSignatureVerifier unit tests ---

  @Test
  void verify_validEdDsaVcJwt_returnsValidatorWithKid() throws Exception {
    mockDidDoc(KID, keyPair.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    Validator result = verifier.verify(compact);

    assertNotNull(result);
    assertEquals(KID, result.getDidURI());
    assertNotNull(result.getPublicKey());
  }

  @Test
  void verify_validEdDsaVpJwt_returnsValidator() throws Exception {
    mockDidDoc(KID, keyPair.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(ISS)
        .claim("holder", ISS)
        .claim("type", List.of("VerifiablePresentation"))
        .build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    Validator result = verifier.verify(compact);

    assertNotNull(result);
    assertEquals(KID, result.getDidURI());
  }

  @Test
  void verify_algNone_throwsClientException() {
    String header = base64url("{\"alg\":\"none\"}");
    String payload = base64url("{\"iss\":\"" + ISS + "\"}");
    String sig = base64url("fake");
    String fakeJwt = header + "." + payload + "." + sig;

    assertThrows(ClientException.class, () -> verifier.verify(fakeJwt));
  }

  @Test
  void verify_missingAlg_throwsClientException() {
    String header = base64url("{\"typ\":\"JWT\"}");
    String payload = base64url("{\"iss\":\"" + ISS + "\"}");
    String sig = base64url("fake");
    String fakeJwt = header + "." + payload + "." + sig;

    assertThrows(ClientException.class, () -> verifier.verify(fakeJwt));
  }

  @Test
  void verify_missingIss_throwsClientException() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().subject("no-iss").build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    assertThrows(ClientException.class, () -> verifier.verify(compact));
  }

  @Test
  void verify_expiredJwt_throwsVerificationException() throws Exception {
    // no DID lookup needed — exp check fires before DID resolution
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer(ISS)
        .expirationTime(Date.from(Instant.now().minusSeconds(3600)))
        .build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    assertThrows(VerificationException.class, () -> verifier.verify(compact));
  }

  @Test
  void verify_kidNotInAssertionMethod_throwsVerificationException() throws Exception {
    // assertionMethod has key-2, but JWT requests key-1
    // getPublicKeyJwk() never called — throw happens before JWK lookup, so only stub getId()
    VerificationMethod vmB = mock(VerificationMethod.class);
    when(vmB.getId()).thenReturn(URI.create(KID_B));
    DIDDocument mockDoc = mock(DIDDocument.class);
    when(mockDoc.getAssertionMethodVerificationMethodsDereferenced()).thenReturn(List.of(vmB));
    when(didResolver.resolveDidDocument(ISS)).thenReturn(mockDoc);

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    VerificationException ex = assertThrows(VerificationException.class,
        () -> verifier.verify(compact));

    // AC 10: error message contains attempted kid and available method IDs
    assertTrue(ex.getMessage().contains(KID), "message should contain attempted kid: " + ex.getMessage());
    assertTrue(ex.getMessage().contains(KID_B), "message should list available ids: " + ex.getMessage());
  }

  @Test
  void verify_badSignature_throwsVerificationException() throws Exception {
    OctetKeyPair differentKey = new OctetKeyPairGenerator(Curve.Ed25519).keyID(KID).generate();

    // DID doc has differentKey, but JWT is signed with keyPair
    mockDidDoc(KID, differentKey.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    assertThrows(VerificationException.class, () -> verifier.verify(compact));
  }

  @Test
  void verify_embeddedJwkHeader_keyFromDidDocNotHeader() throws Exception {
    // DID doc has legitimateKey; JWT is signed with attackerKey and embeds attackerKey in jwk header
    mockDidDoc(KID, keyPair.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID(KID)
        .jwk(attackerKey.toPublicJWK()) // attacker embeds own key — must be ignored
        .build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(attackerKey));

    // Code ignores jwk header → uses DID doc key (legitimate) → signature mismatch → VerificationException
    assertThrows(VerificationException.class, () -> verifier.verify(compact));
  }

  @Test
  void verify_missingKid_iteratesAllAssertionMethods_returnsValidator() throws Exception {
    mockDidDoc(KID, keyPair.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).build(); // no kid
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    Validator result = verifier.verify(compact);

    assertNotNull(result);
    assertNotNull(result.getDidURI());
  }

  @Test
  void verify_unsupportedKeyFormatPublicKeyMultibase_throwsVerificationException() throws Exception {
    // DID doc method has publicKeyMultibase only — no publicKeyJwk
    VerificationMethod vm = mock(VerificationMethod.class);
    when(vm.getId()).thenReturn(URI.create(KID));
    when(vm.getPublicKeyJwk()).thenReturn(null); // no publicKeyJwk
    DIDDocument mockDoc = mock(DIDDocument.class);
    when(mockDoc.getAssertionMethodVerificationMethodsDereferenced()).thenReturn(List.of(vm));
    when(didResolver.resolveDidDocument(ISS)).thenReturn(mockDoc);

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    VerificationException ex = assertThrows(VerificationException.class,
        () -> verifier.verify(compact));
    assertTrue(ex.getMessage().contains("publicKeyJwk"), ex.getMessage());
  }

  @Test
  void verify_algKeyTypeMismatch_throwsVerificationException() throws Exception {
    // JWT has EdDSA alg but DID doc has RSA key → JOSEException wrapped as VerificationException
    RSAKey rsaKey = new RSAKeyGenerator(2048).keyID(KID).generate();
    mockDidDoc(KID, rsaKey.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    VerificationException ex = assertThrows(VerificationException.class,
        () -> verifier.verify(compact));
    assertTrue(ex.getMessage().contains("alg incompatible") || ex.getMessage().contains("key type"),
        ex.getMessage());
  }

  @Test
  void verify_relativeKidFragment_normalizedAndMatches() throws Exception {
    // kid: #key-1 normalized with iss → did:web:example.com#key-1
    mockDidDoc(KID, keyPair.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("#key-1").build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));

    Validator result = verifier.verify(compact);

    assertNotNull(result);
    assertEquals(KID, result.getDidURI()); // normalized kid
  }

  // --- T7: EnvelopedVerifiableCredential / verifyFromDataUrl tests ---

  @Test
  void verifyFromDataUrl_validEnvelopedVcJwt_returnsValidator() throws Exception {
    mockDidDoc(KID, keyPair.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));
    String dataUrl = "data:application/vc+ld+json+jwt," + compact;

    Validator result = verifier.verifyFromDataUrl(dataUrl);

    assertNotNull(result);
    assertEquals(KID, result.getDidURI());
  }

  @Test
  void verifyFromDataUrl_multipleCallsSucceed() throws Exception {
    mockDidDoc(KID, keyPair.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));
    String dataUrl = "data:application/vc+ld+json+jwt," + compact;

    Validator r1 = verifier.verifyFromDataUrl(dataUrl);
    Validator r2 = verifier.verifyFromDataUrl(dataUrl);

    assertNotNull(r1);
    assertNotNull(r2);
    assertEquals(KID, r1.getDidURI());
    assertEquals(KID, r2.getDidURI());
  }

  @Test
  void verifyFromDataUrl_w3cDataUrlPrefix_returnsValidator() throws Exception {
    mockDidDoc(KID, keyPair.toPublicJWK().toJSONObject());

    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID(KID).build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(ISS).build();
    String compact = signJwt(header, claims, new Ed25519Signer(keyPair));
    String dataUrl = "data:application/vc+jwt," + compact;

    Validator result = verifier.verifyFromDataUrl(dataUrl);

    assertNotNull(result);
    assertEquals(KID, result.getDidURI());
  }

  @Test
  void verifyFromDataUrl_invalidDataUrl_throwsClientException() {
    assertThrows(ClientException.class,
        () -> verifier.verifyFromDataUrl("https://not-a-data-url.example.com/vc.jwt"));
    assertThrows(ClientException.class,
        () -> verifier.verifyFromDataUrl("data:application/json,{}"));
  }

  @Test
  void verifyFromDataUrl_malformedInnerJwt_throwsClientException() {
    String dataUrl = "data:application/vc+ld+json+jwt,not-a-jwt";

    assertThrows(ClientException.class, () -> verifier.verifyFromDataUrl(dataUrl));
  }


  private void mockDidDoc(String kid, Map<String, Object> jwkMap) {
    VerificationMethod vm = mock(VerificationMethod.class);
    when(vm.getId()).thenReturn(URI.create(kid));
    when(vm.getPublicKeyJwk()).thenReturn(jwkMap);

    DIDDocument mockDoc = mock(DIDDocument.class);
    when(mockDoc.getAssertionMethodVerificationMethodsDereferenced()).thenReturn(List.of(vm));
    when(didResolver.resolveDidDocument(ISS)).thenReturn(mockDoc);
  }

  private String signJwt(JWSHeader header, JWTClaimsSet claims, JWSSigner signer)
      throws Exception {
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }

  private static String base64url(String json) {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes());
  }

  @SuppressWarnings("unused")
  private static Map<String, Object> jwkAsMap(com.nimbusds.jose.jwk.JWK jwk) {
    return new LinkedHashMap<>(jwk.toJSONObject());
  }
}
