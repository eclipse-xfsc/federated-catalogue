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

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests for cascade-delete behaviour in {@link AssetStoreImpl}.
 *
 * <p>When a machine-readable (MR) asset is deleted, its linked human-readable (HR) asset
 * must be deleted too. When a HR asset is deleted directly, the MR asset must survive.</p>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {AssetStoreCascadeDeleteTest.TestConfig.class, AssetStoreImpl.class,
    AssetJpaDao.class, AssetAuditRepository.class, DatabaseConfig.class, SecurityAuditorAware.class,
    DummyGraphStore.class, FileStoreConfig.class, IriGenerator.class,
    ProtectedNamespaceProperties.class, IriValidator.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AssetStoreCascadeDeleteTest {

  private static final String TEST_ISSUER = "did:example:test-issuer";
  private static final String MR_ID = "urn:uuid:mr-asset-cascade-01";

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

  // ===== delete MR asset cascade-deletes HR =====

  @Test
  void deleteAsset_machineReadableWithLinkedHumanReadable_deletesHumanReadableToo() {
    final var mrMeta = storeNonRdfAsset(MR_ID, "mr content");
    final var hrMeta = storeNonRdfAsset(null, "hr content");

    linkAssets(mrMeta.getId(), hrMeta.getId());

    assetStore.deleteAsset(mrMeta.getAssetHash());

    assertThrows(NotFoundException.class, () -> assetStore.getByHash(mrMeta.getAssetHash()),
        "MR asset must be gone after delete");
    assertThrows(NotFoundException.class, () -> assetStore.getByHash(hrMeta.getAssetHash()),
        "HR asset must be cascade-deleted when MR parent is deleted");
  }

  // ===== delete HR asset directly preserves MR =====

  @Test
  void deleteAsset_humanReadableDirectly_preservesMachineReadable() {
    final var mrMeta = storeNonRdfAsset(MR_ID, "mr content v2");
    final var hrMeta = storeNonRdfAsset(null, "hr content v2");

    linkAssets(mrMeta.getId(), hrMeta.getId());

    assetStore.deleteAsset(hrMeta.getAssetHash());

    assertNotNull(assetStore.getByHash(mrMeta.getAssetHash()),
        "MR asset must survive when HR asset is deleted directly");
    assertThrows(NotFoundException.class, () -> assetStore.getByHash(hrMeta.getAssetHash()),
        "HR asset must be gone after direct delete");
    Asset mrEntity = assetRepository.findBySubjectIdWithLinkedAsset(mrMeta.getId()).orElseThrow();
    assertNull(mrEntity.getLinkedAsset(), "linked_asset_id must be nulled by DB after HR is deleted");
  }

  // ===== delete unlinked asset — no cascade side effects =====

  @Test
  void deleteAsset_noLinksExist_deletesSuccessfully() {
    final var meta = storeNonRdfAsset(null, "standalone asset no links");

    assetStore.deleteAsset(meta.getAssetHash());

    assertThrows(NotFoundException.class, () -> assetStore.getByHash(meta.getAssetHash()));
  }

  // ===== helpers =====

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

    return assetStore.storeUnverified(meta, contentText.replaceAll(" ", "_") + ".bin");
  }
}
