package eu.xfsc.fc.core.service.assetstore;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.service.provenance.ProvenanceService;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.AssetType;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.dao.validation.OutdatedReason;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.mutable.MutableInt;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.core.dao.assets.Asset;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * File system based implementation of the asset store interface.
 *
 * @author hylke
 * @author j_reuter
 */
@Slf4j
@Component("assetStore")
@Transactional
@RequiredArgsConstructor
public class AssetStoreImpl implements AssetStore {

  private static final String PREDICATE_HAS_HUMAN_READABLE = "hasHumanReadable";
  private static final String PREDICATE_HAS_MACHINE_READABLE = "hasMachineReadable";

  private final AssetDao dao;
  private final GraphStore graphDb;
  @Qualifier("assetFileStore") private final FileStore fileStore;
  private final IriGenerator iriGenerator;
  private final AssetRepository assetRepository;
  private final ProtectedNamespaceProperties namespaceProperties;
  private final ProvenanceService provenanceService;
  private final ValidationResultStore validationResultStore;

  @Override
  public ContentAccessor getFileByHash(final String hash) {
    AssetRecord meta = (AssetRecord) getByHash(hash);
    return meta.getContentAccessor();
  }

  @Override
  public AssetMetadata getByHash(final String hash) {
    AssetRecord assetRecord = dao.select(hash);
    if (assetRecord == null) {
      throw new NotFoundException(String.format("no asset found for hash %s", hash));
    }
    return assetRecord;
  }

  @Override
  public PaginatedResults<AssetMetadata> getByFilter(final AssetFilter filter, final boolean withMeta, final boolean withContent) {
    log.debug("getByFilter.enter; got filter: {}, withMeta: {}, withContent: {}", filter, withMeta, withContent);
    PaginatedResults<AssetRecord> page = dao.selectByFilter(filter, withMeta, withContent);
    List assetList = page.getResults();
    return new PaginatedResults<>(page.getTotalCount(), (List<AssetMetadata>) assetList);
  }

  @Override
  public void storeCredential(final AssetMetadata assetMetadata, final CredentialVerificationResult verificationResult) {
	storeCredentialInternal(assetMetadata, verificationResult);
  }

  protected SubjectHashRecord storeCredentialInternal(final AssetMetadata assetMetadata, final CredentialVerificationResult verificationResult) {
    if (verificationResult == null) {
      throw new IllegalArgumentException("verification result must not be null");
    }
    if (assetMetadata.getId() == null) {
      throw new IllegalStateException("Asset ID must be resolved before storing credential");
    }
    log.debug("storeCredential.enter; got meta: {}", assetMetadata);

    Instant expirationTime = null;
    final List<Validator> validators = verificationResult.getValidators();
    if (validators != null) {
      Validator minVal = validators.stream().min(new Validator.ExpirationComparator()).orElse(null);
      expirationTime = minVal == null ? null : minVal.getExpirationDate();
    }
    AssetRecord assetRecord = AssetRecord.builder()
        .assetHash(assetMetadata.getAssetHash())
        .id(assetMetadata.getId())
        .status(assetMetadata.getStatus())
        .issuer(assetMetadata.getIssuer())
        .validatorDids(assetMetadata.getValidatorDids())
        .uploadTime(assetMetadata.getUploadDatetime())
        .statusTime(assetMetadata.getStatusDatetime())
        .content(assetMetadata.getContentAccessor())
        .expirationTime(expirationTime)
        .contentType(assetMetadata.getContentType() != null ? assetMetadata.getContentType() : "application/ld+json")
        .changeComment(assetMetadata.getChangeComment())
        .build();

    SubjectHashRecord subjectHash = null;
    try {
      subjectHash = dao.insert(assetRecord);
    } catch (DataIntegrityViolationException ex) {
      if (ex.getMessage().contains("uq_assets_asset_hash")) {
        throw new ConflictException(String.format("asset with id %s already exists (hash: %s)", assetMetadata.getId(), assetMetadata.getAssetHash()));
      }
      if (ex.getMessage().contains("idx_asset_active")) {
        throw new ConflictException(String.format("active asset with id %s already exists", assetMetadata.getId()));
      }
      log.error("storeCredential.error 2", ex);
      throw new ServerException(ex);
    }
    if (subjectHash != null && subjectHash.subjectId() != null) {
      graphDb.deleteClaims(subjectHash.subjectId());
      validationResultStore.markOutdatedByAssetId(subjectHash.subjectId(), OutdatedReason.ASSET_UPDATED);
      // deleteClaims wipes all triples for the asset, including MR-HR link triples; re-write them
      tryRewriteLinkTriples(assetMetadata.getId());
    }
    graphDb.addClaims(verificationResult.getClaims(), assetMetadata.getId());
    return subjectHash;
  }

