package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.api.generated.model.ProvenanceCredential;
import eu.xfsc.fc.api.generated.model.ProvenanceCredentials;
import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.provenance.ProvenanceCredentialRepository;
import eu.xfsc.fc.core.dao.provenance.ProvenanceRecord;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.dao.assets.ContentKind;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.provenance.ProvenanceService;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.service.verification.VerificationService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Integration tests for {@link ProvenanceService}: verifies DB persistence, PROV-O graph storage,
 * and validation error paths using an embedded Postgres and Fuseki backend.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=fuseki"})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class ProvenanceServiceTest {

  private static final String ASSET_ID = "did:web:example:asset";
  private static final String ASSET_IRI = ASSET_ID + ":v1";
  private static final String CREDENTIAL_ID = "did:vc:prov-test-001";
  private static final String ISSUER = "did:web:issuer.example";
  private static final String PROTECTED_OBJECT =
      "https://projects.eclipse.org/projects/technology.xfsc/federated-catalogue/meta#attacker";
  private static final String ACTIVITY_IRI = "did:web:issuer.example:activity-creation";
  private static final String CRED_SUBJECT_URI = "https://www.w3.org/2018/credentials#credentialSubject";
  private static final String PROV_WAS_GENERATED_BY = "http://www.w3.org/ns/prov#wasGeneratedBy";
  private static final Instant ISSUED_AT = Instant.parse("2024-01-01T00:00:00Z");

  private static final String VALID_VC = """
      {
        "id": "%s",
        "@context": ["https://www.w3.org/2018/credentials/v1"],
        "credentialSubject": {
          "id": "%s",
          "prov:wasGeneratedBy": "%s"
        }
      }
      """.formatted(CREDENTIAL_ID, ASSET_IRI, ACTIVITY_IRI);

  private static final String NO_PREDICATE_VC = """
      {
        "id": "did:vc:prov-no-predicate",
        "@context": ["https://www.w3.org/2018/credentials/v1"],
        "credentialSubject": {
          "id": "%s",
          "provenanceType": "CREATION"
        }
      }
      """.formatted(ASSET_IRI);

  private static final String PROTECTED_OBJECT_VC = """
      {
        "id": "did:vc:prov-protected",
        "@context": ["https://www.w3.org/2018/credentials/v1"],
        "credentialSubject": {
          "id": "%s",
          "prov:wasAttributedTo": "%s"
        }
      }
      """.formatted(ASSET_IRI, PROTECTED_OBJECT);

  @MockitoBean
  private VerificationService verificationService;

  @Autowired
  private ProvenanceService provenanceService;

  @Autowired
  private AssetDao assetDao;

  @Autowired
  private ProvenanceCredentialRepository provenanceRepository;

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @Autowired
  private GraphStore graphStore;

  @BeforeEach
  void setUp() {
    transactionTemplate.executeWithoutResult(status ->
        jdbcTemplate.execute("TRUNCATE provenance_credentials, assets_aud, revinfo, assets CASCADE"));
    graphStore.deleteClaims(ASSET_IRI);
    transactionTemplate.executeWithoutResult(status ->
        assetDao.insert(AssetRecord.builder()
            .assetHash("hash-prov-svc-test")
            .id(ASSET_ID)
            .issuer(ISSUER)
            .uploadTime(ISSUED_AT)
            .statusTime(ISSUED_AT)
            .expirationTime(null)
            .status(AssetStatus.ACTIVE)
            .content(new ContentAccessorDirect("{}"))
            .validatorDids(List.of())
            .contentType("application/ld+json")
            .fileSize(10L)
            .originalFilename("test.jsonld")
            .contentKind(ContentKind.RDF)
            .build()));
  }

  @Test
  void add_validVc_storesPersistentRecord() {
    when(verificationService.verifyCredential(any())).thenReturn(successResult(ASSET_IRI, ISSUER));

    ProvenanceCredential result = provenanceService.add(ASSET_ID, null, VALID_VC, null);

    assertNotNull(result);
    assertEquals(CREDENTIAL_ID, result.getCredentialId());
    assertEquals(ISSUER, result.getIssuer());
    assertEquals(ProvenanceCredential.ProvenanceTypeEnum.CREATION, result.getProvenanceType());
    assertTrue(provenanceRepository.existsByCredentialId(CREDENTIAL_ID));
  }

  @Test
  void add_validVc_storesProvOTriplesInGraph() {
    when(verificationService.verifyCredential(any())).thenReturn(successResult(ASSET_IRI, ISSUER));

    provenanceService.add(ASSET_ID, null, VALID_VC, null);

    String sparql = "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> <%s> <%s> }"
        .formatted(CRED_SUBJECT_URI, ASSET_IRI);
    List<Map<String, Object>> rows = graphStore.queryData(
        new GraphQuery(sparql, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false)
    ).getResults();

    assertEquals(1, rows.size());
    assertEquals(ASSET_IRI, rows.getFirst().get("s"));
    assertEquals(PROV_WAS_GENERATED_BY, rows.getFirst().get("p"));
    assertEquals(ACTIVITY_IRI, rows.getFirst().get("o"));
  }

  @Test
  void add_wrongSubjectId_throwsClientException() {
    when(verificationService.verifyCredential(any()))
        .thenReturn(successResult("did:web:wrong:subject", ISSUER));

    assertThrows(ClientException.class, () ->
        provenanceService.add(ASSET_ID, null, VALID_VC, null));
  }

  @Test
  void add_noPredicate_throwsClientException() {
    when(verificationService.verifyCredential(any()))
        .thenReturn(successResult(ASSET_IRI, ISSUER));

    assertThrows(ClientException.class, () ->
        provenanceService.add(ASSET_ID, null, NO_PREDICATE_VC, null));
  }

  @Test
  void add_duplicateCredentialId_throwsConflictException() {
    when(verificationService.verifyCredential(any())).thenReturn(successResult(ASSET_IRI, ISSUER));

    provenanceService.add(ASSET_ID, null, VALID_VC, null);

    assertThrows(ConflictException.class, () ->
        provenanceService.add(ASSET_ID, null, VALID_VC, null));
  }

  @Test
  void add_protectedNamespaceObject_throwsClientException() {
    when(verificationService.verifyCredential(any()))
        .thenReturn(successResult(ASSET_IRI, ISSUER));

    assertThrows(ClientException.class, () ->
        provenanceService.add(ASSET_ID, null, PROTECTED_OBJECT_VC, null));
  }

  @Test
  void add_nonExistentAsset_throwsNotFoundException() {
    assertThrows(NotFoundException.class, () ->
        provenanceService.add("did:web:unknown:asset", null, VALID_VC, null));
  }

  @Test
  void list_withVersionFilter_returnsFilteredPage() {
    insertRecord(CREDENTIAL_ID, 1, ISSUED_AT);
    insertRecord("did:vc:prov-v2-001", 2, ISSUED_AT.plusSeconds(10));

    assertEquals(2, provenanceRepository.count());

    ProvenanceCredentials page = provenanceService.list(ASSET_ID, 1, Pageable.ofSize(10));

    assertEquals(Integer.valueOf(1), page.getTotalCount());
    assertEquals(1, page.getItems().size());
    assertEquals(CREDENTIAL_ID, page.getItems().getFirst().getCredentialId());
  }

  @Test
  void get_existingCredentialId_returnsCredential() {
    insertRecord(CREDENTIAL_ID, 1, ISSUED_AT);

    ProvenanceCredential result = provenanceService.get(ASSET_ID, CREDENTIAL_ID);

    assertNotNull(result);
    assertEquals(CREDENTIAL_ID, result.getCredentialId());
    assertEquals(ASSET_ID, result.getAssetId());
  }

  @Test
  void verifyOne_validVc_returnsValidResult() {
    insertRecord(CREDENTIAL_ID, 1, ISSUED_AT);
    when(verificationService.verifyCredential(any())).thenReturn(successResult(ASSET_IRI, ISSUER));

    var result = provenanceService.verifyOne(ASSET_ID, CREDENTIAL_ID);

    assertTrue(result.getIsValid());
  }

  @Test
  void list_thirtyCredentials_firstPageReturnsPageSizeItems() {
    for (int i = 1; i <= 30; i++) {
      insertRecord("did:vc:prov-page-" + i, 1, ISSUED_AT.plusSeconds(i));
    }

    ProvenanceCredentials page = provenanceService.list(ASSET_ID, null, PageRequest.of(0, 10));

    assertEquals(Integer.valueOf(30), page.getTotalCount());
    assertEquals(10, page.getItems().size());
  }

  @Test
  void verifyAll_oneFails_returnsInvalidWithErrors() {
    // Records sorted DESC by issuedAt: laterRecord first, earlierRecord second
    Instant laterTime = ISSUED_AT.plusSeconds(60);
    insertRecord("did:vc:prov-later-001", 1, laterTime);
    insertRecord("did:vc:prov-earlier-001", 1, ISSUED_AT);

    when(verificationService.verifyCredential(any()))
        .thenReturn(successResult(ASSET_IRI, ISSUER))
        .thenThrow(new VerificationException("Signature invalid"));

    var result = provenanceService.verifyAll(ASSET_ID, null);

    assertFalse(result.getIsValid());
    assertFalse(result.getErrors().isEmpty());
  }

  private void insertRecord(String credentialId, int version, Instant issuedAt) {
    transactionTemplate.executeWithoutResult(status ->
        provenanceRepository.save(ProvenanceRecord.builder()
            .assetId(ASSET_ID)
            .assetVersion(version)
            .credentialId(credentialId)
            .issuer(ISSUER)
            .issuedAt(issuedAt)
            .provenanceType(ProvenanceType.CREATION)
            .credentialContent(VALID_VC)
            .credentialFormat("JSONLD")
            .build()));
  }

  private CredentialVerificationResult successResult(String subjectId, String issuer) {
    return new CredentialVerificationResult(
        Instant.now(), "Active", issuer, ISSUED_AT, subjectId, List.of(), null, "", "");
  }
}
