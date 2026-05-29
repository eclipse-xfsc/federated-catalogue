package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_FAMILY_GAIA_X;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_DIGITAL_SERVICE_OFFERING;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_PARTICIPANT;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_RESOURCE;
import static eu.xfsc.fc.core.service.trustframework.TestTrustFrameworkConstants.TFW_BASE_CLASS_SERVICE_OFFERING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.apache.jena.riot.system.stream.StreamManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.trustframework.FrameworkBundleConfig;
import eu.xfsc.fc.core.service.trustframework.ResolvedBaseClass;
import eu.xfsc.fc.core.service.trustframework.BaseClassConfig;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundle;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;
import eu.xfsc.fc.core.service.trustframework.ValidationType;
import eu.xfsc.fc.core.util.ClaimValidator;

/**
 * Tests for role-toggle enforcement in {@link ClaimValidator#resolveSubjectBaseClass}.
 *
 * <p>Verifies that a disabled role (predicate returns false) causes a {@link ClientException}
 * on both direct registry hits and ontology-subclass-walk hits, while enabled roles resolve
 * normally (including the default-true predicate used for backward compatibility).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BaseClassToggleResolutionTest {

  private static final String PROFILE_ID = "gaia-x-2511";
  private static final String NAMESPACE   = "https://w3id.org/gaia-x/2511#";

  /** Predicate that disables every role — used for disabled-role tests. */
  private static final BiPredicate<String, String> ALL_DISABLED = (bundleId, baseClassName) -> false;

  /** Predicate that enables every role — backward-compat baseline. */
  private static final BiPredicate<String, String> ALL_ENABLED  = (bundleId, baseClassName) -> true;

  /** Predicate that disables only "Participant". */
  private static final BiPredicate<String, String> PARTICIPANT_DISABLED =
      (bundleId, baseClassName) -> !(TFW_BASE_CLASS_PARTICIPANT.equals(baseClassName));

  private TrustFrameworkRegistry loireRegistry;
  private TrustFrameworkRegistry rootsOnlyRegistry;
  private ContentAccessorDirect  compositeOntology;
  private StreamManager          streamManager;

  @BeforeAll
  void setUp() throws IOException {
    String ttlPath = "Schema-Tests/gx-2511-test-ontology.ttl";
    ContentAccessorDirect gx2511Ontology;
    try (InputStream is = getClass().getClassLoader().getResourceAsStream(ttlPath)) {
      if (is == null) {
        throw new IllegalStateException("Test resource not found: " + ttlPath);
      }
      gx2511Ontology = new ContentAccessorDirect(
          new String(is.readAllBytes(), StandardCharsets.UTF_8));
    }
    streamManager = StreamManager.get().clone();

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

    // roots-only (no boot-time ontology) — forces composite-ontology slow path
    Map<String, BaseClassConfig> rootsOnlyRoles = Map.of(
        TFW_BASE_CLASS_PARTICIPANT, new BaseClassConfig(List.of(), List.of()),
        TFW_BASE_CLASS_SERVICE_OFFERING, new BaseClassConfig(
            List.of(NAMESPACE + TFW_BASE_CLASS_DIGITAL_SERVICE_OFFERING), List.of()),
        TFW_BASE_CLASS_RESOURCE, new BaseClassConfig(List.of(), List.of())
    );
    FrameworkBundleConfig rootsOnlyConfig = new FrameworkBundleConfig(
        PROFILE_ID, TFW_FAMILY_GAIA_X, NAMESPACE, ValidationType.SHACL, rootsOnlyRoles, Map.of());
    rootsOnlyRegistry = new TrustFrameworkRegistry(
        List.of(new TrustFrameworkBundle(rootsOnlyConfig, null, null)));

    compositeOntology = new ContentAccessorDirect("""
        @prefix gx: <%s> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix ex: <https://example.com/> .

        ex:CustomParticipant rdfs:subClassOf gx:Participant .
        """.formatted(NAMESPACE));
  }

  @Test
  @DisplayName("Direct match on disabled role throws ClientException")
  void resolveSubjectBaseClass_directMatch_disabledRole_throwsClientException() {
    String credential = buildCredential(NAMESPACE + "LegalPerson");

    assertThrows(ClientException.class, () ->
        ClaimValidator.resolveSubjectBaseClass(
            streamManager, credential, loireRegistry, null, ALL_DISABLED),
        "A disabled role direct match must throw ClientException (HTTP 400)");
  }

  @Test
  @DisplayName("additionalRoots match on disabled role throws ClientException")
  void resolveSubjectBaseClass_additionalRootsMatch_disabledRole_throwsClientException() {
    // gx:DigitalServiceOffering is indexed via additionalRoots, not namespace+baseClassName
    String credential = buildCredential(NAMESPACE + TFW_BASE_CLASS_DIGITAL_SERVICE_OFFERING);

    assertThrows(ClientException.class, () ->
        ClaimValidator.resolveSubjectBaseClass(
            streamManager, credential, loireRegistry, null, ALL_DISABLED),
        "A disabled role matched via additionalRoots must throw ClientException");
  }

  @Test
  @DisplayName("Ontology subclass walk on disabled role throws ClientException")
  void resolveSubjectBaseClass_ontologyWalk_disabledRole_throwsClientException() {
    // ex:CustomParticipant is not in the boot index; only resolvable via composite ontology
    String credential = buildCredential("https://example.com/CustomParticipant");

    assertThrows(ClientException.class, () ->
        ClaimValidator.resolveSubjectBaseClass(
            streamManager, credential, rootsOnlyRegistry, compositeOntology, ALL_DISABLED),
        "A disabled role matched via ontology subclass walk must throw ClientException");
  }

  @Test
  @DisplayName("Enabled role in same bundle resolves normally when another role is disabled")
  void resolveSubjectBaseClass_enabledRole_whileOtherRoleDisabled_resolvesNormally() {
    // PARTICIPANT_DISABLED disables only TFW_BASE_CLASS_PARTICIPANT; TFW_BASE_CLASS_SERVICE_OFFERING remains enabled
    String credential = buildCredential(NAMESPACE + TFW_BASE_CLASS_DIGITAL_SERVICE_OFFERING);

    ResolvedBaseClass result = ClaimValidator.resolveSubjectBaseClass(
        streamManager, credential, loireRegistry, null, PARTICIPANT_DISABLED);

    assertEquals(new ResolvedBaseClass(PROFILE_ID, TFW_BASE_CLASS_SERVICE_OFFERING), result,
        "ServiceOffering must resolve normally even when Participant is disabled");
  }

  @Test
  @DisplayName("Default-true predicate (all-enabled) resolves Participant normally")
  void resolveSubjectBaseClass_allEnabled_resolvesPrimaryRoleNormally() {
    String credential = buildCredential(NAMESPACE + "LegalPerson");

    ResolvedBaseClass result = ClaimValidator.resolveSubjectBaseClass(
        streamManager, credential, loireRegistry, null, ALL_ENABLED);

    assertEquals(new ResolvedBaseClass(PROFILE_ID, TFW_BASE_CLASS_PARTICIPANT), result,
        "With all roles enabled the predicate must not interfere with normal resolution");
  }

  @Test
  @DisplayName("Unknown type returns UNKNOWN regardless of predicate")
  void resolveSubjectBaseClass_unknownType_returnsUnknown() {
    String credential = buildCredential("https://example.com/SomeOtherType");

    ResolvedBaseClass result = ClaimValidator.resolveSubjectBaseClass(
        streamManager, credential, loireRegistry, null, ALL_ENABLED);

    assertFalse(result.isResolved(),
        "Type not in any registered bundle should still return UNKNOWN");
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
