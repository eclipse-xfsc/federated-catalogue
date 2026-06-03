package eu.xfsc.fc.core.service.assetstore;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.assets.Asset;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.AssetType;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.service.graphdb.DummyGraphStore;
import eu.xfsc.fc.core.service.provenance.ProvenanceService;
import eu.xfsc.fc.core.util.HashUtils;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for the idempotent cascade-by-asset-id delete entry point on the asset store.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {AssetStoreDeleteByIdTest.TestConfig.class, AssetStoreImpl.class,
    AssetJpaDao.class, AssetAuditRepository.class, DatabaseConfig.class, SecurityAuditorAware.class,
    DummyGraphStore.class, FileStoreConfig.class, IriGenerator.class,
    ProtectedNamespaceProperties.class, IriValidator.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AssetStoreDeleteByIdTest {

  private static final String TEST_ISSUER = "did:example:delete-by-id-issuer";

  @Configuration
  @EnableAutoConfiguration
  static class TestConfig {
  }

  @MockitoBean
  private ProvenanceService provenanceService;

  @Autowired
  private AssetStore assetStore;

  @Autowired
  private AssetRepository assetRepository;

  @AfterEach
  void cleanUp() {
    assetStore.clear();
  }

  @Test
  void deleteByAssetId_existingAsset_returnsOneAndDropsLiveRow() {
    final var meta = storeNonRdfAsset("urn:uuid:delete-by-id-single", "single content");

    final int removed = assetStore.deleteByAssetId(meta.getId());

    assertEquals(1, removed);
    assertThrows(NotFoundException.class, () -> assetStore.getByHash(meta.getAssetHash()));
  }

  @Test
  void deleteByAssetId_unknownId_returnsZero() {
    final int removed = assetStore.deleteByAssetId("urn:uuid:not-present");

    assertEquals(0, removed,
        "Delete-by-id must be idempotent: unknown identifier resolves to zero deletions");
  }

  @Test
  void deleteByAssetId_secondInvocation_isIdempotent() {
    final var meta = storeNonRdfAsset("urn:uuid:delete-by-id-twice", "double-call content");

    assertEquals(1, assetStore.deleteByAssetId(meta.getId()));
    assertEquals(0, assetStore.deleteByAssetId(meta.getId()),
        "Repeated delete-by-id calls on the same identifier must return zero after the first");
  }

  @Test
  void deleteByAssetId_assetWithHumanReadableCompanion_cascadesToCompanion() {
    final var mrMeta = storeNonRdfAsset("urn:uuid:delete-by-id-cascade", "mr content");
    final var hrMeta = storeNonRdfAsset(null, "hr companion");
    linkAssets(mrMeta.getId(), hrMeta.getId());

    final int removed = assetStore.deleteByAssetId(mrMeta.getId());

    assertEquals(1, removed);
    assertThrows(NotFoundException.class, () -> assetStore.getByHash(mrMeta.getAssetHash()));
    assertThrows(NotFoundException.class, () -> assetStore.getByHash(hrMeta.getAssetHash()),
        "Linked human-readable companion must be cascade-deleted along with the machine-VC");
  }

  private void linkAssets(String mrId, String hrId) {
    Asset mrEntity = assetRepository.findBySubjectIdWithLinkedAsset(mrId).orElseThrow();
    Asset hrEntity = assetRepository.findBySubjectIdWithLinkedAsset(hrId).orElseThrow();
    mrEntity.setLinkedAsset(hrEntity);
    mrEntity.setAssetType(AssetType.MACHINE_READABLE);
    assetRepository.save(mrEntity);
  }

  private AssetMetadata storeNonRdfAsset(String id, String contentText) {
    final var content = contentText.getBytes(StandardCharsets.UTF_8);
    final var hash = HashUtils.calculateSha256AsHex(content);
    final var now = Instant.now();
    final var contentAccessor = new ContentAccessorBinary(content);

    final var meta = new AssetMetadata(hash, id, AssetStatus.ACTIVE,
        TEST_ISSUER, null, now, now, contentAccessor);
    meta.setContentType("application/octet-stream");
    meta.setFileSize((long) content.length);

    return assetStore.storeUnverified(meta, contentText.replace(" ", "_") + ".bin");
  }
}
