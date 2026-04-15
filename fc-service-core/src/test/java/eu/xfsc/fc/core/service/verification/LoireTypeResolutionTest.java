package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.PARTICIPANT;
import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.RESOURCE;
import static eu.xfsc.fc.core.service.verification.TrustFrameworkBaseClass.SERVICE_OFFERING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.apache.jena.riot.system.stream.StreamManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.util.ClaimValidator;

/**
 * Pure unit tests for Loire (Gaia-X 2511) type resolution via
 * {@link ClaimValidator#getSubjectType}.
 *
 * <p>Calls {@code ClaimValidator.getSubjectType} directly with the minimal 2511 test ontology,
 * using inline JSON-LD context to avoid network calls during Jena JSONLD11 parsing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoireTypeResolutionTest {

  /** 2511 namespace type URIs — matches Loire (GAIAX_V2_LOIRE) processing path. */
  private static final Map<TrustFrameworkBaseClass, List<String>> LOIRE_CLASS_URIS = new EnumMap<>(Map.of(
      PARTICIPANT, List.of("https://w3id.org/gaia-x/2511#Participant"),
      RESOURCE, List.of("https://w3id.org/gaia-x/2511#Resource"),
      SERVICE_OFFERING, List.of(
          "https://w3id.org/gaia-x/2511#ServiceOffering",
          "https://w3id.org/gaia-x/2511#DigitalServiceOffering")
  ));

  /** Legacy gax-core type URIs — intentionally different namespace to test hierarchy disconnection. */
  private static final Map<TrustFrameworkBaseClass, List<String>> LEGACY_CLASS_URIS = new EnumMap<>(Map.of(
      PARTICIPANT, List.of("https://w3id.org/gaia-x/core#Participant"),
      RESOURCE, List.of("https://w3id.org/gaia-x/core#Resource"),
      SERVICE_OFFERING, List.of("https://w3id.org/gaia-x/core#ServiceOffering")
  ));

  private ContentAccessorDirect gx2511Ontology;
  private StreamManager streamManager;

  @BeforeAll
  void setUp() throws IOException {
    String ttlPath = "Schema-Tests/gx-2511-test-ontology.ttl";
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(ttlPath)) {
      if (is == null) {
        throw new IllegalStateException("Test resource not found: " + ttlPath);
      }
      gx2511Ontology = new ContentAccessorDirect(new String(is.readAllBytes(), StandardCharsets.UTF_8));
    }
    streamManager = StreamManager.get().clone();
  }

  // ==================== 2511 type resolution (Loire path) ====================

  @Test
  @DisplayName("gx:LegalPerson resolves to PARTICIPANT via 2511 ontology + 2511 URIs")
  void getSubjectType_legalPerson_returnsParticipant() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#LegalPerson");

    TrustFrameworkBaseClass result =
        ClaimValidator.getSubjectType(gx2511Ontology, streamManager, credential, LOIRE_CLASS_URIS);

    assertEquals(PARTICIPANT, result,
        "gx:LegalPerson → gx:Participant → gx:GaiaXEntity; should resolve to PARTICIPANT");
  }

  @Test
  @DisplayName("gx:DigitalServiceOffering (GaiaXEntity sibling) resolves to SERVICE_OFFERING via explicit root")
  void getSubjectType_digitalServiceOffering_returnsServiceOffering() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#DigitalServiceOffering");

    TrustFrameworkBaseClass result =
        ClaimValidator.getSubjectType(gx2511Ontology, streamManager, credential, LOIRE_CLASS_URIS);

    assertEquals(SERVICE_OFFERING, result,
        "gx:DigitalServiceOffering is a sibling of gx:ServiceOffering; resolves via explicit DigitalServiceOffering root");
  }

  @Test
  @DisplayName("gx:DataProduct (subtype of DigitalServiceOffering) resolves to SERVICE_OFFERING")
  void getSubjectType_dataProduct_returnsServiceOffering() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#DataProduct");

    TrustFrameworkBaseClass result =
        ClaimValidator.getSubjectType(gx2511Ontology, streamManager, credential, LOIRE_CLASS_URIS);

    assertEquals(SERVICE_OFFERING, result,
        "gx:DataProduct rdfs:subClassOf gx:DigitalServiceOffering; traversal from DSO root reaches DataProduct");
  }

  @Test
  @DisplayName("gx:VirtualResource (subtype) resolves to RESOURCE via 2511 ontology")
  void getSubjectType_virtualResource_returnsResource() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#VirtualResource");

    TrustFrameworkBaseClass result =
        ClaimValidator.getSubjectType(gx2511Ontology, streamManager, credential, LOIRE_CLASS_URIS);

    assertEquals(RESOURCE, result,
        "gx:VirtualResource rdfs:subClassOf gx:Resource; should resolve to RESOURCE");
  }

  @Test
  @DisplayName("Unknown type returns null (not in 2511 hierarchy)")
  void getSubjectType_unknownType_returnsNull() {
    String credential = buildCredential("https://example.com/SomeOtherType");

    TrustFrameworkBaseClass result =
        ClaimValidator.getSubjectType(gx2511Ontology, streamManager, credential, LOIRE_CLASS_URIS);

    assertNull(result, "Type not in the 2511 hierarchy should return null");
  }

  // ==================== Namespace isolation ====================

  @Test
  @DisplayName("2511 type returns null when legacy (gax-core) URIs are used — hierarchies disconnected")
  void getSubjectType_loireType_withLegacyUris_returnsNull() {
    String credential = buildCredential("https://w3id.org/gaia-x/2511#LegalPerson");

    TrustFrameworkBaseClass result =
        ClaimValidator.getSubjectType(gx2511Ontology, streamManager, credential, LEGACY_CLASS_URIS);

    assertNull(result,
        "gx:2511#LegalPerson is not a subclass of gax-core:Participant; "
            + "disconnected hierarchies must not cross-resolve");
  }

  // ==================== Helpers ====================

  /**
   * Builds a minimal JSON-LD credential string with the given credential subject type URI.
   * Uses an inline context to avoid network calls during Jena JSONLD11 parsing.
   */
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
