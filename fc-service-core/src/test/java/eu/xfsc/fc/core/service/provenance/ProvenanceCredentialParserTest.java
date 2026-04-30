package eu.xfsc.fc.core.service.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.service.verification.VerificationService;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
        Arguments.of("prov:wasRevisionOf", ProvenanceType.MODIFICATION)
    );
  }

  @ParameterizedTest
  @MethodSource("compactPredicates")
  void extractCredentialInfo_compactPredicate_returnsCorrectProvenance(String predicate, ProvenanceType expected) {
    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vcWith(predicate, OBJECT_VALUE));

    assertEquals(expected, info.provenance().type());
    assertEquals(OBJECT_VALUE, info.provenance().objectValue());
  }

  static Stream<Arguments> fullIriPredicates() {
    return Stream.of(
        Arguments.of(PROV_NS + "wasGeneratedBy", ProvenanceType.CREATION),
        Arguments.of(PROV_NS + "wasDerivedFrom", ProvenanceType.DERIVATION),
        Arguments.of(PROV_NS + "wasAttributedTo", ProvenanceType.ATTRIBUTION),
        Arguments.of(PROV_NS + "wasRevisionOf", ProvenanceType.MODIFICATION)
    );
  }

  @ParameterizedTest
  @MethodSource("fullIriPredicates")
  void extractCredentialInfo_fullIriPredicate_returnsCorrectProvenance(String predicate, ProvenanceType expected) {
    ProvenanceCredentialInfo info = parser.extractCredentialInfo(vcWith(predicate, OBJECT_VALUE));

    assertEquals(expected, info.provenance().type());
    assertEquals(OBJECT_VALUE, info.provenance().objectValue());
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
