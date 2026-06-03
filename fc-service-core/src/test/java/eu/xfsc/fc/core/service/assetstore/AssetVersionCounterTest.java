package eu.xfsc.fc.core.service.assetstore;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.assets.Asset;
import eu.xfsc.fc.core.dao.assets.AssetAuditRepository;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.AssetType;
import eu.xfsc.fc.core.pojo.ContentAccessorBinary;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
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

/**
 * Verifies that the asset version counter advances only on content changes, never on attachment
 * of sub-resources or non-content metadata mutations. Mirrors the SRS rule that linking a
 * provenance credential or human-readable companion must not produce a new asset version.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {AssetVersionCounterTest.TestConfig.class, AssetStoreImpl.class,
    AssetJpaDao.class, AssetAuditRepository.class, DatabaseConfig.class, SecurityAuditorAware.class,
    DummyGraphStore.class, FileStoreConfig.class, IriGenerator.class,
    ProtectedNamespaceProperties.class, IriValidator.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
class AssetVersionCounterTest {

  private static final String TEST_ISSUER = "did:example:version-counter-issuer";

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
  void getVersionCount_afterLinkingHumanReadableCompanion_doesNotAdvance() {
    final var mrMeta = storeNonRdfAsset("urn:uuid:version-counter-mr", "mr v1 bytes");
    final var hrMeta = storeNonRdfAsset(null, "hr companion bytes");

    final int countBeforeLink = assetStore.getVersionCount(mrMeta.getId());

    linkAssets(mrMeta.getId(), hrMeta.getId());

    final int countAfterLink = assetStore.getVersionCount(mrMeta.getId());
    assertEquals(1, countBeforeLink,
        "Initial upload of a single machine-VC must register exactly one content version");
    assertEquals(countBeforeLink, countAfterLink,
        "Attaching a human-readable companion must not advance the machine-VC version counter");
  }

  @Test
  void getVersionCount_afterRepeatedLinkingMutations_remainsAtOne() {
    final var mrMeta = storeNonRdfAsset("urn:uuid:version-counter-mr-2", "mr single content");
    final var hrMeta = storeNonRdfAsset(null, "hr single content");

    linkAssets(mrMeta.getId(), hrMeta.getId());
    unlinkPeer(mrMeta.getId());
    linkAssets(mrMeta.getId(), hrMeta.getId());

    final int finalCount = assetStore.getVersionCount(mrMeta.getId());
    assertEquals(1, finalCount,
        "Repeated link/unlink mutations must not advance the machine-VC version counter");
  }

  @Test
  void getVersionCount_afterMachineReadableUpdate_advancesVersionCount() {
    final String assetId = "urn:uuid:version-counter-mr-update";
    final var v1Meta = storeNonRdfAsset(assetId, "mr v1 content");

    final int countAfterV1 = assetStore.getVersionCount(assetId);
    assertEquals(1, countAfterV1,
        "Initial upload must register exactly one content version");

    final var v2Meta = storeNonRdfAsset(assetId, "mr v2 content");

    final int countAfterV2 = assetStore.getVersionCount(assetId);
    final AssetRecord latestVersion = assetStore.getByIdAndVersion(assetId, countAfterV2);

    assertEquals(2, countAfterV2,
        "Updating asset content with different hash must advance the version counter");
    assertEquals(v2Meta.getAssetHash(), latestVersion.getAssetHash(),
        "Latest version must reflect the new content hash");
  }

  private void linkAssets(String mrId, String hrId) {
    Asset mrEntity = assetRepository.findBySubjectIdWithLinkedAsset(mrId).orElseThrow();
    Asset hrEntity = assetRepository.findBySubjectIdWithLinkedAsset(hrId).orElseThrow();
    mrEntity.setLinkedAsset(hrEntity);
    mrEntity.setAssetType(AssetType.MACHINE_READABLE);
    assetRepository.save(mrEntity);
  }

  private void unlinkPeer(String mrId) {
    Asset mrEntity = assetRepository.findBySubjectIdWithLinkedAsset(mrId).orElseThrow();
    mrEntity.setLinkedAsset(null);
    mrEntity.setAssetType(null);
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
