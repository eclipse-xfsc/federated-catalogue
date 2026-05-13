package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import eu.xfsc.fc.core.pojo.*;

import eu.xfsc.fc.core.util.TestUtil;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import org.springframework.jdbc.core.JdbcTemplate;

import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.validation.rdf.RdfAssetParser;
import eu.xfsc.fc.core.service.validation.strategy.ShaclValidationExecutor;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;
import eu.xfsc.fc.core.service.verification.claims.JenaAllTriplesExtractor;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import lombok.extern.slf4j.Slf4j;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link VerificationServiceImpl}.
 *
 * <p>All test fixtures use Gaia-X 2511 type URIs. Type resolution in
 * {@code ClaimValidator.checkTypeSubClass()} works via exact match or SPARQL {@code rdfs:subClassOf}
 * lookup against the bundled 2511 ontology.
 */
@Slf4j
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {VerificationServiceTest.TestApplication.class, FileStoreConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class,
        VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaJpaDao.class, SchemaAuditRepository.class, DatabaseConfig.class, DidResolverConfig.class, ValidatorCacheJpaDao.class, HttpDocumentResolver.class,
        ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class, SecurityAuditorAware.class, JenaAllTriplesExtractor.class, ClaimExtractionService.class,
        RdfAssetParser.class, ShaclValidationExecutor.class, LoireJwtParser.class})
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
  private ClaimExtractionService claimExtractionService;

    @Autowired
  private JdbcTemplate jdbcTemplate;

  @MockitoBean
  private JwtSignatureVerifier jwtVerifierMock;

  @MockitoSpyBean
  private JwtContentPreprocessor jwtPreprocessorSpy;

  @MockitoSpyBean
  private Vc2Processor vc2ProcessorSpy;

  @MockitoSpyBean
  private LoireJwtParser loireJwtParserSpy;

  @Autowired
  private ProtectedNamespaceProperties protectedNsProps;

  @Autowired
  private SchemaStoreImpl schemaStore;

  @AfterEach
  public void storageSelfCleaning() throws IOException {
    schemaStore.clear();
    verificationService.setVerifySchema(false);
  }

  @Test
  void verifyCredential_nonVcJsonLd_processedAsNonCredentialRdf() {
      // JSON-LD without VC 2.0 context → UNKNOWN format → non-credential RDF path
      // (VC 1.1 format is not recognized by Loire or DanubeTech matchers)
      ContentAccessor content = new ContentAccessorDirect("""
        {
                "@context": {"ex": "http://example.org/"},
                "ex:type": "ex:CustomType",
                "ex:value": "Test"
        }
              """);

      CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

      assertNotNull(result);
      assertNotNull(result.getClaims());
      assertFalse(result.getClaims().isEmpty(), "Non-VC JSON-LD is processed as non-credential RDF");
  }

  @Test
  void verifyCredential_vc2JwtWrappedInput_vc2ProcessorPreProcessInvoked() {
    String vcJson = getAccessor("Claims-Tests/participantVC2.jsonld").getContentAsString();
    ContentAccessor content = new ContentAccessorDirect(fakeVcJwt(vcJson));

    verificationService.verifyCredential(content, false, false, false, false);

    verify(vc2ProcessorSpy).preProcess(any());
  }

  @Test
  void invalidSyntax_MissingQuote() {
      // Malformed JSON (missing quote) → UNKNOWN format → JenaAllTriplesExtractor fails all formats
    String path = "VerificationService/syntax/missingQuote.jsonld";
    ContentAccessor content = getAccessor(path);
    Exception ex = assertThrowsExactly(ClientException.class, ()
            -> verificationService.verifyCredential(content));
      assertTrue(ex.getMessage().startsWith("Non-credential RDF parse failed: "),
              "Expected non-credential RDF parse error but got: " + ex.getMessage());
    assertNotNull(ex.getCause());
  }

  @Test
  void verifyCredential_plainJsonLd_noVcContext_processedAsNonCredentialRdf() {
      // Plain JSON-LD without VC 2.0 context → UNKNOWN → non-credential RDF path
      ContentAccessor content = new ContentAccessorDirect("""
              {
                "@context": {"ex": "http://example.org/"},
                "@id": "http://example.org/person1",
                "ex:name": "Test Person"
              }
              """);

      CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

      assertNotNull(result);
      assertNotNull(result.getClaims());
      assertFalse(result.getClaims().isEmpty(), "Plain JSON-LD must extract triples as non-credential RDF");
  }

    @Test
    void verifyCredential_emptyRdf_throwsClientException() {
        // Valid JSON-LD that produces zero triples → UNKNOWN path → empty-triples guard
        ContentAccessor content = new ContentAccessorDirect("""
                {
                  "@context": {"ex": "http://example.org/"},
                  "@graph": []
                }
                """);

        Exception ex = assertThrowsExactly(ClientException.class, ()
                -> verificationService.verifyCredential(content, false, false, false, false));
        assertEquals("Non-credential RDF content contains no triples", ex.getMessage());
    }

  @Test
  void validVCnoVP() {
    // Standalone Loire VC JWT (not wrapped in VP) — LegalPerson resolves to PARTICIPANT
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    String jwt = fakeLoireVcJwtWithType("did:web:example.com",
        "https://w3id.org/gaia-x/2511#LegalPerson");
    ContentAccessor content = new ContentAccessorDirect(jwt);

    CredentialVerificationResult vr = verificationService.verifyCredential(content, true, false, false, false);
    assertNotNull(vr);
    assertInstanceOf(CredentialVerificationResultParticipant.class, vr);
    CredentialVerificationResultParticipant vrp = (CredentialVerificationResultParticipant) vr;
    assertEquals("did:web:example.com", vrp.getIssuer());
    assertEquals(vrp.getParticipantName(), vrp.getIssuer());
  }

  @Test
  void validVCUnknownType() {
    // With gaiax disabled (default in tests), non-Gaia-X credentials pass the semantics check.
    // verifyVCSignatures=false because input.vc.jsonld has a dummy proof that would fail verification.
    String path = "VerificationService/jsonld/input.vc.jsonld";
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    boolean verifySemantics = true;
    boolean verifyVcSigs = false;
    CredentialVerificationResult vr = verificationService.verifyCredential(getAccessor(path),
        verifySemantics, false, false, verifyVcSigs);
    assertNotNull(vr);
    assertEquals("did:example:ebfeb1f712ebc6f1c276e12ec21", vr.getId());
    assertEquals("https://example.edu/issuers/565049", vr.getIssuer());
  }

  @Test
  void validVCUnknownType_defaultConfig_throwsSignatureError() {
    // Documents that input.vc.jsonld carries a dummy proof: default config (verifyVCSignatures=true) rejects it.
    String path = "VerificationService/jsonld/input.vc.jsonld";
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    Exception ex = assertThrowsExactly(VerificationException.class,
        () -> verificationService.verifyCredential(getAccessor(path), true, false, false, true));
    assertTrue(ex.getMessage().contains("Signatures error"),
        "Expected signature error but got: " + ex.getMessage());
  }

  @Test
  void validVCUnknownType_gaiaxEnabled_throwsNoProperSubjectError() {
    // With gaiax enabled, non-Gaia-X credentials are rejected by the semantics check.
    jdbcTemplate.update("UPDATE trust_frameworks SET enabled = true WHERE id = 'gaia-x'");
    try {
      String path = "VerificationService/jsonld/input.vc.jsonld";
      schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
      Exception ex = assertThrowsExactly(VerificationException.class,
          () -> verificationService.verifyCredential(getAccessor(path), true, false, false, false));
      assertEquals("Semantic Error: no proper CredentialSubject found", ex.getMessage());
    } finally {
      jdbcTemplate.update("UPDATE trust_frameworks SET enabled = false WHERE id = 'gaia-x'");
    }
  }

  @Test
  void validVPUnknownType() {
    // With gaiax disabled (default in tests), non-Gaia-X VP credentials pass the semantics check.
    // verifyVPSignatures=false because input.vp.jsonld uses Ed25519Signature2018 which is unsupported.
    String path = "VerificationService/jsonld/input.vp.jsonld";
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    boolean verifySemantics = true;
    boolean verifyVpSigs = false;
    CredentialVerificationResult vr = verificationService.verifyCredential(getAccessor(path),
        verifySemantics, false, verifyVpSigs, false);
    assertNotNull(vr);
    assertEquals("did:key:z6MkjRagNiMu91DduvCvgEsqLZDVzrJzFrwahc4tXLt9DoHd", vr.getId());
    assertEquals("did:v1:test:nym:z6MkhdmzFu659ZJ4XKj31vtEDmjvsi5yDZG5L7Caz63oP39k", vr.getIssuer());
  }

  @Test
  void validSyntax_Participant() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    String path = "VerificationService/syntax/participantCredential2.jsonld";
    // verifyVCSigs=false: JWS in fixture was computed over original data; cannot re-sign (external GXDCH key)
    CredentialVerificationResult vr = verificationService.verifyCredential(getAccessor(path), true, false, false, false);
    assertNotNull(vr);
    assertInstanceOf(CredentialVerificationResultParticipant.class, vr);
    CredentialVerificationResultParticipant vrp = (CredentialVerificationResultParticipant) vr;
    assertEquals("https://www.handelsregister.de/", vrp.getId());
    assertEquals("https://www.handelsregister.de/", vrp.getIssuer());
    assertEquals(Instant.parse("2010-01-01T19:37:24Z"), vrp.getIssuedDateTime());
  }

  // validSyntax_ValidCredentialVP removed — coverage provided by validSyntax_LegalParticipantNewSchema


  @Test
  void validSyntax_ValidServiceNewSchema() {
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/serviceOffering2.jsonld");
    CredentialVerificationResult vr = verificationService.verifyCredential(content, true, true, false, false);
    assertNotNull(vr);
    assertInstanceOf(CredentialVerificationResultOffering.class, vr);
    CredentialVerificationResultOffering vro = (CredentialVerificationResultOffering) vr;
    assertEquals("https://www.example.org/mySoftwareOffering", vro.getId());
    assertEquals("http://gaiax.de", vro.getIssuer());
    assertNotNull(vro.getClaims());
    assertEquals(17, vro.getClaims().size());
    assertTrue(vro.getValidators().isEmpty());
    assertTrue(vro.getValidatorDids().isEmpty());
    assertEquals(Instant.parse("2022-10-19T18:48:09Z"), vro.getIssuedDateTime());
  }


  @Test
  void validSyntax_ValidPersonNewSchema() {
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/legalPerson2.jsonld");
    CredentialVerificationResult vr = verificationService.verifyCredential(content, true, true, false, false);
    assertNotNull(vr);
    assertInstanceOf(CredentialVerificationResultParticipant.class, vr);
    CredentialVerificationResultParticipant vrp = (CredentialVerificationResultParticipant) vr;
    assertEquals("http://gaiax.de", vrp.getId());
    assertEquals("http://gaiax.de", vrp.getIssuer());
    assertEquals("http://gaiax.de", vrp.getParticipantName()); // could be 'Provider Name'..
    assertNotNull(vrp.getClaims());
    assertEquals(10, vrp.getClaims().size());
    assertTrue(vrp.getValidators().isEmpty());
    assertTrue(vrp.getValidatorDids().isEmpty());
    assertEquals(Instant.parse("2022-10-19T18:48:09Z"), vrp.getIssuedDateTime());
  }

  @Test
  void validSyntax_ValidResourceNewSchema() {
    // Loire VP JWT containing a VirtualResource VC — resolves to RESOURCE via 2511 ontology
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    String vcJson = """
        {"@context":["https://www.w3.org/ns/credentials/v2"],\
        "type":["VerifiableCredential"],\
        "issuer":"did:web:example.com",\
        "validFrom":"2024-01-01T00:00:00Z",\
        "credentialSubject":{"@id":"did:web:resource.example.com",\
        "@type":["https://w3id.org/gaia-x/2511#VirtualResource"]}}""";
    String vpJwt = fakeLoireVpJwtWithVc("did:web:example.com", vcJson);
    ContentAccessor content = new ContentAccessorDirect(vpJwt);

    CredentialVerificationResult vr = verificationService.verifyCredential(content, true, false, false, false);
    assertNotNull(vr);
    assertInstanceOf(CredentialVerificationResultResource.class, vr);
    CredentialVerificationResultResource vrr = (CredentialVerificationResultResource) vr;
    assertEquals("did:web:example.com", vrr.getIssuer());
    assertNotNull(vrr.getClaims());
    assertTrue(vrr.getValidators().isEmpty());
    assertTrue(vrr.getValidatorDids().isEmpty());
  }

  @Test
  void validSyntax_LegalParticipantNewSchema() {
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/legalParticipant.jsonld");
    CredentialVerificationResult vr = verificationService.verifyCredential(content, true, true, false, false);
    assertNotNull(vr);
    assertInstanceOf(CredentialVerificationResultParticipant.class, vr);
    CredentialVerificationResultParticipant vrr = (CredentialVerificationResultParticipant) vr;
    //assertEquals("did:example:fad49ec6-d488-4bf9-bae5-d0ffa62a9bd2", vrr.getId());
    //assertEquals("did:web:compliance.lab.gaia-x.eu", vrr.getIssuer());
    assertEquals(Instant.parse("2024-03-15T12:40:58.486Z"), vrr.getIssuedDateTime());
    assertNotNull(vrr.getClaims());
    assertEquals(10, vrr.getClaims().size());
    assertEquals(1, schemaStore.getSchemaList().get(SchemaType.ONTOLOGY).size());
    schemaStore.deleteSchema("https://w3id.org/gaia-x/2511");
    assertTrue(schemaStore.getSchemaList().get(SchemaType.ONTOLOGY).isEmpty());
    // With gaiax enabled, removing the required ontology schema causes hasClasses() to fail.
    jdbcTemplate.update("UPDATE trust_frameworks SET enabled = true WHERE id = 'gaia-x'");
    try {
      Exception ex = assertThrowsExactly(VerificationException.class, ()
          -> verificationService.verifyCredential(content, true, true, false, false));
      assertEquals("Semantic Error: no proper CredentialSubject found", ex.getMessage());
    } finally {
      jdbcTemplate.update("UPDATE trust_frameworks SET enabled = false WHERE id = 'gaia-x'");
    }
  }

  @Test
  void verifyCredential_ldCredentialWithVpSigsTrue_rejectsLdProof() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    String path = "VerificationService/sign/hasNoSignature1.json";

    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifyCredential(getAccessor(path), false, true, true, true));
    assertTrue(ex.getMessage().contains("Linked Data proof verification is not supported"),
        "Should reject LD credential when signature verification is requested. Got: " + ex.getMessage());
  }

  @Test
  void verifyCredential_ldCredentialWithDefaultFlags_rejectsLdProof() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    String path = "VerificationService/sign/hasNoSignature1.json";

    Exception ex = assertThrowsExactly(VerificationException.class, ()
            -> verificationService.verifyCredential(getAccessor(path)));
    assertTrue(ex.getMessage().contains("Linked Data proof verification is not supported"),
        "Should reject LD credential with default flags. Got: " + ex.getMessage());
  }


  @Test
  void validComplexCredentialPartType() {
    schemaStore.initializeDefaultSchemas();
    String path = "VerificationService/syntax/complexCredentialPartType.jsonld";
    CredentialVerificationResult result = verificationService.verifyCredential(getAccessor(path), true, true, false, false);
    assertNotNull(result);
    assertEquals(12, result.getClaims().size());
    List<RdfClaim> expectedClaims = new ArrayList<>();
    expectedClaims.add(new CredentialClaim("_:b0", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#countrySubdivisionCode>", "\"FR-59\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#countrySubdivisionCode>", "\"FR-59\""));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#LegalParticipant>"));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#headquarterAddress>", "_:b0"));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalAddress>", "_:b1"));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalName>", "\"Riphixel\""));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/8255722c-35de-4741-90b2-650040b3fa57.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalRegistrationNumber>", "<https://www.riphixel.fr/workshop/demo2023/743ad60a-431c-4f45-bf15-e7bf19d64b10.json>"));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/be662688-2a48-48ea-bf23-43a4e83ee6e5.json>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#GaiaXTermsAndConditions>"));
    // cannot compare the multiline claim below
    //expectedClaims.add(new Claim("<https://www.riphixel.fr/workshop/demo2023/be662688-2a48-48ea-bf23-43a4e83ee6e5.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#termsAndConditions>", "\"The PARTICIPANT signing the Self-Description agrees as follows:\n- to update its descriptions about any changes, be it technical, organizational, or legal - especially but not limited to contractual in regards to the indicated attributes present in the descriptions.\n\nThe keypair used to sign Verifiable Credentials will be revoked where Gaia-X Association becomes aware of any inaccurate statements in regards to the claims which result in a non-compliance with the Trust Framework and policy rules defined in the Policy Rules and Labelling Document (PRLD).\""));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/743ad60a-431c-4f45-bf15-e7bf19d64b10.json>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#legalRegistrationNumber>"));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/743ad60a-431c-4f45-bf15-e7bf19d64b10.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#vatID>", "\"FR52899103360\""));
    expectedClaims.add(new CredentialClaim("<https://www.riphixel.fr/workshop/demo2023/743ad60a-431c-4f45-bf15-e7bf19d64b10.json>", "<https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#vatID-countryCode>", "\"FR\""));
    for (RdfClaim claim: expectedClaims) {
    	assertTrue(result.getClaims().contains(claim));
    }
  }

  @Test
  void invalidComplexCredential2Types() {
    schemaStore.initializeDefaultSchemas();
    String path = "VerificationService/syntax/complexCredential2Types.jsonld";
    Exception ex = assertThrowsExactly(VerificationException.class, () -> verificationService.verifyCredential(getAccessor(path), true, true, false, false));
    assertEquals("Semantic error: credential has several types: [" + TrustFrameworkBaseClass.PARTICIPANT + ", " + TrustFrameworkBaseClass.SERVICE_OFFERING + "]", ex.getMessage());
  }

  @Test
  void extractClaims_providerTest() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/providerTest.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, true, false, false);
    List<RdfClaim> actualClaims = result.getClaims();
    Set<RdfClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new CredentialClaim("<http://example.org/test-issuer>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#LegalPerson>"));
    expectedClaims.add(new CredentialClaim("<http://example.org/test-issuer>", "<https://w3id.org/gaia-x/2511#name>", "\"deltaDAO AG\""));
    expectedClaims.add(new CredentialClaim("<http://example.org/test-issuer>", "<https://w3id.org/gaia-x/2511#legalName>", "\"deltaDAO AG\""));
    expectedClaims.add(new CredentialClaim("<http://example.org/test-issuer>", "<https://w3id.org/gaia-x/2511#legalAddress>", "_:b0"));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#Address>"));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#country>", "\"DE\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#locality>", "\"Hamburg\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#postal-code>", "\"22303\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#street-address>", "\"Geibelstraße 46b\""));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
  }

  @Test
  void extractClaims_participantTest() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantCredential.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, false, false, false);
    List<RdfClaim> actualClaims = result.getClaims();

    Set<RdfClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#LegalPerson>"));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#legalName>", "\"deltaDAO AG\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#registrationNumber>", "\"DEK1101R.HRB170364\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#leiCode>", "\"391200FJBNU0YW987L26\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#ethereumAddress>", "\"0x4C84a36fCDb7Bc750294A7f3B5ad5CA8F74C4A52\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#TermsAndConditions>", "_:b0"));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#url>", "\"https://gaia-x.gitlab.io/policy-rules-committee/trust-framework/participant/#legal-person\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#hash>", "\"36ba819f30a3c4d4a7f16ee0a77259fc92f2e1ebf739713609f1c11eb41499e7aa2cd3a5d2011e073f9ba9c107493e3e8629cc15cd4fc07f67281d7ea9023db0\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#headquarterAddress>", "_:b1"));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#street-address>", "\"Geibelstraße 46b\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#locality>", "\"Hamburg\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#Address>"));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#country>", "\"DE\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#postal-code>", "\"22303\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#legalAddress>", "_:b2"));
    expectedClaims.add(new CredentialClaim("_:b2", "<https://w3id.org/gaia-x/2511#street-address>", "\"Geibelstraße 46b\""));
    expectedClaims.add(new CredentialClaim("_:b2", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#Address>"));
    expectedClaims.add(new CredentialClaim("_:b2", "<https://w3id.org/gaia-x/2511#postal-code>", "\"22303\""));
    expectedClaims.add(new CredentialClaim("_:b2", "<https://w3id.org/gaia-x/2511#locality>", "\"Hamburg\""));
    expectedClaims.add(new CredentialClaim("_:b2", "<https://w3id.org/gaia-x/2511#country>", "\"DE\""));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
  }

  @Test
  void extractClaims_participantTwoVCsTest() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantTwoVCs.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, true, false, false);
    List<RdfClaim> actualClaims = result.getClaims();
    List<RdfClaim> expectedClaims = new ArrayList<>();
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://www.w3.org/2006/vcard/ns#Address>"));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#country-name>", "\"Country\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#locality>", "\"Town Name\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#postal-code>", "\"1234\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#street-address>", "\"Street Name\""));
    expectedClaims.add(new CredentialClaim("<https://w3id.org/gaia-x/2511#Provider1>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#LegalPerson>"));
    expectedClaims.add(new CredentialClaim("<https://w3id.org/gaia-x/2511#Provider1>", "<https://w3id.org/gaia-x/2511#headquarterAddress>", "_:b0"));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://www.w3.org/2006/vcard/ns#Address>"));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#country-name>", "\"Country\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#locality>", "\"Town Name\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#postal-code>", "\"1234\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#street-address>", "\"Street Name\""));
    expectedClaims.add(new CredentialClaim("<https://w3id.org/gaia-x/2511#Provider1>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#LegalPerson>"));
    expectedClaims.add(new CredentialClaim("<https://w3id.org/gaia-x/2511#Provider1>", "<https://w3id.org/gaia-x/2511#legalAddress>", "_:b0"));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, actualClaims);
  }

  @Test
  void extractClaims_jsonValueCharacterTest() {
    schemaStore.initializeDefaultSchemas();
    ContentAccessor content = getAccessor("VerificationService/syntax/specialCharacters.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, false, false, false);
    List<RdfClaim> actualClaims = result.getClaims();
    Set<RdfClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new CredentialClaim("<did:web:example.com:fad49ec6-d488-4bf9-bae5-d0ffa62a9bd2>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#Resource>"));
    expectedClaims.add(new CredentialClaim("<did:web:example.com:fad49ec6-d488-4bf9-bae5-d0ffa62a9bd2>", "<http://purl.org/dc/terms/description>", "\"\\n \\\\ Test with </\\\"s>pecial\\\" \\\\ / characters \\b </\\f \\n \\r \\t 🔥\""));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
  }

  @Test
  void extractClaims_participantTwoAdditionalContextTest() {
    // Loire VP JWT with two VCs that have additional inline contexts.
    // Verifies claim extraction succeeds without null-context errors.
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    String vc1Json = """
        {"@context":["https://www.w3.org/ns/credentials/v2",\
        {"vcard":"http://www.w3.org/2006/vcard/ns#"}],\
        "type":["VerifiableCredential"],\
        "issuer":"did:web:example.com",\
        "validFrom":"2024-01-01T00:00:00Z",\
        "credentialSubject":{"@id":"did:web:provider.example.com",\
        "@type":["https://w3id.org/gaia-x/2511#LegalPerson"],\
        "vcard:country-name":"Germany"}}""";
    String vc2Json = """
        {"@context":["https://www.w3.org/ns/credentials/v2",\
        {"vcard":"http://www.w3.org/2006/vcard/ns#"}],\
        "type":["VerifiableCredential"],\
        "issuer":"did:web:example.com",\
        "validFrom":"2024-01-01T00:00:00Z",\
        "credentialSubject":{"@id":"did:web:provider.example.com",\
        "@type":["https://w3id.org/gaia-x/2511#LegalPerson"],\
        "vcard:locality":"Berlin"}}""";
    String vpJwt = fakeLoireVpJwtWithVc("did:web:example.com", vc1Json, vc2Json);
    ContentAccessor content = new ContentAccessorDirect(vpJwt);

    CredentialVerificationResult result = verificationService.verifyCredential(content, true, false, false, false);
    assertNotNull(result, "Claims extraction should succeed with additional contexts");
    assertNotNull(result.getClaims(), "Claims should not be null");
  }

  @Test
  void extractClaims_participantTwoCSsTest() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantTwoCSs.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, true, false, false);
    List<RdfClaim> actualClaims = result.getClaims();

    Set<RdfClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new CredentialClaim("<https://w3id.org/gaia-x/2511#Provider1>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#LegalPerson>"));
    expectedClaims.add(new CredentialClaim("<https://w3id.org/gaia-x/2511#Provider1>", "<https://w3id.org/gaia-x/2511#headquarterAddress>", "_:b0"));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://www.w3.org/2006/vcard/ns#Address>"));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#postal-code>", "\"1234\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#country-name>", "\"Country\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#street-address>", "\"Street Name\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/2006/vcard/ns#locality>", "\"Town Name\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#legalAddress>", "_:b1"));
    expectedClaims.add(new CredentialClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://www.w3.org/2006/vcard/ns#Address>"));
    expectedClaims.add(new CredentialClaim("_:b1", "<http://www.w3.org/2006/vcard/ns#street-address>", "\"Street Name\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<http://www.w3.org/2006/vcard/ns#country-name>", "\"Country\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<http://www.w3.org/2006/vcard/ns#postal-code>", "\"1234\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<http://www.w3.org/2006/vcard/ns#locality>", "\"Town Name\""));
    assertEquals(expectedClaims.size(), actualClaims.size());
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
  }

  @Test
  void verifyValidationResultInvalid() {
    SchemaValidationResult validationResult = verificationService.verifyCredentialAgainstSchema(
            getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"), getAccessor("Schema-Tests/mergedShapesGraph.ttl"));

    if (!validationResult.isConforming()) {
      assertTrue(validationResult.getValidationReport().contains("Property needs to have at least 1 value"));
    }
  }

  @Test
  void verifyValidationResultValid() {
    SchemaValidationResult validationResult = verificationService.verifyCredentialAgainstSchema(
            getAccessor("Validation-Tests/legalPerson_one_VC_Valid.jsonld"), getAccessor("Schema-Tests/mergedShapesGraph.ttl"));
    assertTrue(validationResult.isConforming());

  }

  @Test
  void verifyInvalidCredentialValidation_Result_Against_CompositeSchema() {
    schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));
    SchemaValidationResult result = verificationService.verifyCredentialAgainstCompositeSchema(
    		getAccessor("Validation-Tests/legalPerson_one_VC_Invalid.jsonld"));
    assertFalse(result.isConforming(), "Validation should have failed.");
    assertTrue(result.getValidationReport().contains("Property needs to have at least 1 value"));
  }

  @Test
  void verifyValidCredentialValidation_Result_Against_CompositeSchema() {
    schemaStore.addSchema(getAccessor("Schema-Tests/mergedShapesGraph.ttl"));
    SchemaValidationResult validationResult = verificationService.verifyCredentialAgainstCompositeSchema(
            getAccessor("Validation-Tests/legalPerson_one_VC_Valid.jsonld"));
    assertTrue(validationResult.isConforming());
  }

  /** With verifySchema=true, a credential that violates SHACL shapes should be rejected. */
  @Test
  void schemaValidationEnabled_InvalidCredential_Rejected() {
    verificationService.setVerifySchema(true);
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    schemaStore.addSchema(getAccessor("Validation-Tests/legal-personShape.ttl"));
    ContentAccessor content = getAccessor("VerificationService/syntax/participantCredential2.jsonld");
    Exception ex = assertThrowsExactly(VerificationException.class, ()
            // unsigned fixture, skip only signature verification
            -> verificationService.verifyCredential(content, true, true, false,false));
    assertTrue(ex.getMessage().startsWith("Schema error:"), "Expected schema validation error but got: " + ex.getMessage());
  }

  /** With verifySchema=false, a credential that violates SHACL shapes should still be accepted. */
  @Test
  void schemaValidationDisabled_InvalidCredential_Accepted() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    schemaStore.addSchema(getAccessor("Validation-Tests/legal-personShape.ttl"));
    ContentAccessor content = getAccessor("VerificationService/syntax/participantCredential2.jsonld");
    // verifySchema=false, verifyVCSigs=false: test schema-disabled acceptance, skip JWS (external GXDCH key)
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, false, false, false);
    assertNotNull(result, "Credential should be accepted when schema validation is disabled");
  }

  /** A JSON-LD asset invalid against stored SHACL shapes is accepted with the default configuration. */
  @Test
  void defaultConfig_NoAutomaticSchemaValidation() {
    // verifySchema defaults to false; verifyVCSigs=false: JWS was computed over original fixture content
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    schemaStore.addSchema(getAccessor("Validation-Tests/legal-personShape.ttl"));
    ContentAccessor content = getAccessor("VerificationService/syntax/participantCredential2.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, false, false, false);
    assertNotNull(result, "Credential should be accepted with default config (no automatic schema validation)");
  }


  @Test
  void extractClaims_protectedNamespaceFilteredTest() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantCredential-with-fcmeta.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, false, false, false);
    List<RdfClaim> actualClaims = result.getClaims();

    for (RdfClaim claim : actualClaims) {
      assertFalse(claim.getPredicateString().contains(protectedNsProps.getNamespace()),
          "Protected namespace predicate should have been filtered: " + claim);
      assertFalse(claim.getSubjectString().contains(protectedNsProps.getNamespace()),
          "Protected namespace subject should have been filtered: " + claim);
      if (claim.getObjectString().startsWith("<")) {
        assertFalse(claim.getObjectString().contains(protectedNsProps.getNamespace()),
            "Protected namespace object IRI should have been filtered: " + claim);
      }
    }

    Set<RdfClaim> expectedClaims = new HashSet<>();
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#LegalPerson>"));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#legalName>", "\"deltaDAO AG\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#registrationNumber>", "\"DEK1101R.HRB170364\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#headquarterAddress>", "_:b0"));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#street-address>", "\"Geibelstraße 46b\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#locality>", "\"Hamburg\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#Address>"));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#country>", "\"DE\""));
    expectedClaims.add(new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#postal-code>", "\"22303\""));
    expectedClaims.add(new CredentialClaim("<did:web:delta-dao.com>", "<https://w3id.org/gaia-x/2511#legalAddress>", "_:b1"));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#street-address>", "\"Geibelstraße 46b\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#Address>"));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#postal-code>", "\"22303\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#locality>", "\"Hamburg\""));
    expectedClaims.add(new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#country>", "\"DE\""));
    assertEquals(expectedClaims.size(), actualClaims.size(),
        "fcmeta:complianceResult triple should have been filtered, leaving only normal claims");
    assertEquals(expectedClaims, new HashSet<>(actualClaims));
    assertNotNull(result.getWarnings(), "Warning should be set when fcmeta triples were filtered");
    assertFalse(result.getWarnings().isEmpty(), "Warning list should not be empty when fcmeta triples were filtered");
    assertTrue(result.getWarnings().getFirst().contains("1 triple(s)"), "Warning should mention count of filtered triples");
    assertTrue(result.getWarnings().getFirst().contains(protectedNsProps.getNamespace()), "Warning should mention the protected namespace");
  }

  @Test
  void extractClaims_allFcmetaClaimsFiltered_returnsEmptyList() {
    ContentAccessor content = getAccessor("Claims-Extraction-Tests/participantCredential-only-fcmeta.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);
    List<RdfClaim> claims = result.getClaims();
    assertNotNull(claims, "Result should not be null even when all claims are filtered");
    assertTrue(claims.isEmpty(), "All fcmeta: claims should have been filtered, leaving an empty list");
    assertNotNull(result.getWarnings(), "Warning should be set when fcmeta triples were filtered");
    assertFalse(result.getWarnings().isEmpty(), "Warning list should not be empty when all claims are filtered");
  }

  // --- T5: JWT signature verification smoke tests ---

  /**
   * AC 1 / AC 3 (guard removed): JWT credential with verifyVCSignatures=true no longer throws
   * UnsupportedOperationException; JwtSignatureVerifier is invoked and returns a Validator.
   */
  @Test
  void verifyCredential_jwtVcWithVerifyVcSigsTrue_returnsValidators() {
    String vcJson = getAccessor("Claims-Tests/participantVC2.jsonld").getContentAsString();
    ContentAccessor jwtVc = new ContentAccessorDirect(fakeVcJwt(vcJson));

    Validator testValidator = new Validator("did:test:key-1", "{\"kty\":\"EC\"}", null);
    when(jwtVerifierMock.verify(any())).thenReturn(testValidator);

    CredentialVerificationResult result =
        verificationService.verifyCredential(jwtVc, false, false, false, true);

    assertNotNull(result);
    assertNotNull(result.getValidators(), "JWT VC with verifyVCSignatures=true must have validators");
    assertFalse(result.getValidators().isEmpty());
    verify(jwtVerifierMock).verify(any()); // confirms guard removed — verifier was called
  }

  /**
   * AC 9: LD credential with verifyVCSignatures=true is rejected — LD proof verification
   * was removed. JwtSignatureVerifier is NOT invoked.
   */
  @Test
  void verifyCredential_ldCredentialWithVerifyVcSigsTrue_rejectsLdProof() {
    ContentAccessor ldContent = getAccessor("VerificationService/jsonld/input.vc.jsonld");

    Exception ex = assertThrowsExactly(VerificationException.class, ()
        -> verificationService.verifyCredential(ldContent, false, false, false, true));
    assertTrue(ex.getMessage().contains("Linked Data proof verification is not supported"),
        "Should reject LD credential. Got: " + ex.getMessage());
    verify(jwtVerifierMock, never()).verify(any());
  }

  /**
   * AC 7 (VP JWT iss ≠ holder): VP JWT where iss does not match holder must throw
   * VerificationException when verifySemantics=true.
   */
  @Test
  void verifyCredential_vpJwtIssNotEqualHolder_throwsVerificationException() {
    ContentAccessor vpJwt = new ContentAccessorDirect(
        fakeVpJwt("did:web:issuer.example.com", "did:web:other.example.com"));

    Validator testValidator = new Validator("did:test:key-1", "{\"kty\":\"EC\"}", null);
    when(jwtVerifierMock.verify(any())).thenReturn(testValidator);
    // Mock the unwrap so the VP passes the preProcess step
    ContentAccessor vpJsonLd = getAccessor("VerificationService/syntax/input.vp.jsonld");
    doReturn(vpJsonLd).when(vc2ProcessorSpy).preProcess(any());

    VerificationException ex = assertThrowsExactly(VerificationException.class,
        () -> verificationService.verifyCredential(vpJwt, true, false, true, false));

    assertTrue(ex.getMessage().contains("holder"), "Error must mention holder: " + ex.getMessage());
  }

  /**
   * AC 2 (VP JWT happy path): VP JWT with iss == holder and verifyVPSignatures=true succeeds
   * and result contains validators.
   */
  @Test
  void verifyCredential_vpJwtIssEqualsHolder_returnsValidators() {
    ContentAccessor vpJwt = new ContentAccessorDirect(
        fakeVpJwt("did:web:issuer.example.com", "did:web:issuer.example.com"));

    Validator testValidator = new Validator("did:test:key-1", "{\"kty\":\"EC\"}", null);
    when(jwtVerifierMock.verify(any())).thenReturn(testValidator);
    ContentAccessor vpJsonLd = getAccessor("VerificationService/syntax/input.vp.jsonld");
    doReturn(vpJsonLd).when(vc2ProcessorSpy).preProcess(any());

    // verifySemantics=false to skip class detection; verifyVPSignatures=true for JWT verification
    CredentialVerificationResult result =
        verificationService.verifyCredential(vpJwt, false, false, true, false);

    assertNotNull(result);
    assertNotNull(result.getValidators(), "VP JWT happy path must return validators");
    assertFalse(result.getValidators().isEmpty());
  }

  /**
   * AC 3 (skip path): JWT credential with both signature flags=false must not invoke JwtSignatureVerifier.
   */
  @Test
  void verifyCredential_jwtWithBothSigFlagsFalse_jwtVerifierNotInvoked() {
    String vcJson = getAccessor("Claims-Tests/participantVC2.jsonld").getContentAsString();
    ContentAccessor jwtVc = new ContentAccessorDirect(fakeVcJwt(vcJson));

    verificationService.verifyCredential(jwtVc, false, false, false, false);

    verify(jwtVerifierMock, never()).verify(any());
  }

  /**
   * x5c rejection: a Loire credential whose DID document JWK contains an x5c chain must
   * be rejected with ClientException — full x5c chain building is not implemented.
   * Only x5u (Trust Anchor Registry URL) is supported.
   */
  @Test
  void verifyCredential_loireWithX5cInJwk_throwsClientException() {
    ContentAccessor loireJwt = new ContentAccessorDirect(TestUtil.fakeLoireJwt("did:web:example.com"));

    Validator validatorWithX5c = new Validator("did:web:example.com#key-1",
        "{\"kty\":\"EC\",\"x5c\":[\"MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCg==\"]}", null);
    when(jwtVerifierMock.verify(any())).thenReturn(validatorWithX5c);

    jdbcTemplate.update("UPDATE trust_frameworks SET enabled = true WHERE id = 'gaia-x'");
    try {
      ClientException ex = assertThrowsExactly(ClientException.class,
          () -> verificationService.verifyCredential(loireJwt, false, false, false, true));
      assertTrue(ex.getMessage().contains("x5c"), "Error must mention x5c: " + ex.getMessage());
      assertTrue(ex.getMessage().contains("x5u"), "Error must suggest x5u: " + ex.getMessage());
    } finally {
      jdbcTemplate.update("UPDATE trust_frameworks SET enabled = false WHERE id = 'gaia-x'");
    }
  }

  @Test
  void verifyCredential_noFcmetaTriples_noWarnings() {
    schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
    ContentAccessor content = getAccessor("VerificationService/syntax/serviceOffering1.jsonld");
    CredentialVerificationResult result = verificationService.verifyCredential(content, true, true, false, false);
    assertTrue(result.getWarnings().isEmpty(), "No warnings expected when upload contains no fcmeta triples");
  }

  // --- VC 2.0 tests ---

  @Test
  void extractClaims_vc2StandaloneVC_returnsOnlyCredentialSubjectTriples() {
    ContentAccessor content = getAccessor("Claims-Tests/participantVC2.jsonld");

      List<RdfClaim> claims = claimExtractionService.extractCredentialClaims(content);

    assertNotNull(claims);
    assertFalse(claims.isEmpty(), "VC 2.0 standalone VC should produce non-empty claims");
    RdfClaim expectedType = new CredentialClaim(
        "<did:web:participant.example.com>",
        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
        "<https://w3id.org/gaia-x/2511#LegalPerson>");
    RdfClaim expectedName = new CredentialClaim(
        "<did:web:participant.example.com>",
        "<https://w3id.org/gaia-x/2511#legalName>",
        "\"Example Corp\"");
    assertTrue(claims.contains(expectedType), "rdf:type triple must be present");
    assertTrue(claims.contains(expectedName), "legalName triple must be present");
    boolean noIssuerSubject = claims.stream()
        .noneMatch(c -> c.getSubjectString().contains("issuer.example.com"));
    assertTrue(noIssuerSubject, "Issuer IRI must not appear as a subject");
  }

  @Test
  void extractClaims_vc2VpWithSingleVC_returnsOnlyCredentialSubjectTriples() {
    ContentAccessor content = getAccessor("Claims-Tests/participantVP2.jsonld");

      List<RdfClaim> claims = claimExtractionService.extractCredentialClaims(content);

    assertNotNull(claims);
    assertFalse(claims.isEmpty(), "VC 2.0 VP should produce non-empty claims");
    RdfClaim expectedType = new CredentialClaim(
        "<did:web:participant.example.com>",
        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
        "<https://w3id.org/gaia-x/2511#Participant>");
    assertTrue(claims.contains(expectedType), "rdf:type triple from VP-wrapped VC must be present");
    boolean noHolderSubject = claims.stream()
        .noneMatch(c -> c.getSubjectString().contains("vp2-1"));
    assertTrue(noHolderSubject, "VP holder IRI must not appear as a subject");
  }

  @Test
  void extractClaims_vc2VpWithMultipleVCs_returnsAllCredentialSubjectTriples() {
    ContentAccessor content = getAccessor("Claims-Tests/participantVP2_multi.jsonld");

      List<RdfClaim> claims = claimExtractionService.extractCredentialClaims(content);

    assertNotNull(claims);
    Set<String> subjects = new HashSet<>();
    claims.forEach(c -> subjects.add(c.getSubjectString()));
    assertTrue(subjects.contains("<did:web:participant-a.example.com>"),
        "Claims from first VC must be present");
    assertTrue(subjects.contains("<did:web:participant-b.example.com>"),
        "Claims from second VC must be present");
  }

  @Test
  void extractClaims_vc2MultipleCredentialSubjects_returnsAllSubjectTriples() {
    ContentAccessor content = getAccessor("Claims-Tests/participantVC2_multi_cs.jsonld");

      List<RdfClaim> claims = claimExtractionService.extractCredentialClaims(content);

    assertNotNull(claims);
    Set<String> subjects = new HashSet<>();
    claims.forEach(c -> subjects.add(c.getSubjectString()));
    assertTrue(subjects.contains("<did:web:participant-a.example.com>"),
        "Claims from first credentialSubject must be present");
    assertTrue(subjects.contains("<did:web:participant-b.example.com>"),
        "Claims from second credentialSubject must be present");
  }

  @Test
  void verifyCredential_vc2WithValidFrom_passesSemanticValidation() {
    schemaStore.initializeDefaultSchemas();

    ContentAccessor content = getAccessor("Claims-Tests/participantVC2.jsonld");
    CredentialVerificationResult vr = verificationService.verifyCredential(content, true, false, false, false);

    assertNotNull(vr);
      assertInstanceOf(CredentialVerificationResultParticipant.class, vr, "VC 2.0 with 2511#LegalPerson type must be recognized");
    assertNotNull(vr.getIssuedDateTime(), "issuedDateTime must be non-null for VC 2.0 validFrom");
    assertEquals(Instant.parse("2026-01-30T00:00:00Z"), vr.getIssuedDateTime());
  }

  @Test
  void verifyCredential_vc2WithValidUntilInFuture_passesSemanticValidation() {
    schemaStore.initializeDefaultSchemas();

    String vcJson = "{"
        + "\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],"
        + "\"type\":[\"VerifiableCredential\"],"
        + "\"issuer\":\"did:web:issuer.example.com\","
        + "\"validFrom\":\"2026-01-30T00:00:00Z\","
        + "\"validUntil\":\"2099-01-01T00:00:00Z\","
        + "\"credentialSubject\":{"
        + "\"id\":\"did:web:participant.example.com\","
        + "\"@type\":[\"https://w3id.org/gaia-x/2511#Participant\"],"
        + "\"https://w3id.org/gaia-x/2511#legalName\":[{\"@value\":\"Example Corp\"}]"
        + "}}";
    ContentAccessor content = new ContentAccessorDirect(vcJson);
    CredentialVerificationResult vr = verificationService.verifyCredential(content, true, false, false, false);

    assertNotNull(vr, "VC 2.0 with future validUntil must pass semantic validation");
  }

  @Test
  void verifyCredential_vc2WithValidUntilInPast_failsSemanticValidation() {
    String vcJson = "{"
        + "\"@context\":[\"https://www.w3.org/ns/credentials/v2\"],"
        + "\"type\":[\"VerifiableCredential\"],"
        + "\"issuer\":\"did:web:issuer.example.com\","
        + "\"validFrom\":\"2020-01-01T00:00:00Z\","
        + "\"validUntil\":\"2021-01-01T00:00:00Z\","
        + "\"credentialSubject\":{"
        + "\"id\":\"did:web:participant.example.com\","
        + "\"@type\":[\"https://w3id.org/gaia-x/2511#Participant\"],"
        + "\"https://w3id.org/gaia-x/2511#legalName\":[{\"@value\":\"Example Corp\"}]"
        + "}}";
    ContentAccessor content = new ContentAccessorDirect(vcJson);

    Exception ex = assertThrowsExactly(VerificationException.class,
        () -> verificationService.verifyCredential(content, true, false, false, false));
    assertTrue(ex.getMessage().contains("validUntil") || ex.getMessage().contains("expirationDate"),
        "Error must mention expired date field, got: " + ex.getMessage());
  }

  @Test
  void verifyCredential_vc2JwtWrapped_passesAfterUnwrap() {
    String vcJson = getAccessor("Claims-Tests/participantVC2.jsonld").getContentAsString();
    ContentAccessor content = new ContentAccessorDirect(fakeVcJwt(vcJson));

    CredentialVerificationResult vr = verificationService.verifyCredential(content, false, false, false, false);

    assertNotNull(vr, "JWT-wrapped VC 2.0 must be unwrapped and processed without exception");
  }

  @Test
  void verifyCredential_invalidJwtLikeContent_throwsClientException() {
    // Content starts with "eyJ" (looks like a JWT) but cannot be parsed as VC 2.0 or VP 2.0 JWT
    String invalidJwt = "eyJub3RhcmVhbGp3dA.invalidsegment.AAAA";
    ContentAccessor content = new ContentAccessorDirect(invalidJwt);

    assertThrowsExactly(ClientException.class,
        () -> verificationService.verifyCredential(content, false, false, false, false),
        "Invalid JWT-like content must throw ClientException, not a server error");
  }

  // --- VC 2.0 non-Gaia-X RDF asset tests ---

  /**
   * VC 2.0 credential without any Gaia-X type must pass semantic verification
   * when gaiaxTrustFrameworkEnabled=false (default). Gating hasClasses() on the
   * Gaia-X flag allows generic VC 2.0 assets to be accepted as RDF claims.
   */
  @Test
  void verifyCredential_vc2NonGaiaxRdfAsset_passesWithSemanticsEnabled() {
    ContentAccessor content = getAccessor("Claims-Tests/vc2NonGaiax.jsonld");

    CredentialVerificationResult vr = verificationService.verifyCredential(content, true, false, false, false);

    assertNotNull(vr, "VC 2.0 without Gaia-X type must pass when gaiax is disabled");
    assertEquals("did:web:subject.example.com", vr.getId());
    assertEquals("did:web:issuer.example.com", vr.getIssuer());
  }

  /**
   * VC 2.0 credential without Gaia-X type must be rejected when
   * gaiaxTrustFrameworkEnabled=true — Gaia-X deployments require a recognized class.
   */
  @Test
  void verifyCredential_vc2NonGaiaxRdfAsset_gaiaxEnabled_throwsNoProperSubjectError() {
    jdbcTemplate.update("UPDATE trust_frameworks SET enabled = true WHERE id = 'gaia-x'");
    try {
      schemaStore.addSchema(getAccessor("Schema-Tests/gx-2511-test-ontology.ttl"));
      ContentAccessor content = getAccessor("Claims-Tests/vc2NonGaiax.jsonld");

      Exception ex = assertThrowsExactly(VerificationException.class,
          () -> verificationService.verifyCredential(content, true, false, false, false));
      assertEquals("Semantic Error: no proper CredentialSubject found", ex.getMessage());
    } finally {
      jdbcTemplate.update("UPDATE trust_frameworks SET enabled = false WHERE id = 'gaia-x'");
    }
  }

  // --- EnvelopedVerifiableCredential / EnvelopedVerifiablePresentation ---

  @Test
  void verifyCredential_evcWrapper_innerVcJwtUnwrappedAndProcessed() {
    // EVC per ICAM 24.07: outer JSON-LD wrapper with data: URI carrying a Loire VC JWT.
    // The wrapper is stripped and the inner JWT is routed through the Loire (GAIAX_V2_LOIRE) path.
    String innerJwt = TestUtil.fakeLoireJwt("did:example:issuer");
    String evcBody = "{\"@context\":\"https://www.w3.org/ns/credentials/v2\","
        + "\"type\":\"EnvelopedVerifiableCredential\","
        + "\"id\":\"data:application/vc+ld+json+jwt," + innerJwt + "\"}";
    ContentAccessor content = new ContentAccessorDirect(evcBody, "application/vc+ld+json");

    CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

    assertNotNull(result, "EVC wrapper must be unwrapped and inner Loire VC processed");
    verify(loireJwtParserSpy).unwrap(any());
  }

  @Test
  void verifyCredential_evpWrapper_innerVpJwtUnwrappedAndProcessed() {
    // EVP per ICAM 24.07: outer JSON-LD wrapper with data: URI carrying a Loire VP JWT.
    // The wrapper is stripped and the inner JWT is routed through the Loire (GAIAX_V2_LOIRE) path.
    String innerJwt = fakeLoireVpJwt("did:example:issuer");
    String evpBody = "{\"@context\":\"https://www.w3.org/ns/credentials/v2\","
        + "\"type\":\"EnvelopedVerifiablePresentation\","
        + "\"id\":\"data:application/vp+ld+jwt," + innerJwt + "\"}";
    ContentAccessor content = new ContentAccessorDirect(evpBody, "application/vp+ld+json");

    verificationService.verifyCredential(content, false, false, false, false);

    verify(loireJwtParserSpy).unwrap(any());
  }

  @Test
  void verifyCredential_evcWrapper_missingId_throwsClientException() {
    String evcBody = "{\"@context\":\"https://www.w3.org/ns/credentials/v2\","
        + "\"type\":\"EnvelopedVerifiableCredential\"}";
    ContentAccessor content = new ContentAccessorDirect(evcBody, "application/vc+ld+json");

    ClientException ex = assertThrowsExactly(ClientException.class,
        () -> verificationService.verifyCredential(content, false, false, false, false));

    assertTrue(ex.getMessage().contains("EnvelopedVerifiableCredential"), ex.getMessage());
  }

  @Test
  void verifyCredential_evcWrapper_malformedDataUri_throwsClientException() {
    String evcBody = "{\"@context\":\"https://www.w3.org/ns/credentials/v2\","
        + "\"type\":\"EnvelopedVerifiableCredential\","
        + "\"id\":\"not-a-data-uri\"}";
    ContentAccessor content = new ContentAccessorDirect(evcBody, "application/vc+ld+json");

    ClientException ex = assertThrowsExactly(ClientException.class,
        () -> verificationService.verifyCredential(content, false, false, false, false));

    assertTrue(ex.getMessage().contains("EnvelopedVerifiableCredential"), ex.getMessage());
  }

    // --- Non-credential RDF claims extraction ---

    @Test
    void verifyCredential_nonCredentialJsonLd_returnsAllTriples() {
        ContentAccessor content = getAccessor("Claims-Tests/simple-jsonld.jsonld");

        CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

        assertNotNull(result);
        assertNotNull(result.getClaims());
        assertEquals(3, result.getClaims().size(), "All 3 triples must be extracted from non-credential JSON-LD");
        assertTrue(result.getClaims().stream()
                        .anyMatch(c -> c.getSubjectString().equals("<http://example.org/item1>")),
                "Subject IRI must appear in extracted claims");
    }

    @Test
    void verifyCredential_turtleFormat_returnsAllTriples() {
        ContentAccessor content = getAccessor("Claims-Tests/simple.ttl");

        CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

        assertNotNull(result);
        assertNotNull(result.getClaims());
        assertEquals(3, result.getClaims().size(), "All 3 triples must be extracted from Turtle");
        assertTrue(result.getClaims().stream()
                        .anyMatch(c -> c.getSubjectString().equals("<http://example.org/item1>")),
                "Subject IRI must appear in extracted claims");
    }

    @Test
    void verifyCredential_nTriplesFormat_returnsAllTriples() {
        ContentAccessor content = getAccessor("Claims-Tests/simple.nt");

        CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

        assertNotNull(result);
        assertNotNull(result.getClaims());
        assertEquals(3, result.getClaims().size(), "All 3 triples must be extracted from N-Triples");
        assertTrue(result.getClaims().stream()
                        .anyMatch(c -> c.getSubjectString().equals("<http://example.org/item1>")),
                "Subject IRI must appear in extracted claims");
    }

    @Test
    void verifyCredential_rdfXmlFormat_returnsAllTriples() {
        ContentAccessor content = getAccessor("Claims-Tests/simple.rdf");

        CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

        assertNotNull(result);
        assertNotNull(result.getClaims());
        assertEquals(3, result.getClaims().size(), "All 3 triples must be extracted from RDF/XML");
        assertTrue(result.getClaims().stream()
                        .anyMatch(c -> c.getSubjectString().equals("<http://example.org/item1>")),
                "Subject IRI must appear in extracted claims");
    }

    @Test
    void verifyCredential_nonCredentialResult_claimsAreRdfClaimNotCredentialClaim() {
        ContentAccessor content = getAccessor("Claims-Tests/simple-jsonld.jsonld");

        CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

        assertNotNull(result);
        assertFalse(result.getClaims().isEmpty());
        assertInstanceOf(RdfClaim.class, result.getClaims().get(0));
        assertFalse(result.getClaims().get(0) instanceof CredentialClaim,
                "Non-credential extractor must return RdfClaim, not CredentialClaim");
    }

    @Test
    void verifyCredential_credentialContent_stillUsesVcExtractors() {
        // Regression: VC 2.0 credential path unchanged — only credentialSubject triples returned
        ContentAccessor content = getAccessor("Claims-Tests/participantVC2.jsonld");

        CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

        assertNotNull(result);
        assertNotNull(result.getClaims(), "VC credential must return non-null claims");
        assertFalse(result.getClaims().isEmpty(), "VC credential must return non-empty claims");
        boolean hasIssuerSubject = result.getClaims().stream()
                .anyMatch(c -> c.getSubjectString().contains("issuer.example.com"));
        assertFalse(hasIssuerSubject, "Issuer IRI must not appear as credentialSubject — only subject triples");
    }

    @Test
    void verifyCredential_credentialContent_claimsAreCredentialClaim() {
        // Regression: VC 2.0 credential extractors still return CredentialClaim instances
        ContentAccessor content = getAccessor("Claims-Tests/participantVC2.jsonld");

        CredentialVerificationResult result = verificationService.verifyCredential(content, false, false, false, false);

        assertNotNull(result);
        assertFalse(result.getClaims().isEmpty());
        assertInstanceOf(CredentialClaim.class, result.getClaims().get(0),
                "VC credential extractor must return CredentialClaim instances");
    }


  /** Builds a fake danubetech-style JWT wrapping the given VC JSON under a {@code vc} claim. */
  private static String fakeVcJwt(String vcJson) {
    var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString(
        "{\"alg\":\"RS256\"}".getBytes(StandardCharsets.UTF_8));
    String payload = encoder.encodeToString(
        ("{\"vc\":" + vcJson + "}").getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".AAAA";
  }

  /**
   * Builds a fake Loire VC JWT with a specific credentialSubject type.
   * Used for type-resolution tests (e.g. LegalPerson → PARTICIPANT).
   */
  private static String fakeLoireVcJwtWithType(String iss, String subjectType) {
    var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString(
        "{\"alg\":\"RS256\",\"typ\":\"vc+jwt\",\"cty\":\"vc\"}".getBytes(StandardCharsets.UTF_8));
    String payloadJson = """
        {"iss":"%s","@context":["https://www.w3.org/ns/credentials/v2"],\
        "type":["VerifiableCredential"],\
        "issuer":"%s",\
        "validFrom":"2024-01-01T00:00:00Z",\
        "credentialSubject":{"@id":"did:web:subject.example.com","@type":["%s"]}}"""
        .formatted(iss, iss, subjectType);
    String payload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".AAAA";
  }

  /**
   * Builds a fake Loire VP JWT containing the given inline VC JSON objects.
   * The inner VCs are plain JSON-LD entries in the {@code verifiableCredential} array.
   */
  private static String fakeLoireVpJwtWithVc(String iss, String... vcJsons) {
    var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString(
        "{\"alg\":\"RS256\",\"typ\":\"vp+ld+jwt\",\"cty\":\"vp\"}".getBytes(StandardCharsets.UTF_8));
    String vcs = String.join(",", vcJsons);
    String payloadJson = """
        {"iss":"%s","@context":["https://www.w3.org/ns/credentials/v2"],\
        "type":["VerifiablePresentation"],\
        "holder":"%s",\
        "verifiableCredential":[%s]}""".formatted(iss, iss, vcs);
    String payload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".AAAA";
  }

  /** Builds a fake Loire VP JWT (typ=vp+ld+jwt, top-level fields, no vp wrapper). */
  private static String fakeLoireVpJwt(String iss) {
    var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString(
        "{\"alg\":\"RS256\",\"typ\":\"vp+ld+jwt\",\"cty\":\"vp\"}".getBytes(StandardCharsets.UTF_8));
    String payloadJson = """
        {"iss":"%s","@context":["https://www.w3.org/ns/credentials/v2"],\
        "type":["VerifiablePresentation"],\
        "holder":"%s"}""".formatted(iss, iss);
    String payload = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + ".AAAA";
  }

  /** Builds a fake danubetech-style VP JWT with the given iss and holder. */
  private static String fakeVpJwt(String iss, String holder) {
    var encoder = java.util.Base64.getUrlEncoder().withoutPadding();
    String header = encoder.encodeToString(
        "{\"alg\":\"EdDSA\"}".getBytes(StandardCharsets.UTF_8));
    String payloadJson = """
        {"iss":"%s","holder":"%s",\
        "vp":{"@context":["https://www.w3.org/ns/credentials/v2"],\
        "type":["VerifiablePresentation"]}}""".formatted(iss, holder);
    String payload = encoder.encodeToString(
        payloadJson.getBytes(StandardCharsets.UTF_8));
    String sig = encoder.encodeToString(
        "fakesig".getBytes(StandardCharsets.UTF_8));
    return header + "." + payload + "." + sig;
  }

}
