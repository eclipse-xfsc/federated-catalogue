package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.EVC_TYPE;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.EVP_TYPE;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import foundation.identity.jsonld.JsonLDObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EnvelopedCredentialResolverTest {

  private static JWSSigner signer;

  @Mock
  private CredentialFormatDetector formatDetector;

  @Mock
  private JwtSignatureVerifier jwtSignatureVerifier;

  @Mock
  private CredentialFormatProcessor formatProcessor;

  @Spy
  private List<CredentialFormatProcessor> formatProcessors = new ArrayList<>();

  @Spy
  private ObjectMapper objectMapper = new ObjectMapper();

  @InjectMocks
  private EnvelopedCredentialResolver resolver;

  @BeforeAll
  static void initKeys() throws Exception {
    OctetKeyPair jwk = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-key").generate();
    signer = new Ed25519Signer(jwk);
  }

  @BeforeEach
  void setUp() {
    when(formatProcessor.getFormat()).thenReturn(CredentialFormat.VC2_DANUBETECH);
    formatProcessors.add(formatProcessor);
    resolver.indexProcessors();
  }


  @Test
  void resolveType_typeKey_returnsTypeValue() {
    assertEquals("VC", EnvelopedCredentialResolver.resolveType(Map.of("type", "VC")));
  }

  @Test
  void resolveType_atTypeKey_returnsAtTypeValue() {
    assertEquals("VC", EnvelopedCredentialResolver.resolveType(Map.of("@type", "VC")));
  }

  @Test
  void resolveType_neitherKey_returnsNull() {
    assertNull(EnvelopedCredentialResolver.resolveType(Map.of("other", "value")));
  }

  @Test
  void resolveType_bothKeys_typeWins() {
    var map = Map.of("type", "preferred", "@type", "fallback");
    assertEquals("preferred", EnvelopedCredentialResolver.resolveType(map));
  }


  @Test
  void resolveId_idKey_returnsIdValue() {
    assertEquals("urn:x", EnvelopedCredentialResolver.resolveId(Map.of("id", "urn:x")));
  }

  @Test
  void resolveId_atIdKey_returnsAtIdValue() {
    assertEquals("urn:x", EnvelopedCredentialResolver.resolveId(Map.of("@id", "urn:x")));
  }

  @Test
  void resolveId_neitherKey_returnsNull() {
    assertNull(EnvelopedCredentialResolver.resolveId(Map.of("other", "value")));
  }


  @Test
  void isEnvelopedVerifiableCredential_stringTypeStringContext_returnsTrue() {
    assertTrue(EnvelopedCredentialResolver.isEnvelopedVerifiableCredential(EVC_TYPE, VC_20_CONTEXT));
  }

  @Test
  void isEnvelopedVerifiableCredential_wrongType_returnsFalse() {
    assertFalse(EnvelopedCredentialResolver.isEnvelopedVerifiableCredential("Other", VC_20_CONTEXT));
  }

  @Test
  void isEnvelopedVerifiableCredential_correctTypeWrongContext_returnsFalse() {
    assertFalse(EnvelopedCredentialResolver.isEnvelopedVerifiableCredential(EVC_TYPE, "https://other.org/v1"));
  }

  @Test
  void isEnvelopedVerifiableCredential_listTypeContainsEvc_returnsTrue() {
    assertTrue(EnvelopedCredentialResolver.isEnvelopedVerifiableCredential(
        List.of("AnotherType", EVC_TYPE), VC_20_CONTEXT));
  }

  @Test
  void isEnvelopedVerifiableCredential_listContextContainsVc20_returnsTrue() {
    assertTrue(EnvelopedCredentialResolver.isEnvelopedVerifiableCredential(
        EVC_TYPE, List.of("https://other.org/v1", VC_20_CONTEXT)));
  }

  @Test
  void isEnvelopedVerifiableCredential_nullType_returnsFalse() {
    assertFalse(EnvelopedCredentialResolver.isEnvelopedVerifiableCredential(null, VC_20_CONTEXT));
  }


  @Test
  void extractEnvelopedJwt_nonJsonBody_returnsNull() {
    assertNull(resolver.extractEnvelopedJwt("not-json-at-all"));
  }

  @Test
  void extractEnvelopedJwt_evcWrapperValidDataUri_returnsInnerJwt() throws Exception {
    String jwtStr = buildMinimalJwt();
    String body = buildEvcWrapper(jwtStr);

    String result = resolver.extractEnvelopedJwt(body);

    assertEquals(jwtStr, result);
  }

  @Test
  void extractEnvelopedJwt_evpWrapperValidDataUri_returnsInnerJwt() throws Exception {
    String jwtStr = buildMinimalJwt();
    String body = buildEvpWrapper(jwtStr);

    String result = resolver.extractEnvelopedJwt(body);

    assertEquals(jwtStr, result);
  }

  @Test
  void extractEnvelopedJwt_evcWrapperWrongContext_returnsNull() {
    String body = """
        {"type":"%s","@context":"https://other.org/v1","id":"data:application/vc+jwt,eyJ"}""".formatted(EVC_TYPE);

    assertNull(resolver.extractEnvelopedJwt(body));
  }

  @Test
  void extractEnvelopedJwt_evcWrapperMissingCommaInDataUri_throwsClientException() {
    String body = """
        {"type":"%s","@context":"%s","id":"data:notype"}""".formatted(EVC_TYPE, VC_20_CONTEXT);

    assertThrows(ClientException.class, () -> resolver.extractEnvelopedJwt(body));
  }

  @Test
  void extractEnvelopedJwt_evcWrapperEmptyPayload_throwsClientException() {
    String body = """
        {"type":"%s","@context":"%s","id":"data:application/vc+jwt,"}""".formatted(EVC_TYPE, VC_20_CONTEXT);

    assertThrows(ClientException.class, () -> resolver.extractEnvelopedJwt(body));
  }

  @Test
  void extractEnvelopedJwt_plainVcJsonLd_returnsNull() {
    String body = """
        {"@context":["%s"],"type":["VerifiableCredential"]}""".formatted(VC_20_CONTEXT);

    assertNull(resolver.extractEnvelopedJwt(body));
  }


  @Test
  void resolveInnerEnvelopedCredentials_nonJsonPayload_returnsOriginal() {
    var payload = new ContentAccessorDirect("@prefix ex: <https://example.org/> .");

    assertSame(payload, resolver.resolveInnerEnvelopedCredentials(payload));
  }

  @Test
  void resolveInnerEnvelopedCredentials_nonVpPayload_returnsOriginal() {
    var payload = new ContentAccessorDirect("""
        {"@context":["%s"],"type":["VerifiableCredential"]}""".formatted(VC_20_CONTEXT));

    assertSame(payload, resolver.resolveInnerEnvelopedCredentials(payload));
  }

  @Test
  void resolveInnerEnvelopedCredentials_vpWithNoVcList_returnsOriginal() {
    var payload = new ContentAccessorDirect("""
        {"@context":["%s"],"type":["VerifiablePresentation"]}""".formatted(VC_20_CONTEXT));

    assertSame(payload, resolver.resolveInnerEnvelopedCredentials(payload));
  }

  @Test
  void resolveInnerEnvelopedCredentials_vpWithNonEvcEntries_returnsOriginal() {
    String vcEntry = """
        {"@context":["%s"],"type":["VerifiableCredential"]}""".formatted(VC_20_CONTEXT);
    var payload = new ContentAccessorDirect("""
        {"@context":["%s"],"type":["VerifiablePresentation"],"verifiableCredential":[%s]}""".formatted(VC_20_CONTEXT,
        vcEntry));

    assertSame(payload, resolver.resolveInnerEnvelopedCredentials(payload));
  }


  @Test
  void verifyInnerVcCredentials_noVcKey_returnsEmptyList() throws Exception {
    JsonLDObject ld = JsonLDObject.fromJson("""
        {"@context":"%s"}""".formatted(VC_20_CONTEXT));

    List<Validator> result = resolver.verifyInnerVcCredentials(ld);

    assertTrue(result.isEmpty());
  }

  @Test
  void verifyInnerVcCredentials_jwtStringEntry_callsVerify() throws Exception {
    String jwtStr = buildMinimalJwt();
    var validator = new Validator("did:web:example.com", null, null);
    when(jwtSignatureVerifier.verify(jwtStr)).thenReturn(validator);
    JsonLDObject ld = JsonLDObject.fromJson("""
        {"verifiableCredential":["%s"]}""".formatted(jwtStr));

    List<Validator> result = resolver.verifyInnerVcCredentials(ld);

    assertEquals(1, result.size());
    assertEquals(validator, result.getFirst());
  }

  @Test
  void verifyInnerVcCredentials_evcMapEntry_callsVerifyFromDataUrl() {
    String dataUri = "data:application/vc+jwt,eyJfake";
    var validator = new Validator("did:web:example.com", null, null);
    when(jwtSignatureVerifier.verifyFromDataUrl(dataUri)).thenReturn(validator);
    String evcEntry = """
        {"type":"%s","@context":"%s","id":"%s"}""".formatted(EVC_TYPE, VC_20_CONTEXT, dataUri);
    JsonLDObject ld = JsonLDObject.fromJson("""
        {"verifiableCredential":[%s]}""".formatted(evcEntry));

    List<Validator> result = resolver.verifyInnerVcCredentials(ld);

    assertEquals(1, result.size());
    verify(jwtSignatureVerifier).verifyFromDataUrl(dataUri);
  }

  @Test
  void verifyInnerVcCredentials_jsonLdProofEntry_throwsVerificationException() {
    String jsonLdEntry = """
        {"@context":"%s","type":"VerifiableCredential","proof":{}}""".formatted(VC_20_CONTEXT);
    JsonLDObject ld = JsonLDObject.fromJson("""
        {"verifiableCredential":[%s]}""".formatted(jsonLdEntry));

    assertThrows(VerificationException.class, () -> resolver.verifyInnerVcCredentials(ld));
  }


  private static String buildMinimalJwt() throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA).keyID("k1").build();
    JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("did:web:example.com").build();
    SignedJWT jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }

  private static String buildEvcWrapper(String jwtStr) {
    return """
        {"@context":"%s","type":"%s","id":"data:application/vc+jwt,%s"}""".formatted(VC_20_CONTEXT, EVC_TYPE, jwtStr);
  }

  private static String buildEvpWrapper(String jwtStr) {
    return """
        {"@context":"%s","type":"%s","id":"data:application/vp+jwt,%s"}""".formatted(VC_20_CONTEXT, EVP_TYPE, jwtStr);
  }
}
