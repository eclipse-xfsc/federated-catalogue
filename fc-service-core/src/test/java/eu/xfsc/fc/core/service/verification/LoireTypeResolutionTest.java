package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.apache.jena.riot.system.stream.StreamManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.ResolvedRole;
import eu.xfsc.fc.core.service.trustframework.RoleConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.ValidationType;
import eu.xfsc.fc.core.util.ClaimValidator;

/**
 * Pure unit tests for Loire (Gaia-X 2511) type resolution via
 * {@link ClaimValidator#resolveSubjectRole}.
 *
 * <p>Calls {@code ClaimValidator.resolveSubjectRole} directly with the minimal 2511 test ontology,
 * using inline JSON-LD context to avoid network calls during Jena JSONLD11 parsing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoireTypeResolutionTest {

  private static final String PROFILE_ID = "gaia-x-2511";
  private static final String NAMESPACE = "https://w3id.org/gaia-x/2511#";

  private TrustFrameworkRegistry loireRegistry;
  private TrustFrameworkRegistry legacyRegistry;
  private StreamManager streamManager;

  @BeforeAll
  void setUp() throws IOException {
    String ttlPath = "Schema-Tests/gx-2511-test-ontology.ttl";
    ContentAccessorDirect gx2511Ontology;
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(ttlPath)) {
      if (is == null) {
        throw new IllegalStateException("Test resource not found: " + ttlPath);
      }
      gx2511Ontology = new ContentAccessorDirect(new String(is.readAllBytes(), StandardCharsets.UTF_8));
    }
    streamManager = StreamManager.get().clone();

    // 2511 roles — mirrors the production gaia-x-2511 bundle
    Map<String, RoleConfig> loireRoles = Map.of(
        "Participant", new RoleConfig(List.of(), List.of()),
        "ServiceOffering", new RoleConfig(
            List.of(NAMESPACE + "DigitalServiceOffering"), List.of()),
        "Resource", new RoleConfig(List.of(), List.of())
    );
    FrameworkBundleConfig loireConfig = new FrameworkBundleConfig(
        PROFILE_ID, "gaia-x", NAMESPACE, ValidationType.SHACL, loireRoles, Map.of());
    loireRegistry = new TrustFrameworkRegistry(
        List.of(new TrustFrameworkBundle(loireConfig, gx2511Ontology, null)));

    // Legacy gax-core roles — intentionally different namespace
    Map<String, RoleConfig> legacyRoles = Map.of(
        "Participant", new RoleConfig(List.of(), List.of()),
        "ServiceOffering", new RoleConfig(List.of(), List.of()),
        "Resource", new RoleConfig(List.of(), List.of())
    );
    FrameworkBundleConfig legacyConfig = new FrameworkBundleConfig(
        "gaia-x-legacy", "gaia-x", "https://w3id.org/gaia-x/core#",
        ValidationType.SHACL, legacyRoles, Map.of());
    legacyRegistry = new TrustFrameworkRegistry(
        List.of(new TrustFrameworkBundle(legacyConfig, gx2511Ontology, null)));
  }

  // ==================== 2511 type resolution (Loire path) ====================

  @Test
  @DisplayName("gx:LegalPerson resolves to Participant via 2511 ontology")
  void resolveSubjectRole_legalPerson_returnsParticipant() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#LegalPerson");

    ResolvedRole result =
        ClaimValidator.resolveSubjectRole(streamManager, credential, loireRegistry, null);

    assertEquals(new ResolvedRole(PROFILE_ID, "Participant"), result,
        "gx:LegalPerson → gx:Participant → gx:GaiaXEntity; should resolve to Participant");
  }

  @Test
  @DisplayName("gx:DigitalServiceOffering (GaiaXEntity sibling) resolves to ServiceOffering via explicit root")
  void resolveSubjectRole_digitalServiceOffering_returnsServiceOffering() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#DigitalServiceOffering");

    ResolvedRole result =
        ClaimValidator.resolveSubjectRole(streamManager, credential, loireRegistry, null);

    assertEquals(new ResolvedRole(PROFILE_ID, "ServiceOffering"), result,
        "gx:DigitalServiceOffering is a sibling of gx:ServiceOffering; resolves via additionalRoots");
  }

  @Test
  @DisplayName("gx:DataProduct (subtype of DigitalServiceOffering) resolves to ServiceOffering")
  void resolveSubjectRole_dataProduct_returnsServiceOffering() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#DataProduct");

    ResolvedRole result =
        ClaimValidator.resolveSubjectRole(streamManager, credential, loireRegistry, null);

    assertEquals(new ResolvedRole(PROFILE_ID, "ServiceOffering"), result,
        "gx:DataProduct rdfs:subClassOf gx:DigitalServiceOffering; traversal from DSO root reaches DataProduct");
  }

  @Test
  @DisplayName("gx:VirtualResource (subtype) resolves to Resource via 2511 ontology")
  void resolveSubjectRole_virtualResource_returnsResource() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#VirtualResource");

    ResolvedRole result =
        ClaimValidator.resolveSubjectRole(streamManager, credential, loireRegistry, null);

    assertEquals(new ResolvedRole(PROFILE_ID, "Resource"), result,
        "gx:VirtualResource rdfs:subClassOf gx:Resource; should resolve to Resource");
  }

  @Test
  @DisplayName("Unknown type returns UNKNOWN (not in 2511 hierarchy)")
  void resolveSubjectRole_unknownType_returnsUnknown() {
    String credential = buildCredential("https://example.com/SomeOtherType");

    ResolvedRole result =
        ClaimValidator.resolveSubjectRole(streamManager, credential, loireRegistry, null);

    assertFalse(result.isResolved(), "Type not in the 2511 hierarchy should return UNKNOWN");
  }

  // ==================== Namespace isolation ====================

  @Test
  @DisplayName("2511 type returns UNKNOWN when legacy (gax-core) registry is used — hierarchies disconnected")
  void resolveSubjectRole_loireType_withLegacyRegistry_returnsUnknown() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#LegalPerson");

    ResolvedRole result =
        ClaimValidator.resolveSubjectRole(streamManager, credential, legacyRegistry, null);

    assertFalse(result.isResolved(),
        "gx:2511#LegalPerson is not a subclass of gax-core:Participant; "
            + "disconnected hierarchies must not cross-resolve");
  }

  // ==================== Composite ontology slow path ====================

  private static final String COMPOSITE_ONTOLOGY_TTL = """
      @prefix gx: <https://w3id.org/gaia-x/2511#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      @prefix ex: <https://example.com/> .
      
      ex:CustomParticipant rdfs:subClassOf gx:Participant .
      """;

  @Test
  @DisplayName("Custom subclass not in boot index resolves to Participant via composite ontology")
  void resolveSubjectRole_customSubclass_withCompositeOntology_returnsParticipant() {
    TrustFrameworkRegistry rootsOnlyRegistry = buildRootsOnlyRegistry();
    ContentAccessorDirect compositeOntology = new ContentAccessorDirect(COMPOSITE_ONTOLOGY_TTL);
    String credential = buildCredential("https://example.com/CustomParticipant");

    ResolvedRole result =
        ClaimValidator.resolveSubjectRole(streamManager, credential, rootsOnlyRegistry, compositeOntology);

    assertEquals(new ResolvedRole(PROFILE_ID, "Participant"), result,
        "Custom subclass absent from boot index should resolve via composite ontology");
  }

  @Test
  @DisplayName("Custom subclass returns UNKNOWN when composite ontology is absent")
  void resolveSubjectRole_customSubclass_withoutCompositeOntology_returnsUnknown() {
    TrustFrameworkRegistry rootsOnlyRegistry = buildRootsOnlyRegistry();
    String credential = buildCredential("https://example.com/CustomParticipant");

    ResolvedRole result =
        ClaimValidator.resolveSubjectRole(streamManager, credential, rootsOnlyRegistry, null);

    assertFalse(result.isResolved(),
        "Custom subclass absent from boot index with no ontology fallback should return UNKNOWN");
  }

  // ==================== Helpers ====================

  private TrustFrameworkRegistry buildRootsOnlyRegistry() {
    Map<String, RoleConfig> roles = Map.of(
        "Participant", new RoleConfig(List.of(), List.of()),
        "ServiceOffering", new RoleConfig(
            List.of(NAMESPACE + "DigitalServiceOffering"), List.of()),
        "Resource", new RoleConfig(List.of(), List.of())
    );
    FrameworkBundleConfig config = new FrameworkBundleConfig(
        PROFILE_ID, "gaia-x", NAMESPACE, ValidationType.SHACL, roles, Map.of());
    return new TrustFrameworkRegistry(
        List.of(new TrustFrameworkBundle(config, null, null)));
  }

  private static String buildCredential(String subjectTypeUri) {
    return """
        {
          "@context": {
            "cred": "https://www.w3.org/2018/credentials#",
            "credentialSubject": {"@id": "cred:credentialSubject", "@type": "@id"}
          },
          "credentialSubject": {
            "@type": ["%s"],
            "@id": "did:web:subject.example.com"
          }
        }
        """.formatted(subjectTypeUri);
  }
}
