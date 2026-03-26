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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AssetJpaDao implements AssetDao {

    private static final short ACTIVE_STATUS = (short) AssetStatus.ACTIVE.ordinal();

    private final AssetRepository repository;

    @Override
    public Optional<AssetRecord> selectBySubjectId(String subjectId) {
        return repository.findBySubjectIdAndStatus(subjectId, ACTIVE_STATUS)
                .map(AssetMapper::toRecord);
    }

    @Override
    public AssetRecord select(String hash) {
        return repository.findById(hash)
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
    // AssetStoreImpl relies on DuplicateKeyException("assets_pkey") to detect duplicates.
    @Override
    @Transactional
    public SubjectHashRecord insert(AssetRecord assetRecord) {
        if (repository.existsById(assetRecord.getAssetHash())) {
            throw new DuplicateKeyException("assets_pkey: " + assetRecord.getAssetHash());
        }

        Optional<Asset> existing = repository.findBySubjectIdAndStatus(
                assetRecord.getId(), ACTIVE_STATUS);

        String oldSubjectId = null;
        String oldHash = null;
        if (existing.isPresent()) {
            Asset old = existing.get();
            oldSubjectId = old.getSubjectId();
            oldHash = old.getAssetHash();
            old.setStatus((short) AssetStatus.DEPRECATED.ordinal());
            old.setStatusTime(Instant.now());
            repository.saveAndFlush(old);
        }

        Asset newEntity = AssetMapper.toEntity(assetRecord);
        repository.save(newEntity);

        return new SubjectHashRecord(oldSubjectId, oldHash);
    }

    @Override
    public SubjectStatusRecord update(String hash, int status) {
        Asset entity = repository.findById(hash)
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
        Optional<Asset> existing = repository.findById(hash);
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
}
