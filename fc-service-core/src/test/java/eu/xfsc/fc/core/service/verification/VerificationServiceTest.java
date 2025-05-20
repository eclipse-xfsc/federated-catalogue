package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.dao.impl.SchemaDaoImpl;
import eu.xfsc.fc.core.dao.impl.ValidatorCacheDaoImpl;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SemanticValidationResult;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import eu.xfsc.fc.core.pojo.VerificationResultParticipant;
import eu.xfsc.fc.core.pojo.VerificationResultResource;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {VerificationServiceTest.TestApplication.class, FileStoreConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class,
        VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaDaoImpl.class, DatabaseConfig.class, DidResolverConfig.class, ValidatorCacheDaoImpl.class, HttpDocumentResolver.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public class VerificationServiceTest {

  @SpringBootApplication
  public static class TestApplication {

    public static void main(final String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }

  @Autowired
  private VerificationServiceImpl verificationService;

  @Autowired
  private SchemaStoreImpl schemaStore;

  @AfterEach
  public void storageSelfCleaning() throws IOException {
    schemaStore.clear();
  }

  @Test
  void invalidSyntax_MissingQuote() {
    log.debug("invalidSyntax_MissingQuote");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/syntax/missingQuote.jsonld";
    ContentAccessor content = getAccessor(path);
    Exception ex = assertThrowsExactly(ClientException.class, ()
            -> verificationService.verifySelfDescription(content));
    assertTrue(ex.getMessage().startsWith("Syntactic error: "));
    assertNotNull(ex.getCause());
  }

  @Test
  void invalidSyntax_NoVCinSD() {
    log.debug("invalidSyntax_NoVCinSD");
    String path = "VerificationService/syntax/smallExample.jsonld";
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifySelfDescription(getAccessor(path)));
    assertTrue(ex.getMessage().contains("unexpected SD type: null"));
  }

  @Test
  void validVCnoVP() {
    log.debug("validVCnoVP");
    String path = "VerificationService/syntax/input.vc.jsonld";
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    VerificationResult vr = verificationService.verifySelfDescription(getAccessor(path), true, true, false, false);
    assertNotNull(vr);
    assertTrue(vr instanceof VerificationResultParticipant);
    VerificationResultParticipant vrp = (VerificationResultParticipant) vr;
    assertEquals(vrp.getIssuer(), "did:v1:test:nym:z6MkhdmzFu659ZJ4XKj31vtEDmjvsi5yDZG5L7Caz63oP39k");
    assertEquals(vrp.getParticipantName(), vrp.getIssuer());
    assertEquals(vrp.getId(), vrp.getIssuer()); // not sure this is correct..
  }

  @Test
  void validVCUnknownType() {
    log.debug("validVCUnknownType");
    String path = "VerificationService/jsonld/input.vc.jsonld";
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    Exception ex = assertThrowsExactly(VerificationException.class, () -> verificationService.verifySelfDescription(getAccessor(path)));
    assertEquals("Semantic Error: no proper CredentialSubject found", ex.getMessage());

    VerificationResult vr = verificationService.verifySelfDescription(getAccessor(path), false, true, false, false);
    assertNotNull(vr);
    assertEquals("did:example:ebfeb1f712ebc6f1c276e12ec21", vr.getId());
    assertEquals("https://example.edu/issuers/565049", vr.getIssuer());
  }
  
  @Test
  void validVPUnknownType() {
    log.debug("validVPUnknownType");
    String path = "VerificationService/jsonld/input.vp.jsonld";
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    Exception ex = assertThrowsExactly(VerificationException.class, () -> verificationService.verifySelfDescription(getAccessor(path)));
    assertEquals("Semantic Error: no proper CredentialSubject found", ex.getMessage());

    VerificationResult vr = verificationService.verifySelfDescription(getAccessor(path), false, true, false, false);
    assertNotNull(vr);
    assertEquals("did:key:z6MkjRagNiMu91DduvCvgEsqLZDVzrJzFrwahc4tXLt9DoHd", vr.getId());
    assertEquals("did:v1:test:nym:z6MkhdmzFu659ZJ4XKj31vtEDmjvsi5yDZG5L7Caz63oP39k", vr.getIssuer());
  }

  @Test
  void validSyntax_Participant() {
    log.debug("validSyntax_Participant");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/syntax/participantSD2.jsonld";
    VerificationResult vr = verificationService.verifySelfDescription(getAccessor(path));
    assertNotNull(vr);
    assertTrue(vr instanceof VerificationResultParticipant);
    VerificationResultParticipant vrp = (VerificationResultParticipant) vr;
    assertEquals("https://www.handelsregister.de/", vrp.getId());
    assertEquals("https://www.handelsregister.de/", vrp.getIssuer());
    assertEquals(Instant.parse("2010-01-01T19:37:24Z"), vrp.getIssuedDateTime());
  }

  @Test
  void validSyntax_ValidSDVP() {
    log.debug("validSyntax_ValidSDVP");
    //schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/syntax/input.vp.jsonld";
    VerificationResult vr = verificationService.verifySelfDescription(getAccessor(path), true, true, false, false);
    assertNotNull(vr);
    assertTrue(vr instanceof VerificationResultParticipant);
    VerificationResultParticipant vrp = (VerificationResultParticipant) vr;
    //assertEquals("http://example.gov/credentials/3732", vrp.getId()); for Participants id = issuer!
    assertEquals("did:v1:test:nym:z6MkhdmzFu659ZJ4XKj31vtEDmjvsi5yDZG5L7Caz63oP39k", vrp.getId());
    assertEquals("did:v1:test:nym:z6MkhdmzFu659ZJ4XKj31vtEDmjvsi5yDZG5L7Caz63oP39k", vrp.getIssuer());
    assertEquals(Instant.parse("2020-03-10T04:24:12.164Z"), vrp.getIssuedDateTime());
  }

  @Test
  void validSyntax_ValidServiceOldSchema() {
    log.debug("validSyntax_ValidServiceOldSchema");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("VerificationService/syntax/serviceOffering1.jsonld");
    VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
    assertNotNull(vr);
    assertFalse(vr instanceof VerificationResultParticipant);
    assertTrue(vr instanceof VerificationResultOffering);
    VerificationResultOffering vro = (VerificationResultOffering) vr;
    assertEquals("https://www.example.org/Service1", vro.getId());
    assertEquals("http://gaiax.de", vro.getIssuer());
    assertNotNull(vro.getClaims());
    assertEquals(19, vro.getClaims().size()); //!!
    assertNull(vro.getValidators());
    assertNull(vro.getValidatorDids());
    assertEquals(Instant.parse("2022-10-19T18:48:09Z"), vro.getIssuedDateTime());
  }

  @Test
  void validSyntax_ValidServiceNewSchema() {
    log.debug("validSyntax_ValidServiceNewSchema");
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/serviceOffering2.jsonld");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.SERVICE_OFFERING, "https://w3id.org/gaia-x/core#ServiceOffering");
    VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.SERVICE_OFFERING, "http://w3id.org/gaia-x/service#ServiceOffering");
    assertNotNull(vr);
    assertTrue(vr instanceof VerificationResultOffering);
    assertFalse(vr instanceof VerificationResultParticipant);
    VerificationResultOffering vro = (VerificationResultOffering) vr;
    assertEquals("https://www.example.org/mySoftwareOffering", vro.getId());
    assertEquals("http://gaiax.de", vro.getIssuer());
    assertNotNull(vro.getClaims());
    assertEquals(21, vro.getClaims().size()); //!!
    assertNull(vro.getValidators());
    assertNull(vro.getValidatorDids());
    assertEquals(Instant.parse("2022-10-19T18:48:09Z"), vro.getIssuedDateTime());
  }

  @Test
  void validSyntax_ValidPersonOldSchema() {
    log.debug("validSyntax_ValidPerson");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("VerificationService/syntax/legalPerson1.jsonld");
    VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
    assertNotNull(vr);
    assertTrue(vr instanceof VerificationResultParticipant);
    assertFalse(vr instanceof VerificationResultOffering);
    VerificationResultParticipant vrp = (VerificationResultParticipant) vr;
    assertEquals("http://gaiax.de", vrp.getId());
    assertEquals("http://gaiax.de", vrp.getIssuer());
    assertEquals("http://gaiax.de", vrp.getParticipantName()); // could be 'Provider Name'..
    assertNotNull(vrp.getClaims());
    assertEquals(26, vrp.getClaims().size()); //!!
    assertNull(vrp.getValidators());
    assertNull(vrp.getValidatorDids());
    assertEquals(Instant.parse("2022-10-19T18:48:09Z"), vrp.getIssuedDateTime());
  }

  @Test
  void validSyntax_ValidPersonNewSchema() {
    log.debug("validSyntax_ValidPerson2");
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/legalPerson2.jsonld");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "https://w3id.org/gaia-x/core#Participant");
    VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "http://w3id.org/gaia-x/participant#Participant"); 
    assertNotNull(vr);
    assertTrue(vr instanceof VerificationResultParticipant);
    assertFalse(vr instanceof VerificationResultOffering);
    VerificationResultParticipant vrp = (VerificationResultParticipant) vr;
    assertEquals("http://gaiax.de", vrp.getId());
    assertEquals("http://gaiax.de", vrp.getIssuer());
    assertEquals("http://gaiax.de", vrp.getParticipantName()); // could be 'Provider Name'..
    assertNotNull(vrp.getClaims());
    assertEquals(26, vrp.getClaims().size()); //!!
    assertNull(vrp.getValidators());
    assertNull(vrp.getValidatorDids());
    assertEquals(Instant.parse("2022-10-19T18:48:09Z"), vrp.getIssuedDateTime());
  }

  @Test
  void validSyntax_ValidResourceNewSchema() {
    log.debug("validSyntax_ValidResourceNewSchema");
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/resourceSD.jsonld");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.RESOURCE, "https://w3id.org/gaia-x/core#Resource"); 
    VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.RESOURCE, "http://w3id.org/gaia-x/resource#Resource"); 
    assertNotNull(vr);
    assertTrue(vr instanceof VerificationResultResource);
    assertFalse(vr instanceof VerificationResultOffering);
    VerificationResultResource vrr = (VerificationResultResource) vr;
    assertEquals("did:example:fad49ec6-d488-4bf9-bae5-d0ffa62a9bd2", vrr.getId());
    assertEquals("did:web:compliance.lab.gaia-x.eu", vrr.getIssuer());
    assertEquals(Instant.parse("2023-08-08T11:29:40Z"), vrr.getIssuedDateTime());
    assertNotNull(vrr.getClaims());
    assertEquals(4, vrr.getClaims().size()); 
    assertNull(vrr.getValidators());
    assertNull(vrr.getValidatorDids());
  }
  
  @Test
  void validSyntax_LegalParticipantNewSchema() {
    log.debug("validSyntax_LegalParticipantNewSchema");
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/legalParticipant.jsonld");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "https://w3id.org/gaia-x/core#Participant"); 
    VerificationResult vr = verificationService.verifySelfDescription(content, true, true, false, false);
    assertNotNull(vr);
    assertTrue(vr instanceof VerificationResultParticipant);
    assertFalse(vr instanceof VerificationResultOffering);
    VerificationResultParticipant vrr = (VerificationResultParticipant) vr;
    //assertEquals("did:example:fad49ec6-d488-4bf9-bae5-d0ffa62a9bd2", vrr.getId());
    //assertEquals("did:web:compliance.lab.gaia-x.eu", vrr.getIssuer());
    assertEquals(Instant.parse("2024-03-15T12:40:58.486Z"), vrr.getIssuedDateTime());
    assertNotNull(vrr.getClaims());
    assertEquals(14, vrr.getClaims().size()); 
    assertEquals(3, schemaStore.getSchemaList().get(SchemaType.ONTOLOGY).size());
    schemaStore.deleteSchema("https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#");
    assertEquals(2, schemaStore.getSchemaList().get(SchemaType.ONTOLOGY).size());
    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifySelfDescription(content, true, true, false, false));
    assertEquals("Semantic Error: no proper CredentialSubject found", ex.getMessage());
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "http://w3id.org/gaia-x/participant#Participant"); 
  }

  @Test
  void invalidProof_InvalidSignatureType(){
    log.debug("invalidProof_InvalidSignatureType");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/syntax/input.vp.jsonld";
    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifySelfDescription(getAccessor(path)));
    assertEquals("Signatures error; The proof type is not supported yet: Ed25519Signature2018", ex.getMessage());
  }

  @Test
  void invalidProof_MissingProofs() {
    log.debug("invalidProof_MissingProofs");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/sign/hasNoSignature1.json";

    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifySelfDescription(getAccessor(path), false, true, true, true));
    assertEquals("Signatures error; No proof found", ex.getMessage());
    assertNull(ex.getCause());
  }

  @Test
  void invalidProof_UnknownVerificationMethod() {
    log.debug("invalidProof_UnknownVerificationMethod");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/sign/hasInvalidSignatureType.json";

    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifySelfDescription(getAccessor(path), false, true, true, false));
    assertEquals("Signatures error; Unknown Verification Method: https://example.edu/issuers/565049#key-1", ex.getMessage());
    assertNull(ex.getCause());
  }

  @Test
  void invalidProof_SignaturesMissing2() {
    log.debug("invalidProof_SignaturesMissing2");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/sign/lacksSomeSignatures.json";

    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifySelfDescription(getAccessor(path)));
    assertEquals("Signatures error; No proof found", ex.getMessage());
    assertNull(ex.getCause());
  }

  @Test
  void verifySignature_InvalidSignature() { 
    log.debug("verifySignature_InvalidSignature");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/sign/hasInvalidSignature.json";
    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifySelfDescription(getAccessor(path)));
    assertEquals("Signatures error; VerifiableCredential does not match with proof", ex.getMessage());
  }

  private static String pkey = """
	{
		"kty": "RSA",
		"e": "AQAB",
		"alg": "PS256",
		"n": "0nYZU6EuuzHKBCzkcBZqsMkVZXngYO7VujfLU_4ys7onF4HxTJPP3OGKEjbjbMgmpa7vKaWRomt_XXTjemA3r3f5t8bj0IoqFfvbTIq65GUIIh4y2mVbomdcQLRK2Auf79vDiqiONknTSstoPjAiCg6t6z_KruGFZbDOhYkZwqrjGnmB_LfFSlpeLwkQQ-5dVLhhXkImmWhnACoAo8ECny24Ap7wLbN9i9o1fNSz2uszACj0zxFhl3NGunHFUm3YkGd0URvoToXpK9a4zfihSUxHjeT0_7a9puVF4E3w1AAjSh4nV3pLE0cJyDITVb2M4d3m9tjjz_3XwjYiAAJ1MKVBSKDM27pexRFCJj_Dvb-dr-AImhqBhPDHn_gjdaRZIVoADC4zwBULkpvUaUIKmNFyYOjDYWWTBzTf4Gs9QL5adlVfVyK14MZPBOyq-cqIIymgp6A5_R3hKnCCBP8C_S0-VDidhI6Pr5VJPx9DydI0eB2DiOyOZvbfg7sKVkJXFUEJRiBTMhujyjYqeTtCHjCFHctZVQ8hU279eyk7mpmpDrktfCFJFi-00ZzQWTgtzBoGhke5hj0hjtG1n4jN6BfypdT5oB-DeXl2P1hp_hNC9I5gveWUYHAqN4VKve_52A3ub8vBlISQhEUeZoFUterTiDA3NyK7wsj_V7-KM6U"
	}""";  
    
  //@Test //TODO: think how to run it with the static key above
  void validSyntax_ValidSO() {
    log.debug("validSyntax_ValidSO");
    //schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    schemaStore.initializeDefaultSchemas();
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.SERVICE_OFFERING, "https://w3id.org/gaia-x/core#ServiceOffering");
    VerificationResult vr = verificationService.verifySelfDescription(getAccessor("Signature-Tests/gxfsSignarure.jsonld"), true, true, true, true);
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.SERVICE_OFFERING, "http://w3id.org/gaia-x/service#ServiceOffering");
    assertNotNull(vr);
  }
    
  @Test
  void validSD() {
    log.debug("validSD");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/sign/valid_signature.json";
    VerificationResult result = verificationService.verifySelfDescription(getAccessor(path), false, false, true, true);
    assertEquals(1, result.getValidators().size(), "Incorrect number of validators found");
  }

  @Test
  void validComplexSD() {
    log.debug("validComplexSD");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    String path = "VerificationService/sign/valid_complex_signature.json";
    VerificationResult result = verificationService.verifySelfDescription(getAccessor(path));
    assertEquals(1, result.getValidators().size(), "Incorrect number of validators found");
  }

  @Test
  void validComplexSDPartType() {
    schemaStore.initializeDefaultSchemas();
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "https://w3id.org/gaia-x/core#Participant");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.SERVICE_OFFERING, "https://w3id.org/gaia-x/core#ServiceOffering");
    String path = "VerificationService/syntax/complexSDPartType.jsonld";
    VerificationResult result = verificationService.verifySelfDescription(getAccessor(path), true, true, false, false);
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.SERVICE_OFFERING, "http://w3id.org/gaia-x/service#ServiceOffering");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "http://w3id.org/gaia-x/participant#Participant"); 
    assertNotNull(result);
    assertEquals(12, result.getClaims().size());
    List<SdClaim> expectedClaims = new ArrayList<>();
    expectedClaims.add(new SdClaim("_:b0", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#countrySubdivisionCode>", "\"FR-59\"")); 
    expectedClaims.add(new SdClaim("_:b1", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#countrySubdivisionCode>", "\"FR-59\"")); 
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#LegalParticipant>")); 
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#headquarterAddress>", "_:b0")); 
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalAddress>", "_:b1")); 
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalName>", "\"Riphixel\""));
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalRegistrationNumber>", "<https://www.riphixel.fr/workshop/demo2023/743ad60a-431c-4f45-bf15-e7bf19d64b10.json>")); 
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/be662688-2a48-48ea-bf23-43a4e83ee6e5.json>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#GaiaXTermsAndConditions>"));
    // cannot compare the multiline claim below 
    //expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/be662688-2a48-48ea-bf23-43a4e83ee6e5.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#termsAndConditions>", "\"The PARTICIPANT signing the Self-Description agrees as follows:\n- to update its descriptions about any changes, be it technical, organizational, or legal - especially but not limited to contractual in regards to the indicated attributes present in the descriptions.\n\nThe keypair used to sign Verifiable Credentials will be revoked where Gaia-X Association becomes aware of any inaccurate statements in regards to the claims which result in a non-compliance with the Trust Framework and policy rules defined in the Policy Rules and Labelling Document (PRLD).\"")); 
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/743ad60a-431c-4f45-bf15-e7bf19d64b10.json>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalRegistrationNumber>")); 
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/743ad60a-431c-4f45-bf15-e7bf19d64b10.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#vatID>", "\"FR52899103360\"")); 
    expectedClaims.add(new SdClaim("<https://www.riphixel.fr/workshop/demo2023/743ad60a-431c-4f45-bf15-e7bf19d64b10.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#vatID-countryCode>", "\"FR\""));
    for (SdClaim claim: expectedClaims) {
    	assertTrue(result.getClaims().contains(claim));
    }
  }
  
  @Test
  void invalidComplexSD2Types() {
    schemaStore.initializeDefaultSchemas();
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "https://w3id.org/gaia-x/core#Participant");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.SERVICE_OFFERING, "https://w3id.org/gaia-x/core#ServiceOffering");
    String path = "VerificationService/syntax/complexSD2Types.jsonld";
    Exception ex = assertThrowsExactly(VerificationException.class, () -> verificationService.verifySelfDescription(getAccessor(path), true, true, false, false));
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.SERVICE_OFFERING, "http://w3id.org/gaia-x/service#ServiceOffering");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.PARTICIPANT, "http://w3id.org/gaia-x/participant#Participant"); 
    assertEquals("Semantic error: SD has several types: [" + TrustFrameworkBaseClass.PARTICIPANT + ", " + TrustFrameworkBaseClass.SERVICE_OFFERING + "]", ex.getMessage());
  }
  
  @Test
  void extractClaims_providerTest() {
    log.debug("extractClaims_providerTest");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/providerTest.jsonld");
    VerificationResult result = verificationService.verifySelfDescription(content, true, true, false, false);
    List<SdClaim> actualClaims = result.getClaims();
    log.debug("extractClaims_providerTest; actual claims: {}", actualClaims);
    Set<SdClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new SdClaim("<http://example.org/test-issuer>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/participant#Provider>"));
    expectedClaims.add(new SdClaim("<http://example.org/test-issuer>", "<http://w3id.org/gaia-x/participant#name>", "\"deltaDAO AG\""));
    expectedClaims.add(new SdClaim("<http://example.org/test-issuer>", "<http://w3id.org/gaia-x/participant#legalName>", "\"deltaDAO AG\""));
    expectedClaims.add(new SdClaim("<http://example.org/test-issuer>", "<http://w3id.org/gaia-x/participant#legalAddress>", "_:b0"));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/participant#Address>"));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#country>", "\"DE\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#locality>", "\"Hamburg\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#postal-code>", "\"22303\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#street-address>", "\"Geibelstraße 46b\""));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
  }

  @Test
  void extractClaims_participantTest() {
    log.debug("extractClaims_participantTest");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantSD.jsonld");
    VerificationResult result = verificationService.verifySelfDescription(content, true, false, false, false);
    List<SdClaim> actualClaims = result.getClaims();
    log.debug("extractClaims_participantTest; actual claims: {}", actualClaims);

    Set<SdClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new SdClaim("<did:web:delta-dao.com>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/participant#LegalPerson>"));
    expectedClaims.add(new SdClaim("<did:web:delta-dao.com>", "<http://w3id.org/gaia-x/participant#legalName>", "\"deltaDAO AG\""));
    expectedClaims.add(new SdClaim("<did:web:delta-dao.com>", "<http://w3id.org/gaia-x/participant#registrationNumber>", "\"DEK1101R.HRB170364\""));
    expectedClaims.add(new SdClaim("<did:web:delta-dao.com>", "<http://w3id.org/gaia-x/participant#leiCode>", "\"391200FJBNU0YW987L26\""));
    expectedClaims.add(new SdClaim("<did:web:delta-dao.com>", "<http://w3id.org/gaia-x/participant#ethereumAddress>", "\"0x4C84a36fCDb7Bc750294A7f3B5ad5CA8F74C4A52\""));
    expectedClaims.add(new SdClaim("<did:web:delta-dao.com>", "<http://w3id.org/gaia-x/participant#headquarterAddress>", "_:b0"));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#street-address>", "\"Geibelstraße 46b\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#locality>", "\"Hamburg\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/participant#Address>"));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#country>", "\"DE\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#postal-code>", "\"22303\""));
    expectedClaims.add(new SdClaim("<did:web:delta-dao.com>", "<http://w3id.org/gaia-x/participant#legalAddress>", "_:b1"));
    expectedClaims.add(new SdClaim("_:b1", "<http://w3id.org/gaia-x/participant#street-address>", "\"Geibelstraße 46b\""));
    expectedClaims.add(new SdClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/participant#Address>"));
    expectedClaims.add(new SdClaim("_:b1", "<http://w3id.org/gaia-x/participant#postal-code>", "\"22303\""));
    expectedClaims.add(new SdClaim("_:b1", "<http://w3id.org/gaia-x/participant#locality>", "\"Hamburg\""));
    expectedClaims.add(new SdClaim("_:b1", "<http://w3id.org/gaia-x/participant#country>", "\"DE\""));
    expectedClaims.add(new SdClaim("<did:web:delta-dao.com>", "<http://w3id.org/gaia-x/service#TermsAndConditions>", "_:b2"));
    expectedClaims.add(new SdClaim("_:b2", "<http://w3id.org/gaia-x/service#url>", "\"https://gaia-x.gitlab.io/policy-rules-committee/trust-framework/participant/#legal-person\""));
    expectedClaims.add(new SdClaim("_:b2", "<http://w3id.org/gaia-x/service#hash>", "\"36ba819f30a3c4d4a7f16ee0a77259fc92f2e1ebf739713609f1c11eb41499e7aa2cd3a5d2011e073f9ba9c107493e3e8629cc15cd4fc07f67281d7ea9023db0\""));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
  }

  @Test
  void extractClaims_participantTwoVCsTest() {
    log.debug("extractClaims_participantTwoVCsTest");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantTwoVCs.jsonld");
    VerificationResult result = verificationService.verifySelfDescription(content, true, true, false, false);
    List<SdClaim> actualClaims = result.getClaims();
    log.debug("extractClaims_participantTest; actual claims: {}", actualClaims);
    List<SdClaim> expectedClaims = new ArrayList<>();
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://www.w3.org/2006/vcard/ns#Address>"));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#country-name>", "\"Country\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#locality>", "\"Town Name\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#postal-code>", "\"1234\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#street-address>", "\"Street Name\""));
    expectedClaims.add(new SdClaim("<http://w3id.org/gaia-x/participant#Provider1>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/participant#LegalPerson>"));
    expectedClaims.add(new SdClaim("<http://w3id.org/gaia-x/participant#Provider1>", "<http://w3id.org/gaia-x/participant#headquarterAddress>", "_:b0"));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://www.w3.org/2006/vcard/ns#Address>"));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#country-name>", "\"Country\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#locality>", "\"Town Name\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#postal-code>", "\"1234\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#street-address>", "\"Street Name\""));
    expectedClaims.add(new SdClaim("<http://w3id.org/gaia-x/participant#Provider1>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/participant#LegalPerson>"));
    expectedClaims.add(new SdClaim("<http://w3id.org/gaia-x/participant#Provider1>", "<http://w3id.org/gaia-x/participant#legalAddress>", "_:b0"));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, actualClaims);
  }

  @Test
  void extractClaims_jsonValueCharacterTest() throws Exception {
    log.debug("validSyntax_jsonValueCharacterTest");
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/specialCharacters.jsonld");
    verificationService.setBaseClassUri(TrustFrameworkBaseClass.RESOURCE, "https://w3id.org/gaia-x/core#Resource");
    VerificationResult result = verificationService.verifySelfDescription(content, true, false, false, false);
    List<SdClaim> actualClaims = result.getClaims();
    log.debug("validSyntax_jsonValueCharacterTest; actual claims: {}", actualClaims);
    Set<SdClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new SdClaim("<did:web:example.com:fad49ec6-d488-4bf9-bae5-d0ffa62a9bd2>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/gax-trust-framework#Resource>"));
    expectedClaims.add(new SdClaim("<did:web:example.com:fad49ec6-d488-4bf9-bae5-d0ffa62a9bd2>", "<http://purl.org/dc/terms/description>", "\"\\n \\\\ Test with </\\\"s>pecial\\\" \\\\ / characters \\b </\\f \\n \\r \\t 🔥\""));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
  }

  @Test
  void extractClaims_participantTwoAdditionalContextTest() throws Exception {
    log.debug("extractClaims_participantTwoVCsTest");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantTwoAdditionalContext.jsonld");
    try {
		verificationService.verifySelfDescription(content, true, true, true, false);
		fail("Signature error expected");
	} catch (VerificationException e) {
		assertFalse(e.getMessage().contains("Imported context is null"), "Context related error message not expecteed");
		assertTrue(e.getMessage().contains("VerifiablePresentation does not match with proof"), "Exception message not expecteed");
	}
  }
  
  @Test
  void extractClaims_participantTwoCSsTest() {
    log.debug("extractClaims_participantTwoCSsTest");
    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantTwoCSs.jsonld");
    VerificationResult result = verificationService.verifySelfDescription(content, true, true, false, false);
    List<SdClaim> actualClaims = result.getClaims();
    log.debug("extractClaims_participantTest; actual claims: {}", actualClaims);

    Set<SdClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new SdClaim("<http://w3id.org/gaia-x/participant#Provider1>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/participant#LegalPerson>"));
    expectedClaims.add(new SdClaim("<http://w3id.org/gaia-x/participant#Provider1>", "<http://w3id.org/gaia-x/participant#headquarterAddress>", "_:b0"));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://www.w3.org/2006/vcard/ns#Address>"));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#postal-code>", "\"1234\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#country-name>", "\"Country\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#street-address>", "\"Street Name\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#locality>", "\"Town Name\""));
    expectedClaims.add(new SdClaim("_:b0", "<http://w3id.org/gaia-x/participant#legalAddress>", "_:b1"));
    expectedClaims.add(new SdClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://www.w3.org/2006/vcard/ns#Address>"));
    expectedClaims.add(new SdClaim("_:b1", "<http://www.w3.org/2006/vcard/ns#street-address>", "\"Street Name\""));
    expectedClaims.add(new SdClaim("_:b1", "<http://www.w3.org/2006/vcard/ns#country-name>", "\"Country\""));
    expectedClaims.add(new SdClaim("_:b1", "<http://www.w3.org/2006/vcard/ns#postal-code>", "\"1234\""));
    expectedClaims.add(new SdClaim("_:b1", "<http://www.w3.org/2006/vcard/ns#locality>", "\"Town Name\""));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
  }

  @Test
  void verifyValidationResultInvalid() {
    log.debug("verifyValidationResult");
    SemanticValidationResult validationResult = verificationService.verifySelfDescriptionAgainstSchema(
            getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"), getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

    if (!validationResult.isConforming()) {
      assertTrue(validationResult.getValidationReport().contains("Property needs to have at least 1 value"));
    } else {
      assertFalse(validationResult.isConforming());
    }
  }

  @Test
  void verifyValidationResultValid() {
    log.debug("verifyValidationResult");
    SemanticValidationResult validationResult = verificationService.verifySelfDescriptionAgainstSchema(
            getAccessor("Validation-Tests/legalPerson_one_VC_Valid.jsonld"), getAccessor("Schema-Tests/mergedShapesGraph.ttl"));
    assertTrue(validationResult.isConforming());

  }

  @Test
  void verifyInvalidSDValidation_Result_Against_CompositeSchema() {
    log.debug("verifyInvalidSDValidation_Result_Against_CompositeSchema_bug");
    schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));
    schemaStore.addSchema(getAccessor("Validation-Tests/legal-personShape.ttl"));
    SemanticValidationResult result = verificationService.verifySelfDescriptionAgainstCompositeSchema(
    		getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"));
    assertFalse(result.isConforming(), "Validation should have failed.");
    assertTrue(result.getValidationReport().contains("Property needs to have at least 1 value"));
  }

  @Test
  void verifyValidVP_SDValidationCompositeSchema() {
    log.debug("verifyValidVP_SDValidationCompositeSchema");
    schemaStore.addSchema(getAccessor("Validation-Tests/legal-personShape.ttl"));
    schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));
    SemanticValidationResult validationResult = verificationService.verifySelfDescriptionAgainstCompositeSchema(
            getAccessor("Validation-Tests/legalPerson_one_VC_Valid.jsonld"));
    assertTrue(validationResult.isConforming());
  }
  
}