  @Override
  public AssetMetadata storeUnverified(final AssetMetadata assetMetadata, final String originalFilename) {
    log.debug("storeAsset.enter; got meta: {}", assetMetadata);
    String subjectId = assetMetadata.getId();
    if (subjectId == null) {
      subjectId = iriGenerator.generateUuidUrn();
      assetMetadata.setId(subjectId);
      log.debug("storeAsset; generated IRI for non-RDF asset: {}", subjectId);
    }
    AssetRecord assetRecord = AssetRecord.builder()
        .assetHash(assetMetadata.getAssetHash())
        .id(subjectId)
        .status(assetMetadata.getStatus())
        .issuer(assetMetadata.getIssuer())
        .validatorDids(assetMetadata.getValidatorDids())
        .uploadTime(assetMetadata.getUploadDatetime())
        .statusTime(assetMetadata.getStatusDatetime())
        .contentType(assetMetadata.getContentType())
        .fileSize(assetMetadata.getFileSize())
        .originalFilename(originalFilename)
        .build();
    try {
      dao.insert(assetRecord);
    } catch (DataIntegrityViolationException ex) {
      if (ex.getMessage().contains("uq_assets_asset_hash")) {
        throw new ConflictException(String.format("asset with id %s already exists (hash: %s)", subjectId, assetMetadata.getAssetHash()));
      }
      if (ex.getMessage().contains("idx_asset_active")) {
        throw new ConflictException(String.format("active asset with id %s already exists", subjectId));
      }
      throw new ServerException(ex);
    }
    try {
      fileStore.replaceFile(assetMetadata.getAssetHash(), assetMetadata.getContentAccessor());
    } catch (IOException ex) {
      throw new ServerException("Failed to store asset content in file store", ex);
    }
    log.debug("storeAsset.exit; stored asset with hash: {}", assetMetadata.getAssetHash());
    return assetRecord;
  }

  @Override
  public void changeLifeCycleStatus(final String hash, final AssetStatus targetStatus) {
	SubjectStatusRecord ssr = dao.update(hash, targetStatus.ordinal());
    log.debug("changeLifeCycleStatus; update result: {}", ssr);
    if (ssr == null) {
      throw new NotFoundException("no asset found for hash " + hash);
    }

    if (ssr.subjectId() == null) {
      throw new ConflictException(String.format("can not change status of asset with hash %s: require status %s, but encountered status %s",
    	hash, AssetStatus.ACTIVE, ssr.getAssetStatus()));
    }
    graphDb.deleteClaims(ssr.subjectId());
    validationResultStore.markOutdatedByAssetId(ssr.subjectId(), OutdatedReason.ASSET_REVOKED);
  }

  @Override
  public void deleteAsset(final String hash) {
    deleteAsset(hash, false);
  }

  private void deleteAsset(final String hash, final boolean cascading) {
    final Optional<Asset> assetOpt = assetRepository.findByAssetHashWithLinkedAsset(hash);
    // Only cascade from MR → HR once; a cascading call never triggers a further cascade.
    final String hrHashToCascade = cascading ? null : assetOpt.map(this::findHumanReadableAssetHash).orElse(null);

    assetOpt.filter(a -> a.getLinkedAsset() != null).ifPresent(a -> {
      final Asset peer = a.getLinkedAsset();
      peer.setLinkedAsset(null);
      peer.setAssetType(null);
      assetRepository.save(peer);
    });

    SubjectStatusRecord ssr = dao.delete(hash);
    log.debug("deleteAsset; delete result: {}", ssr);
    if (ssr == null) {
      throw new NotFoundException("no asset found for hash " + hash);
    }

    provenanceService.deleteByAssetId(ssr.subjectId());
    validationResultStore.deleteByAssetId(ssr.subjectId());

    if (ssr.getAssetStatus() == AssetStatus.ACTIVE) {
      graphDb.deleteClaims(ssr.subjectId());
    }
    try {
      fileStore.deleteFile(hash);
    } catch (IOException ex) {
      log.debug("deleteAsset; file store cleanup skipped for hash {}: {}", hash, ex.getMessage());
    }

    if (hrHashToCascade != null) {
      try {
        deleteAsset(hrHashToCascade, true);
      } catch (NotFoundException ex) {
        log.debug("deleteAsset; HR asset already gone, skipping cascade");
      }
    }
  }

  @Override
  public int invalidateExpiredAssets() {
    // A possible Performance optimisation may be required here to limit the number
    // of assets that are expired in one run, to limit the size of the Transaction.
    List<String> expiredAssets = dao.selectExpiredHashes();
    final MutableInt count = new MutableInt();
    // we could also expire/update all assets from batch in one batchUpdate..
    expiredAssets.forEach(assetHash -> {
      try {
        changeLifeCycleStatus(assetHash, AssetStatus.EOL);
        count.increment();
      } catch (ConflictException exc) {
        log.info("invalidateExpiredAssets; asset was set non-active before we could expire it. Hash: {}", assetHash);
      }
    });
    return count.intValue();
  }

