package eu.xfsc.fc.core.dao.assets;

import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.core.pojo.AssetFilter;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.service.assetstore.AssetRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectHashRecord;
import eu.xfsc.fc.core.service.assetstore.SubjectStatusRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class AssetJpaDao implements AssetDao {

    private static final short ACTIVE_STATUS = (short) AssetStatus.ACTIVE.ordinal();

    private final AssetRepository repository;
    private final AssetAuditRepository auditRepository;

    @Override
    public Optional<AssetRecord> selectBySubjectId(String subjectId) {
        return repository.findBySubjectIdAndStatus(subjectId, ACTIVE_STATUS)
                .map(AssetMapper::toRecord);
    }

    @Override
    public AssetRecord select(String hash) {
        return repository.findByAssetHash(hash)
                .map(AssetMapper::toRecord)
                .orElse(null);
    }

    @Override
    public PaginatedResults<AssetRecord> selectByFilter(AssetFilter filter,
                                                        boolean withMeta,
                                                        boolean withContent) {
        return repository.selectByFilter(filter, withMeta, withContent);
    }

    @Override
    public List<String> selectHashes(String startHash, int count, int chunks, int chunkId) {
        int status = AssetStatus.ACTIVE.ordinal();
        if (startHash == null) {
            return repository.findHashesFirstPage(status, chunks, chunkId, count);
        }
        return repository.findHashesAfter(startHash, status, chunks, chunkId, count);
    }

    @Override
    public List<String> selectExpiredHashes() {
        return repository.findExpiredHashes(AssetStatus.ACTIVE.ordinal());
    }

    // Explicit duplicate check needed because JPA's save() uses merge() for entities
    // with non-null @Id, which silently upserts instead of throwing on conflicts.
    // AssetStoreImpl relies on DuplicateKeyException("uq_assets_asset_hash") to detect duplicates.
    @Override
    @Transactional
    public SubjectHashRecord insert(AssetRecord assetRecord) {
        if (repository.existsByAssetHash(assetRecord.getAssetHash())) {
            throw new DuplicateKeyException("uq_assets_asset_hash: " + assetRecord.getAssetHash());
        }

        Optional<Asset> existing = repository.findBySubjectIdAndStatus(
                assetRecord.getId(), ACTIVE_STATUS);

        if (existing.isPresent()) {
            // Update existing row in-place. Envers intercepts the flush and records
            // the pre-update state as a new revision in assets_aud automatically.
            Asset old = existing.get();
            String previousHash = old.getAssetHash();
            old.setAssetHash(assetRecord.getAssetHash());
            old.setContent(assetRecord.getContent());
            old.setChangeComment(assetRecord.getChangeComment());
            old.setUploadTime(assetRecord.getUploadDatetime());
            old.setStatusTime(assetRecord.getStatusDatetime());
            old.setIssuer(assetRecord.getIssuer());
            old.setExpirationTime(assetRecord.getExpirationTime());
            old.setValidators(assetRecord.getValidators());
            old.setContentType(assetRecord.getContentType());
            old.setFileSize(assetRecord.getFileSize());
            old.setOriginalFilename(assetRecord.getOriginalFilename());
            old.setStatus(ACTIVE_STATUS);
            repository.saveAndFlush(old);
            return new SubjectHashRecord(old.getSubjectId(), previousHash);
        }

        Asset newEntity = AssetMapper.toEntity(assetRecord);
        repository.save(newEntity);

        return new SubjectHashRecord(null, null);
    }

    @Override
    public SubjectStatusRecord update(String hash, int status) {
        Asset entity = repository.findByAssetHash(hash)
                .orElseThrow(() -> new EmptyResultDataAccessException(1));

        if (entity.getStatus() == ACTIVE_STATUS) {
            entity.setStatus((short) status);
            entity.setStatusTime(Instant.now());
            repository.save(entity);
            return new SubjectStatusRecord(entity.getSubjectId(), null);
        }
        return new SubjectStatusRecord(null, (int) entity.getStatus());
    }

    @Override
    public SubjectStatusRecord delete(String hash) {
        Optional<Asset> existing = repository.findByAssetHash(hash);
        if (existing.isEmpty()) {
            return null;
        }
        Asset entity = existing.get();
        repository.delete(entity);
        return new SubjectStatusRecord(entity.getSubjectId(), (int) entity.getStatus());
    }

    @Override
    @Transactional
    public int deleteAll() {
        return repository.deleteAllReturningCount();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AssetRecord> selectVersions(String subjectId) {
        return repository.findBySubjectId(subjectId)
            .map(entity -> auditRepository.findAllVersions(entity.getId()))
            .orElse(List.of());
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResults<AssetRecord> selectVersionsPageWithTotal(String subjectId, int page, int size) {
        return repository.findBySubjectId(subjectId)
            .map(entity -> auditRepository.findVersionsPageWithTotal(entity.getId(), page, size))
            .orElse(new PaginatedResults<>(0, List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AssetRecord> selectVersion(String subjectId, int version) {
        return repository.findBySubjectId(subjectId)
            .flatMap(entity -> auditRepository.findVersion(entity.getId(), version));
    }

    @Override
    @Transactional(readOnly = true)
    public int getVersionCount(String subjectId) {
        return repository.findBySubjectId(subjectId)
            .map(entity -> auditRepository.countVersions(entity.getId()))
            .orElse(0);
    }
}
