package eu.xfsc.fc.core.service.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.provenance.ProvenanceCredentialRepository;
import eu.xfsc.fc.core.dao.provenance.ProvenanceRecord;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.VerificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * Unit tests for {@link ProvenanceServiceImpl#verifyAll}, covering the zero-credential
 * verification defect alongside the existing valid/invalid regression paths.
 *
 * <p>Collaborators are mocked directly (no Spring context, no graph-store backend) since
 * {@code verifyAll} never touches the graph store or asset content — only the provenance
 * repository, the asset DAO, and the verification service.</p>
 */
@ExtendWith(MockitoExtension.class)
class ProvenanceServiceImplVerifyAllTest {

  private static final String ASSET_ID = "did:web:example:zero-cred-asset";
  private static final String ISSUER = "did:web:issuer.example";
  private static final String CREDENTIAL_ID = "did:vc:prov-verify-all-001";
  private static final String CREDENTIAL_CONTENT = "{\"id\":\"" + CREDENTIAL_ID + "\"}";
  private static final Instant ISSUED_AT = Instant.parse("2024-01-01T00:00:00Z");
  private static final String VERIFICATION_FAILURE_REASON = "Signature invalid";
  private static final String NO_CREDENTIALS_REASON = "No provenance credentials present for this asset";

  @Mock
  private VerificationService verificationService;
  @Mock
  private ProvenanceCredentialRepository repository;
  @Mock
  private AssetDao assetDao;
  @Mock
  private GraphStore graphStore;
  @Mock
  private ProtectedNamespaceFilter namespaceFilter;
  @Mock
  private ProvenanceCredentialParser parser;

  private ProvenanceServiceImpl provenanceService;

  @BeforeEach
  void setUp() {
    ProvenanceModelMapper mapper = new ProvenanceModelMapper(new ObjectMapper().registerModule(new JavaTimeModule()));
    provenanceService = new ProvenanceServiceImpl(
        verificationService, repository, assetDao, graphStore, namespaceFilter, parser, mapper);
  }

  @Test
  void verifyAll_assetWithNoProvenanceCredentials_doesNotReportValid() {
    when(assetDao.getVersionCount(ASSET_ID)).thenReturn(1);
    when(repository.findByAssetIdOrderByIssuedAtDesc(eq(ASSET_ID), any())).thenReturn(Page.empty());

    ProvenanceVerificationResult result = provenanceService.verifyAll(ASSET_ID, null);

    assertEquals(Boolean.FALSE, result.getIsValid(),
        () -> "vacuous pass: verifying an asset with zero provenance credentials must report "
            + "isValid=false, not merely 'not true' — actual result: " + result);
    assertNull(result.getVerificationTimestamp(),
        "no credential was inspected, so verificationTimestamp must stay null — this is the "
            + "signal that distinguishes 'nothing to verify' from a genuine verified-and-invalid "
            + "result (which always carries a timestamp)");
    assertEquals(List.of(NO_CREDENTIALS_REASON), result.getErrors());
  }

  @Test
  void verifyAll_versionWithNoCredentialsWhileOtherVersionHasValidCredential_doesNotReportValidForEmptyVersion() {
    when(assetDao.getVersionCount(ASSET_ID)).thenReturn(2);
    ProvenanceRecord versionOneRecord = recordWithCredential(CREDENTIAL_ID, 1);
    when(repository.findByAssetIdAndAssetVersionOrderByIssuedAtDesc(eq(ASSET_ID), eq(1), any()))
        .thenReturn(new PageImpl<>(List.of(versionOneRecord)));
    when(repository.findByAssetIdAndAssetVersionOrderByIssuedAtDesc(eq(ASSET_ID), eq(2), any()))
        .thenReturn(Page.empty());
    when(verificationService.verifyCredential(any(), eq(false))).thenReturn(successResult());

    ProvenanceVerificationResult versionOneResult = provenanceService.verifyAll(ASSET_ID, 1);
    ProvenanceVerificationResult versionTwoResult = provenanceService.verifyAll(ASSET_ID, 2);

    assertEquals(Boolean.TRUE, versionOneResult.getIsValid(),
        "precondition: version 1 has a valid provenance credential and must verify true");
    assertEquals(Boolean.FALSE, versionTwoResult.getIsValid(),
        () -> "vacuous pass: version 2 has zero provenance credentials and must report "
            + "isValid=false, not merely 'not true' — actual result: " + versionTwoResult);
  }

  @Test
  void verifyAll_assetWithValidCredential_returnsValidTrue() {
    when(assetDao.getVersionCount(ASSET_ID)).thenReturn(1);
    ProvenanceRecord record = recordWithCredential(CREDENTIAL_ID, 1);
    when(repository.findByAssetIdOrderByIssuedAtDesc(eq(ASSET_ID), any()))
        .thenReturn(new PageImpl<>(List.of(record)));
    when(verificationService.verifyCredential(any(), eq(false))).thenReturn(successResult());

    ProvenanceVerificationResult result = provenanceService.verifyAll(ASSET_ID, null);

    assertEquals(Boolean.TRUE, result.getIsValid());
    assertEquals(List.of(), result.getErrors());
  }

  @Test
  void verifyAll_assetWithInvalidCredential_returnsValidFalseWithReason() {
    when(assetDao.getVersionCount(ASSET_ID)).thenReturn(1);
    ProvenanceRecord record = recordWithCredential(CREDENTIAL_ID, 1);
    when(repository.findByAssetIdOrderByIssuedAtDesc(eq(ASSET_ID), any()))
        .thenReturn(new PageImpl<>(List.of(record)));
    when(verificationService.verifyCredential(any(), eq(false)))
        .thenThrow(new VerificationException(VERIFICATION_FAILURE_REASON));

    ProvenanceVerificationResult result = provenanceService.verifyAll(ASSET_ID, null);

    assertEquals(Boolean.FALSE, result.getIsValid());
    assertEquals(List.of("[" + CREDENTIAL_ID + "] " + VERIFICATION_FAILURE_REASON), result.getErrors());
    assertNotNull(result.getVerificationTimestamp(),
        "a credential was actually inspected here, so verificationTimestamp must be set — this "
            + "distinguishes a genuine verified-and-invalid result from the zero-credential case");
  }

  private ProvenanceRecord recordWithCredential(String credentialId, int version) {
    return ProvenanceRecord.builder()
        .assetId(ASSET_ID)
        .assetVersion(version)
        .credentialId(credentialId)
        .issuer(ISSUER)
        .issuedAt(ISSUED_AT)
        .provenanceType(ProvenanceType.CREATION)
        .credentialContent(CREDENTIAL_CONTENT)
        .credentialFormat("JSONLD")
        .build();
  }

  private CredentialVerificationResult successResult() {
    return new CredentialVerificationResult(
        Instant.now(), "Active", ISSUER, ISSUED_AT, ASSET_ID + ":v1", List.of(), null, "", "");
  }
}
