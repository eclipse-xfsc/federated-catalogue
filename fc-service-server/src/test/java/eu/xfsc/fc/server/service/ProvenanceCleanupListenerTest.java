package eu.xfsc.fc.server.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.provenance.ProvenanceService;
import eu.xfsc.fc.core.util.HashUtils;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Tests {@code ProvenanceCleanupListener} rollback behaviour.
 *
 * <p>Verifies that when the listener throws inside the BEFORE_COMMIT phase, the outer
 * asset-deletion transaction is rolled back and the asset row remains in the database.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {"graphstore.impl=none"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class ProvenanceCleanupListenerTest {

  private static final String TEST_ISSUER = "http://example.org/test-issuer";
  private static final String ASSET_ID = "did:web:test:provenance-rollback-asset";
  private static final String ASSET_CONTENT =
      "{\"@context\": \"https://example.org/\", \"id\": \"" + ASSET_ID + "\"}";
  private static final String ASSET_HASH = HashUtils.calculateSha256AsHex(ASSET_CONTENT);

  @Autowired
  private AssetStore assetStore;

  @MockitoSpyBean
  private ProvenanceService provenanceService;

  @AfterEach
  void cleanUp() {
    reset(provenanceService);
    try {
      assetStore.deleteAsset(ASSET_HASH);
    } catch (NotFoundException e) {
      // expected — either the rollback worked (asset still there and we just deleted it)
      // or the test never stored it
    }
  }

  @Test
  void deleteAsset_listenerThrows_rollsBackAndAssetRemainsInDatabase() {
    AssetMetadata asset = buildTestAsset();
    CredentialVerificationResult vr = new CredentialVerificationResult(
        Instant.now(), AssetStatus.ACTIVE.getValue(), TEST_ISSUER, Instant.now(), ASSET_ID,
        List.of(), List.of());
    assetStore.storeCredential(asset, vr);

    doThrow(new RuntimeException("forced listener failure"))
        .when(provenanceService).deleteByAssetId(any());

    assertThrows(RuntimeException.class, () -> assetStore.deleteAsset(ASSET_HASH),
        "deleteAsset must propagate the listener exception");

    AssetMetadata found = assetStore.getById(ASSET_ID);
    assertNotNull(found, "Asset must still exist after the rolled-back transaction");
  }


  private static AssetMetadata buildTestAsset() {
    AssetMetadata asset = new AssetMetadata();
    asset.setId(ASSET_ID);
    asset.setIssuer(TEST_ISSUER);
    asset.setAssetHash(ASSET_HASH);
    asset.setStatus(AssetStatus.ACTIVE);
    asset.setUploadDatetime(Instant.now());
    asset.setStatusDatetime(Instant.now());
    asset.setContentAccessor(new ContentAccessorDirect(ASSET_CONTENT));
    return asset;
  }
}
