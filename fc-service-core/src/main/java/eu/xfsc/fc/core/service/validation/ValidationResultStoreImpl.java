package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.core.dao.validation.GraphSyncStatus;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidationResultRepository;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements {@link ValidationResultStore} and provides
 * validation result persistence with graph DB sync.
 *
 * <p>Write sequence: PostgreSQL INSERT + graph DB write both happen inside the {@code @Transactional}
 * boundary; PostgreSQL commits only when {@code store()} returns. If PostgreSQL fails to commit after a
 * successful graph write, graph triples will exist without a corresponding PostgreSQL row.
 * If the graph write itself fails, the row is marked
 * {@code FAILED} and no retry is attempted. PostgreSQL is the system of record.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationResultStoreImpl implements ValidationResultStore {

  private final ValidationResultRepository repository;
  private final GraphStore graphStore;
  private final ValidationResultGraphWriter graphWriter;
  private final ValidationResultHasher hasher;

  /**
   * {@inheritDoc}
   *
   * <p>Persists the result to PostgreSQL (with tamper-proof hash), then attempts a
   * graph DB write (best-effort). The PostgreSQL row is the source of truth; a failed graph
   * write marks the row {@code FAILED} and is not retried.</p>
   */
  @Override
  @Transactional
  public Long store(ValidationResultRecord record) {
    ValidationResult entity = buildEntity(record);
    entity.setContentHash(hasher.hash(entity));
    ValidationResult saved = repository.save(entity);
    log.debug("store; saved ValidationResult id={}, conforms={}", saved.getId(), saved.isConforms());

    // Note: the graph write happens while the @Transactional method is still open.
    // PostgreSQL commits when store() returns. Best-effort: graph write failure marks row FAILED; no retry.
    tryWriteToGraph(saved);

    return saved.getId();
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ValidationResult> getByAssetId(String assetId, Pageable pageable) {
    return repository.findByAssetId(assetId, pageable);
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<ValidationResult> getById(Long id) {
    return repository.findById(id);
  }

  @Override
  @Transactional(readOnly = true)
  public Page<ValidationResult> findAll(Pageable pageable) {
    return repository.findAll(pageable);
  }

  @Override
  @Transactional
  public void syncToGraph(ValidationResult result, GraphStore graphStore) {
    try {
      graphWriter.write(result, graphStore);
      result.setGraphSyncStatus(GraphSyncStatus.SYNCED);
      repository.saveAndFlush(result);
    } catch (Exception e) {
      log.error("syncToGraph; graph write failed for result id={}, marking FAILED", result.getId(), e);
      result.setGraphSyncStatus(GraphSyncStatus.FAILED);
      repository.saveAndFlush(result);
    }
  }

  private ValidationResult buildEntity(ValidationResultRecord record) {
    ValidationResult entity = new ValidationResult();
    entity.setAssetIds(record.assetIds().toArray(String[]::new));
    entity.setValidatorIds(record.validatorIds().toArray(String[]::new));
    entity.setValidatorType(record.validatorType());
    entity.setConforms(record.conforms());
    entity.setValidatedAt(record.validatedAt());
    entity.setReport(record.report());
    return entity;
  }

  private void tryWriteToGraph(ValidationResult saved) {
    try {
      graphWriter.write(saved, graphStore);
      saved.setGraphSyncStatus(GraphSyncStatus.SYNCED);
      repository.save(saved);
    } catch (Exception e) {
      log.error("store; graph DB write failed for result id={}, marking FAILED (no retry)",
          saved.getId(), e);
      saved.setGraphSyncStatus(GraphSyncStatus.FAILED);
      repository.save(saved);
    }
  }
}
