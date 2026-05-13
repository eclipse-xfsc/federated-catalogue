package eu.xfsc.fc.core.service.verification;

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

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import java.util.List;
import java.util.Map;

import net.minidev.json.JSONObject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static eu.xfsc.fc.core.service.verification.TestVerificationConstants.GAIAX_2511_CONTEXT;
import static eu.xfsc.fc.core.service.verification.VerificationConstants.VC_20_CONTEXT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoireJwtParserTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final LoireJwtParser parser = new LoireJwtParser();
  private static OctetKeyPair jwk;
  private static JWSSigner signer;

  @BeforeAll
  static void initKeys() throws Exception {
    jwk = new OctetKeyPairGenerator(Curve.Ed25519).keyID("test-key").generate();
    signer = new Ed25519Signer(jwk);
  }

  // --- valid Loire VC ---

  @Test
  void unwrap_validLoireVc_returnsJsonLdWithCredentialFields() throws Exception {
    String jwt = buildLoireVcJwt();

    ContentAccessor result = parser.unwrap(new ContentAccessorDirect(jwt));

    String json = result.getContentAsString();
    JsonNode root = MAPPER.readTree(json);
    assertNotNull(root.get("@context"), "unwrapped payload must have @context");
    assertNotNull(root.get("credentialSubject"), "unwrapped payload must have credentialSubject");
    assertEquals("2026-01-01T00:00:00Z", root.get("validFrom").asText());
  }

  @Test
  void unwrap_validLoireVp_returnsJsonLdWithPresentationFields() throws Exception {
    String jwt = buildLoireVpJwt();

    ContentAccessor result = parser.unwrap(new ContentAccessorDirect(jwt));

    String json = result.getContentAsString();
    JsonNode root = MAPPER.readTree(json);
    assertNotNull(root.get("@context"));
    assertNotNull(root.get("verifiableCredential"));
  }

  // --- typ/cty header validation ---

  @Test
  void unwrap_missingTypHeader_throwsClientException() throws Exception {
    String jwt = buildJwtWithHeaders(null, null);

    ClientException ex = assertThrows(ClientException.class,
        () -> parser.unwrap(new ContentAccessorDirect(jwt)));

    assertTrue(ex.getMessage().contains("missing required 'typ' header"));
  }

  @Test
  void unwrap_invalidTypHeader_throwsClientException() throws Exception {
    String jwt = buildJwtWithHeaders("jwt", null);

    ClientException ex = assertThrows(ClientException.class,
        () -> parser.unwrap(new ContentAccessorDirect(jwt)));

    assertTrue(ex.getMessage().contains("invalid 'typ' header"));
  }

  @Test
  void unwrap_vcTypWithWrongCty_throwsClientException() throws Exception {
    String jwt = buildJwtWithHeaders("vc+ld+json+jwt", "vp+ld+json");

    ClientException ex = assertThrows(ClientException.class,
        () -> parser.unwrap(new ContentAccessorDirect(jwt)));

    assertTrue(ex.getMessage().contains("invalid 'cty' header"));
  }

  @Test
  void unwrap_vpTypWithWrongCty_throwsClientException() throws Exception {
    String jwt = buildVpJwtWithHeaders("vp+ld+jwt", "vc+ld+json");

    ClientException ex = assertThrows(ClientException.class,
        () -> parser.unwrap(new ContentAccessorDirect(jwt)));

    assertTrue(ex.getMessage().contains("invalid 'cty' header"));
  }

  @Test
  void unwrap_missingCtyHeader_throwsClientException() throws Exception {
    String jwt = buildJwtWithHeaders("vc+ld+json+jwt", null);

    ClientException ex = assertThrows(ClientException.class,
        () -> parser.unwrap(new ContentAccessorDirect(jwt)));

    assertTrue(ex.getMessage().contains("missing required 'cty' header"));
  }

  // --- wrapper claim rejection ---

  @Test
  void unwrap_vcWrapperClaimPresent_throwsClientException() throws Exception {
    String jwt = buildJwtWithWrapperClaim("vc");

    ClientException ex = assertThrows(ClientException.class,
        () -> parser.unwrap(new ContentAccessorDirect(jwt)));

    assertTrue(ex.getMessage().contains("must not contain 'vc' wrapper claim"));
  }

  @Test
  void unwrap_vpWrapperClaimPresent_throwsClientException() throws Exception {
    String jwt = buildJwtWithWrapperClaim("vp");

    ClientException ex = assertThrows(ClientException.class,
        () -> parser.unwrap(new ContentAccessorDirect(jwt)));

    assertTrue(ex.getMessage().contains("must not contain 'vp' wrapper claim"));
  }

  // --- W3C VC-JOSE-COSE header variants ---

  @Test
  void unwrap_validW3cLoireVc_returnsJsonLd() throws Exception {
    String jwt = buildJwtWithHeaders("vc+jwt", "vc");

    ContentAccessor result = parser.unwrap(new ContentAccessorDirect(jwt));

    String json = result.getContentAsString();
    JsonNode root = MAPPER.readTree(json);
    assertNotNull(root.get("@context"), "unwrapped payload must have @context");
    assertNotNull(root.get("credentialSubject"), "unwrapped payload must have credentialSubject");
  }

  @Test
  void unwrap_validW3cLoireVp_returnsJsonLd() throws Exception {
    String jwt = buildVpJwtWithHeaders("vp+jwt", "vp");

    ContentAccessor result = parser.unwrap(new ContentAccessorDirect(jwt));

    String json = result.getContentAsString();
    JsonNode root = MAPPER.readTree(json);
    assertNotNull(root.get("@context"));
    assertNotNull(root.get("verifiableCredential"));
  }

  @Test
  void unwrap_w3cTypWithIcamCty_succeeds() throws Exception {
    // Cross-family: W3C typ + ICAM cty — no pairing enforcement
    String jwt = buildJwtWithHeaders("vc+jwt", "vc+ld+json");

    ContentAccessor result = parser.unwrap(new ContentAccessorDirect(jwt));

    assertNotNull(result.getContentAsString());
  }

  // --- isVpJwt ---

  @Test
  void isVpJwt_vpJwt_returnsTrue() throws Exception {
    String jwt = buildLoireVpJwt();

    assertTrue(parser.isVpJwt(new ContentAccessorDirect(jwt)));
  }

  @Test
  void isVpJwt_vcJwt_returnsFalse() throws Exception {
    String jwt = buildLoireVcJwt();

    assertFalse(parser.isVpJwt(new ContentAccessorDirect(jwt)));
  }

  @Test
  void isVpJwt_w3cVpJwt_returnsTrue() throws Exception {
    String jwt = buildVpJwtWithHeaders("vp+jwt", "vp");

    assertTrue(parser.isVpJwt(new ContentAccessorDirect(jwt)));
  }


  private String buildLoireVcJwt() throws Exception {
    return buildJwtWithHeaders("vc+ld+json+jwt", "vc+ld+json");
  }

  private String buildLoireVpJwt() throws Exception {
    return buildVpJwtWithHeaders("vp+ld+jwt", "vp+ld+json");
  }

  private String buildJwtWithHeaders(String typ, String cty) throws Exception {
    JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key");
    if (typ != null) {
      headerBuilder.type(new JOSEObjectType(typ));
    }
    if (cty != null) {
      headerBuilder.contentType(cty);
    }
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("@context", List.of(VC_20_CONTEXT, GAIAX_2511_CONTEXT))
        .claim("type", List.of("VerifiableCredential", "gx:LegalPerson"))
        .claim("credentialSubject", Map.of("id", "did:web:example.com"))
        .claim("validFrom", "2026-01-01T00:00:00Z")
        .build();
    SignedJWT signedJwt = new SignedJWT(headerBuilder.build(), claims);
    signedJwt.sign(signer);
    return signedJwt.serialize();
  }

  private String buildVpJwtWithHeaders(String typ, String cty) throws Exception {
    JWSHeader.Builder headerBuilder = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key");
    if (typ != null) {
      headerBuilder.type(new JOSEObjectType(typ));
    }
    if (cty != null) {
      headerBuilder.contentType(cty);
    }
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("@context", List.of(VC_20_CONTEXT))
        .claim("type", List.of("VerifiablePresentation"))
        .claim("holder", "did:web:example.com")
        .claim("verifiableCredential", List.of())
        .build();
    SignedJWT signedJwt = new SignedJWT(headerBuilder.build(), claims);
    signedJwt.sign(signer);
    return signedJwt.serialize();
  }

  private String buildJwtWithWrapperClaim(String wrapperKey) throws Exception {
    JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.EdDSA)
        .keyID("did:web:example.com#test-key")
        .type(new JOSEObjectType("vc+ld+json+jwt"))
        .contentType("vc+ld+json")
        .build();
    JSONObject wrapper = new JSONObject();
    wrapper.put("@context", List.of(VC_20_CONTEXT));
    JWTClaimsSet claims = new JWTClaimsSet.Builder()
        .issuer("did:web:example.com")
        .claim("@context", List.of(VC_20_CONTEXT))
        .claim("type", List.of("VerifiableCredential"))
        .claim(wrapperKey, wrapper)
        .build();
    SignedJWT signedJwt = new SignedJWT(header, claims);
    signedJwt.sign(signer);
    return signedJwt.serialize();
  }
}