  @Override
  public List<String> getActiveAssetHashes(String afterHash, int count, int chunks, int chunkId) {
    return dao.selectHashes(afterHash, count, chunks, chunkId);
  }

  @Override
  public AssetMetadata getById(final String id) {
    return dao.selectBySubjectId(id)
        .orElseThrow(() -> new NotFoundException(
            String.format("no active asset found for id %s", id)));
  }

  @Override
  public boolean existsById(final String id) {
    return dao.selectBySubjectId(id).isPresent();
  }

  @Override
  public ContentAccessor getFileById(final String id) {
    AssetRecord record = (AssetRecord) getById(id);
    return record.getContentAccessor();
  }

  @Override
  public void clear() {
	int cnt = dao.deleteAll();
    log.debug("clear; deleted {} assets", cnt);
  }

  @Override
  @Transactional(readOnly = true)
  public PaginatedResults<AssetRecord> getVersionHistoryPage(String id, int page, int size) {
    PaginatedResults<AssetRecord> result = dao.selectVersionsPageWithTotal(id, page, size);
    if (result.getTotalCount() == 0) {
      throw new NotFoundException("no asset found for id %s".formatted(id));
    }
    return result;
  }

  @Override
  @Transactional(readOnly = true)
  public AssetRecord getByIdAndVersion(String id, int version) {
    return dao.selectVersion(id, version)
        .orElseThrow(() -> new NotFoundException("no asset found for id %s at version %d".formatted(id, version)));
  }

  @Override
  @Transactional(readOnly = true)
  public int getVersionCount(String id) {
    int count = dao.getVersionCount(id);
    if (count == 0) {
      throw new NotFoundException("no asset found for id %s".formatted(id));
    }
    return count;
  }

  @Override
  public void linkAssets(String mrIri, String hrIri) {
    Asset mrAsset = assetRepository.findBySubjectId(mrIri)
        .orElseThrow(() -> new NotFoundException(String.format("no active asset found for id %s", mrIri)));
    Asset hrAsset = assetRepository.findBySubjectId(hrIri)
        .orElseThrow(() -> new NotFoundException(String.format("no active asset found for id %s", hrIri)));
    mrAsset.setAssetType(AssetType.MACHINE_READABLE);
    mrAsset.setLinkedAsset(hrAsset);
    hrAsset.setAssetType(AssetType.HUMAN_READABLE);
    hrAsset.setLinkedAsset(mrAsset);
    assetRepository.saveAll(List.of(mrAsset, hrAsset));
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<LinkedAssetRef> findLink(String assetIri) {
    return assetRepository.findBySubjectIdWithLinkedAsset(assetIri)
        .filter(a -> a.getLinkedAsset() != null)
        .map(a -> new LinkedAssetRef(a.getLinkedAsset().getSubjectId(), a.getAssetType()));
  }

  @Override
  public void writeAssetLinkTriples(String mrIri, String hrIri) {
    writeLinkTriples(mrIri, hrIri);
  }

  private void tryRewriteLinkTriples(String assetId) {
    try {
      assetRepository.findBySubjectIdWithLinkedAsset(assetId)
          .filter(a -> a.getLinkedAsset() != null && a.getAssetType() == AssetType.MACHINE_READABLE)
          .ifPresent(a -> writeAssetLinkTriples(assetId, a.getLinkedAsset().getSubjectId()));
    } catch (Exception ex) {
      log.warn("tryRewriteLinkTriples; failed to restore link triples after graph rebuild for asset {}", assetId, ex);
    }
  }

  private String findHumanReadableAssetHash(Asset asset) {
    if (asset.getAssetType() != AssetType.MACHINE_READABLE || asset.getLinkedAsset() == null) {
      return null;
    }
    return asset.getLinkedAsset().getAssetHash();
  }

  private void writeLinkTriples(String mrIri, String hrIri) {
    final var ns = namespaceProperties.getNamespace();
    final var hasHumanReadable = new CredentialClaim(
        "<" + mrIri + ">",
        "<" + ns + PREDICATE_HAS_HUMAN_READABLE + ">",
        "<" + hrIri + ">");
    final var hasMachineReadable = new CredentialClaim(
        "<" + hrIri + ">",
        "<" + ns + PREDICATE_HAS_MACHINE_READABLE + ">",
        "<" + mrIri + ">");
    graphDb.addClaims(List.of(hasHumanReadable), mrIri);
    graphDb.addClaims(List.of(hasMachineReadable), hrIri);
  }

}
