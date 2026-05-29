package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_PARTICIPANT;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_DIGITAL_SERVICE_OFFERING;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_SERVICE_OFFERING;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_RESOURCE;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_FAMILY_GAIA_X;

import org.apache.jena.riot.system.stream.StreamManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.ResolvedBaseClass;
import eu.xfsc.fc.core.service.trustframework.BaseClassConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.ValidationType;
import eu.xfsc.fc.core.util.ClaimValidator;

/**
 * Pure unit tests for Loire (Gaia-X 2511) type resolution via
 * {@link ClaimValidator#resolveSubjectBaseClass}.
 *
 * <p>Calls {@code ClaimValidator.resolveSubjectBaseClass} directly with the minimal 2511 test ontology,
 * using inline JSON-LD context to avoid network calls during Jena JSONLD11 parsing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoireTypeResolutionTest {

  /** All roles enabled — passed to {@link eu.xfsc.fc.core.util.ClaimValidator#resolveSubjectBaseClass}
   *  so existing tests are unaffected by the new mandatory predicate parameter. */
  private static final BiPredicate<String, String> ALL_ENABLED = (b, r) -> true;

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
    Map<String, BaseClassConfig> loireRoles = Map.of(
        TFW_BASE_CLASS_PARTICIPANT, new BaseClassConfig(List.of(), List.of()),
        TFW_BASE_CLASS_SERVICE_OFFERING, new BaseClassConfig(
            List.of(NAMESPACE + TFW_BASE_CLASS_DIGITAL_SERVICE_OFFERING), List.of()),
        TFW_BASE_CLASS_RESOURCE, new BaseClassConfig(List.of(), List.of())
    );
    FrameworkBundleConfig loireConfig = new FrameworkBundleConfig(
        PROFILE_ID, TFW_FAMILY_GAIA_X, NAMESPACE, ValidationType.SHACL, loireRoles, Map.of());
    loireRegistry = new TrustFrameworkRegistry(
        List.of(new TrustFrameworkBundle(loireConfig, gx2511Ontology, null)));

    // Legacy gax-core roles — intentionally different namespace
    Map<String, BaseClassConfig> legacyRoles = Map.of(
        TFW_BASE_CLASS_PARTICIPANT, new BaseClassConfig(List.of(), List.of()),
        TFW_BASE_CLASS_SERVICE_OFFERING, new BaseClassConfig(List.of(), List.of()),
        TFW_BASE_CLASS_RESOURCE, new BaseClassConfig(List.of(), List.of())
    );
    FrameworkBundleConfig legacyConfig = new FrameworkBundleConfig(
        "gaia-x-legacy", TFW_FAMILY_GAIA_X, "https://w3id.org/gaia-x/core#",
        ValidationType.SHACL, legacyRoles, Map.of());
    legacyRegistry = new TrustFrameworkRegistry(
        List.of(new TrustFrameworkBundle(legacyConfig, gx2511Ontology, null)));
  }

  @Test
  @DisplayName("gx:LegalPerson resolves to Participant via 2511 ontology")
  void resolveSubjectBaseClass_legalPerson_returnsParticipant() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#LegalPerson");

    ResolvedBaseClass result =
        ClaimValidator.resolveSubjectBaseClass(streamManager, credential, loireRegistry, null, ALL_ENABLED);

    assertEquals(new ResolvedBaseClass(PROFILE_ID, TFW_BASE_CLASS_PARTICIPANT), result,
        "gx:LegalPerson → gx:Participant → gx:GaiaXEntity; should resolve to Participant");
  }

  @Test
  @DisplayName("gx:DigitalServiceOffering (GaiaXEntity sibling) resolves to ServiceOffering via explicit root")
  void resolveSubjectBaseClass_digitalServiceOffering_returnsServiceOffering() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#DigitalServiceOffering");

    ResolvedBaseClass result =
        ClaimValidator.resolveSubjectBaseClass(streamManager, credential, loireRegistry, null, ALL_ENABLED);

    assertEquals(new ResolvedBaseClass(PROFILE_ID, TFW_BASE_CLASS_SERVICE_OFFERING), result,
        "gx:DigitalServiceOffering is a sibling of gx:ServiceOffering; resolves via additionalRoots");
  }

  @Test
  @DisplayName("gx:DataProduct (subtype of DigitalServiceOffering) resolves to ServiceOffering")
  void resolveSubjectBaseClass_dataProduct_returnsServiceOffering() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#DataProduct");

    ResolvedBaseClass result =
        ClaimValidator.resolveSubjectBaseClass(streamManager, credential, loireRegistry, null, ALL_ENABLED);

    assertEquals(new ResolvedBaseClass(PROFILE_ID, TFW_BASE_CLASS_SERVICE_OFFERING), result,
        "gx:DataProduct rdfs:subClassOf gx:DigitalServiceOffering; traversal from DSO root reaches DataProduct");
  }

  @Test
  @DisplayName("gx:VirtualResource (subtype) resolves to Resource via 2511 ontology")
  void resolveSubjectBaseClass_virtualResource_returnsResource() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#VirtualResource");

    ResolvedBaseClass result =
        ClaimValidator.resolveSubjectBaseClass(streamManager, credential, loireRegistry, null, ALL_ENABLED);

    assertEquals(new ResolvedBaseClass(PROFILE_ID, TFW_BASE_CLASS_RESOURCE), result,
        "gx:VirtualResource rdfs:subClassOf gx:Resource; should resolve to Resource");
  }

  @Test
  @DisplayName("Unknown type returns UNKNOWN (not in 2511 hierarchy)")
  void resolveSubjectBaseClass_unknownType_returnsUnknown() {
    String credential = buildCredential("https://example.com/SomeOtherType");

    ResolvedBaseClass result =
        ClaimValidator.resolveSubjectBaseClass(streamManager, credential, loireRegistry, null, ALL_ENABLED);

    assertFalse(result.isResolved(), "Type not in the 2511 hierarchy should return UNKNOWN");
  }

  @Test
  @DisplayName("2511 type returns UNKNOWN when legacy (gax-core) registry is used — hierarchies disconnected")
  void resolveSubjectBaseClass_loireType_withLegacyRegistry_returnsUnknown() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#LegalPerson");

    ResolvedBaseClass result =
        ClaimValidator.resolveSubjectBaseClass(streamManager, credential, legacyRegistry, null, ALL_ENABLED);

    assertFalse(result.isResolved(),
        "gx:2511#LegalPerson is not a subclass of gax-core:Participant; "
            + "disconnected hierarchies must not cross-resolve");
  }

  private static final String COMPOSITE_ONTOLOGY_TTL = """
      @prefix gx: <https://w3id.org/gaia-x/2511#> .
      @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
      @prefix ex: <https://example.com/> .
      
      ex:CustomParticipant rdfs:subClassOf gx:Participant .
      """;

  @Test
  @DisplayName("Custom subclass not in boot index resolves to Participant via composite ontology")
  void resolveSubjectBaseClass_customSubclass_withCompositeOntology_returnsParticipant() {
    TrustFrameworkRegistry rootsOnlyRegistry = buildRootsOnlyRegistry();
    ContentAccessorDirect compositeOntology = new ContentAccessorDirect(COMPOSITE_ONTOLOGY_TTL);
    String credential = buildCredential("https://example.com/CustomParticipant");

    ResolvedBaseClass result =
        ClaimValidator.resolveSubjectBaseClass(streamManager, credential, rootsOnlyRegistry, compositeOntology, ALL_ENABLED);

    assertEquals(new ResolvedBaseClass(PROFILE_ID, TFW_BASE_CLASS_PARTICIPANT), result,
        "Custom subclass absent from boot index should resolve via composite ontology");
  }

  @Test
  @DisplayName("Custom subclass returns UNKNOWN when composite ontology is absent")
  void resolveSubjectBaseClass_customSubclass_withoutCompositeOntology_returnsUnknown() {
    TrustFrameworkRegistry rootsOnlyRegistry = buildRootsOnlyRegistry();
    String credential = buildCredential("https://example.com/CustomParticipant");

    ResolvedBaseClass result =
        ClaimValidator.resolveSubjectBaseClass(streamManager, credential, rootsOnlyRegistry, null, ALL_ENABLED);

    assertFalse(result.isResolved(),
        "Custom subclass absent from boot index with no ontology fallback should return UNKNOWN");
  }


  private TrustFrameworkRegistry buildRootsOnlyRegistry() {
    Map<String, BaseClassConfig> roles = Map.of(
        TFW_BASE_CLASS_PARTICIPANT, new BaseClassConfig(List.of(), List.of()),
        TFW_BASE_CLASS_SERVICE_OFFERING, new BaseClassConfig(
            List.of(NAMESPACE + TFW_BASE_CLASS_DIGITAL_SERVICE_OFFERING), List.of()),
        TFW_BASE_CLASS_RESOURCE, new BaseClassConfig(List.of(), List.of())
    );
    FrameworkBundleConfig config = new FrameworkBundleConfig(
        PROFILE_ID, TFW_FAMILY_GAIA_X, NAMESPACE, ValidationType.SHACL, roles, Map.of());
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
