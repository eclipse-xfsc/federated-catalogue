package eu.xfsc.fc.core.service.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.verification.VerificationService;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvenanceCredentialParserTest {

  private static final String PROV_NS = "http://www.w3.org/ns/prov#";
  private static final String SUBJECT_ID = "did:web:example:asset:v1";
  private static final String OBJECT_VALUE = "did:web:example:activity";

  @Mock
  private VerificationService verificationService;

  private ProvenanceCredentialParser parser;

  @BeforeEach
  void setUp() {
    parser = new ProvenanceCredentialParser(verificationService, new ObjectMapper());
  }

  static Stream<Arguments> compactPredicates() {
    return Stream.of(
        Arguments.of("prov:wasGeneratedBy", ProvenanceType.CREATION),
        Arguments.of("prov:wasDerivedFrom", ProvenanceType.DERIVATION),
        Arguments.of("prov:wasAttributedTo", ProvenanceType.ATTRIBUTION),
        Arguments.of("prov:wasRevisionOf", ProvenanceType.MODIFICATION),
        Arguments.of("prov:generated", ProvenanceType.GENERATION),
        Arguments.of("prov:used", ProvenanceType.USAGE),
        Arguments.of("prov:wasAssociatedWith", ProvenanceType.ASSOCIATION),
        Arguments.of("prov:actedOnBehalfOf", ProvenanceType.DELEGATION)
    );
  }

  @ParameterizedTest
  @MethodSource("compactPredicates")
  void extractCredentialInfo_compactPredicate_returnsCorrectProvenance(String predicate, ProvenanceType expected) {
    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vcWith(predicate, OBJECT_VALUE));

    assertEquals(expected, info.primary().type());
    assertEquals(OBJECT_VALUE, info.primary().objectValue());
    assertEquals(1, info.facts().size());
  }

  static Stream<Arguments> fullIriPredicates() {
    return Stream.of(
        Arguments.of(PROV_NS + "wasGeneratedBy", ProvenanceType.CREATION),
        Arguments.of(PROV_NS + "wasDerivedFrom", ProvenanceType.DERIVATION),
        Arguments.of(PROV_NS + "wasAttributedTo", ProvenanceType.ATTRIBUTION),
        Arguments.of(PROV_NS + "wasRevisionOf", ProvenanceType.MODIFICATION),
        Arguments.of(PROV_NS + "generated", ProvenanceType.GENERATION),
        Arguments.of(PROV_NS + "used", ProvenanceType.USAGE),
        Arguments.of(PROV_NS + "wasAssociatedWith", ProvenanceType.ASSOCIATION),
        Arguments.of(PROV_NS + "actedOnBehalfOf", ProvenanceType.DELEGATION)
    );
  }

  @ParameterizedTest
  @MethodSource("fullIriPredicates")
  void extractCredentialInfo_fullIriPredicate_returnsCorrectProvenance(String predicate, ProvenanceType expected) {
    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vcWith(predicate, OBJECT_VALUE));

    assertEquals(expected, info.primary().type());
    assertEquals(OBJECT_VALUE, info.primary().objectValue());
    assertEquals(1, info.facts().size());
  }

  @Test
  void extractCredentialInfo_withCredentialId_returnsPresent() {
    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vcWith("prov:wasGeneratedBy", OBJECT_VALUE));

    assertEquals("did:vc:test-001", info.credentialId());
  }

  @Test
  void extractCredentialInfo_withoutCredentialId_returnsEmpty() {
    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vcWithoutId("prov:wasGeneratedBy", OBJECT_VALUE));

    assertNull(info.credentialId());
  }

  @Test
  void extractCredentialInfo_withContext_returnsJsonldFormat() {
    String vc = """
        {
          "@context": ["https://www.w3.org/2018/credentials/v1"],
          "credentialSubject": {"prov:wasGeneratedBy": "%s"}
        }
        """.formatted(OBJECT_VALUE);

    assertEquals("JSONLD", parser.extractCredentialInfo(vc).formatLabel());
  }

  @Test
  void extractCredentialInfo_withoutContext_returnsJsonldJwtFormat() {
    assertEquals("JSONLD_JWT", parser.extractCredentialInfo(vcWith("prov:wasGeneratedBy", OBJECT_VALUE)).formatLabel());
  }

  @Test
  void extractCredentialInfo_jwtInput_throwsClientException() {
    assertThrows(ClientException.class,
        () -> parser.extractCredentialInfo("eyJhbGciOiJFZERTQSJ9.e30.sig"));
  }

  @Test
  void extractCredentialInfo_missingCredentialSubject_throwsClientException() {
    String vc = """
        {"id": "did:vc:test"}
        """;

    assertThrows(ClientException.class, () -> parser.extractCredentialInfo(vc));
  }

  @Test
  void extractCredentialInfo_noProvPredicate_throwsClientException() {
    String vc = """
        {
          "credentialSubject": {
            "id": "%s",
            "provenanceType": "CREATION"
          }
        }
        """.formatted(SUBJECT_ID);

    assertThrows(ClientException.class, () -> parser.extractCredentialInfo(vc));
  }

  @Test
  void parseAndValidateVc_bypassesTrustFrameworkRoleCheck() {
    // Provenance VCs intentionally lack Participant/ServiceOffering/Resource type — the
    // role-resolution guard must be opted out on this path.
    String rawVc = vcWith("prov:wasGeneratedBy", OBJECT_VALUE);
    when(verificationService.verifyCredential(ArgumentMatchers.any(ContentAccessor.class), ArgumentMatchers.eq(false)))
        .thenReturn(mock(CredentialVerificationResult.class));

    parser.parseAndValidateVc(rawVc, "JSONLD");

    verify(verificationService).verifyCredential(ArgumentMatchers.any(ContentAccessor.class), ArgumentMatchers.eq(false));
  }

  @Test
  void extractCredentialSubjectId_entityCentricVc_returnsVersionedAssetIri() {
    String vc = vcWith("prov:wasGeneratedBy", OBJECT_VALUE);

    String subjectId = parser.extractCredentialSubjectId(vc);

    assertNotNull(subjectId);
    assertEquals(SUBJECT_ID, subjectId);
  }

  @Test
  void extractCredentialSubjectId_activityCentricVc_returnsActivityIri() {
    String activityIri = "urn:activity:alice-created-v1";
    String vc = """
        {
          "id": "did:vc:activity-subject-extract",
          "credentialSubject": {
            "id": "%s",
            "prov:generated": "%s",
            "prov:wasAssociatedWith": "did:web:alice"
          }
        }
        """.formatted(activityIri, SUBJECT_ID);

    String subjectId = parser.extractCredentialSubjectId(vc);

    assertEquals(activityIri, subjectId,
        "Activity-centric VC must surface the activity's own IRI, not the linked asset IRI");
  }

  @Test
  void extractCredentialSubjectId_missingCredentialSubject_returnsNull() {
    String vc = """
        {"id": "did:vc:no-subject"}
        """;

    assertNull(parser.extractCredentialSubjectId(vc));
  }

  @Test
  void extractCredentialSubjectId_credentialSubjectWithoutId_returnsNull() {
    String vc = """
        {
          "credentialSubject": {
            "prov:wasGeneratedBy": "%s"
          }
        }
        """.formatted(OBJECT_VALUE);

    assertNull(parser.extractCredentialSubjectId(vc));
  }

  @Test
  void extractCredentialSubjectId_malformedJson_returnsNull() {
    assertNull(parser.extractCredentialSubjectId("not even json"));
  }

  @Test
  void extractCredentialSubjectId_payloadWithLeadingWhitespace_returnsIdAfterStripping() {
    String activityIri = "urn:activity:trimmed";
    String vc = "   \n  " + """
        {
          "credentialSubject": { "id": "%s", "prov:wasGeneratedBy": "%s" }
        }
        """.formatted(activityIri, OBJECT_VALUE);

    assertEquals(activityIri, parser.extractCredentialSubjectId(vc));
  }

  @Test
  void extractCredentialInfo_multiplePredicatesOnSameSubject_returnsAllFactsInOrder() {
    String vc = """
        {
          "id": "did:vc:test-multi",
          "credentialSubject": {
            "id": "%s",
            "prov:wasGeneratedBy": "did:web:example:activity",
            "prov:wasAssociatedWith": "did:web:example:agent",
            "prov:actedOnBehalfOf": "did:web:example:organisation"
          }
        }
        """.formatted(SUBJECT_ID);

    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vc);

    assertEquals(3, info.facts().size());
    assertEquals(ProvenanceType.CREATION, info.facts().get(0).type());
    assertEquals(ProvenanceType.ASSOCIATION, info.facts().get(1).type());
    assertEquals(ProvenanceType.DELEGATION, info.facts().get(2).type());
    assertEquals(ProvenanceType.CREATION, info.primary().type());
  }

  @ParameterizedTest
  @MethodSource("compactPredicates")
  void extractCredentialInfo_objectReferenceWithJsonLdId_returnsIriFromAtId(String predicate, ProvenanceType expected) {
    String vc = """
        {
          "id": "did:vc:test-002",
          "credentialSubject": {
            "id": "%s",
            "%s": {"@id": "%s"}
          }
        }
        """.formatted(SUBJECT_ID, predicate, OBJECT_VALUE);

    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vc);

    assertEquals(expected, info.primary().type());
    assertEquals(OBJECT_VALUE, info.primary().objectValue());
  }

  @ParameterizedTest
  @MethodSource("compactPredicates")
  void extractCredentialInfo_objectReferenceWithCompactId_returnsIriFromId(String predicate, ProvenanceType expected) {
    String vc = """
        {
          "id": "did:vc:test-003",
          "credentialSubject": {
            "id": "%s",
            "%s": {"id": "%s"}
          }
        }
        """.formatted(SUBJECT_ID, predicate, OBJECT_VALUE);

    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vc);

    assertEquals(expected, info.primary().type());
    assertEquals(OBJECT_VALUE, info.primary().objectValue());
  }

  @Test
  void extractCredentialInfo_nestedObjectReferenceWithExtraProperties_returnsTopLevelIri() {
    // PROV-O wasAssociatedWith may carry a nested Agent description; only the @id matters here.
    String vc = """
        {
          "id": "did:vc:test-004",
          "credentialSubject": {
            "id": "%s",
            "prov:wasAssociatedWith": {
              "id": "%s",
              "type": "prov:Agent",
              "prov:actedOnBehalfOf": {"id": "did:web:org"}
            }
          }
        }
        """.formatted(SUBJECT_ID, OBJECT_VALUE);

    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vc);

    assertEquals(ProvenanceType.ASSOCIATION, info.primary().type());
    assertEquals(OBJECT_VALUE, info.primary().objectValue());
  }

  @Test
  void extractCredentialInfo_objectReferenceWithoutIdField_throwsClientException() {
    String vc = """
        {
          "credentialSubject": {
            "id": "%s",
            "prov:wasGeneratedBy": {"type": "prov:Activity"}
          }
        }
        """.formatted(SUBJECT_ID);

    assertThrows(ClientException.class, () -> parser.extractCredentialInfo(vc));
  }

  @Test
  void extractCredentialInfo_activityCentricVc_returnsTypeInformedByTimestampAndActionFacts() {
    String activityIri = "https://example.org/templates/dpa/v1#prov-approved";
    String vc = """
        {
          "@context": [
            "https://www.w3.org/ns/credentials/v2",
            { "prov": "http://www.w3.org/ns/prov#",
              "dcs": "https://w3id.org/facis/dcs/1#" }
          ],
          "id": "did:vc:activity-approved",
          "credentialSubject": {
            "id": "%s",
            "type": "prov:Activity",
            "prov:wasAssociatedWith": "did:web:example:bob",
            "prov:used": "https://example.org/templates/dpa/v1",
            "prov:wasInformedBy": "https://example.org/templates/dpa/v1#prov-created",
            "prov:startedAtTime": "2026-06-02T14:00:00Z",
            "prov:endedAtTime": "2026-06-02T14:15:00Z",
            "dcs:action": "approved"
          }
        }
        """.formatted(activityIri);

    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vc);

    java.util.List<ProvenanceType> types = info.facts().stream()
        .map(ProvenanceInfo::type)
        .toList();
    assertTrue(types.contains(ProvenanceType.TYPE));
    assertTrue(types.contains(ProvenanceType.ASSOCIATION));
    assertTrue(types.contains(ProvenanceType.USAGE));
    assertTrue(types.contains(ProvenanceType.INFORMATION));
    assertTrue(types.contains(ProvenanceType.STARTED_AT_TIME));
    assertTrue(types.contains(ProvenanceType.ENDED_AT_TIME));
    assertTrue(types.contains(ProvenanceType.ACTION));

    ProvenanceInfo typeFact = info.facts().stream()
        .filter(f -> f.type() == ProvenanceType.TYPE).findFirst().orElseThrow();
    assertEquals(ProvenanceInfo.ObjectKind.IRI, typeFact.objectKind());
    assertEquals(PROV_NS + "Activity", typeFact.objectValue());

    ProvenanceInfo startedFact = info.facts().stream()
        .filter(f -> f.type() == ProvenanceType.STARTED_AT_TIME).findFirst().orElseThrow();
    assertEquals(ProvenanceInfo.ObjectKind.LITERAL, startedFact.objectKind());
    assertEquals("2026-06-02T14:00:00Z", startedFact.objectValue());
    assertEquals("http://www.w3.org/2001/XMLSchema#dateTime", startedFact.datatypeIri());

    ProvenanceInfo actionFact = info.facts().stream()
        .filter(f -> f.type() == ProvenanceType.ACTION).findFirst().orElseThrow();
    assertEquals(ProvenanceInfo.ObjectKind.LITERAL, actionFact.objectKind());
    assertEquals("approved", actionFact.objectValue());
    assertNull(actionFact.datatypeIri());

    assertEquals(ProvenanceType.ASSOCIATION, info.primary().type());
  }

  @Test
  void extractCredentialInfo_predicateWithNullValue_throwsClientException() {
    String vc = """
        {
          "credentialSubject": {
            "id": "%s",
            "prov:wasGeneratedBy": null
          }
        }
        """.formatted(SUBJECT_ID);

    assertThrows(ClientException.class, () -> parser.extractCredentialInfo(vc));
  }

  private static String vcWith(String predicate, String objectValue) {
    return """
        {
          "id": "did:vc:test-001",
          "credentialSubject": {
            "id": "%s",
            "%s": "%s"
          }
        }
        """.formatted(SUBJECT_ID, predicate, objectValue);
  }

  private static String vcWithoutId(String predicate, String objectValue) {
    return """
        {
          "credentialSubject": {
            "id": "%s",
            "%s": "%s"
          }
        }
        """.formatted(SUBJECT_ID, predicate, objectValue);
  }
}
