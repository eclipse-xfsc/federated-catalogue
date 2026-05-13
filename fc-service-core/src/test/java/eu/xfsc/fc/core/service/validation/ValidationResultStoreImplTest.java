package eu.xfsc.fc.core.service.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import eu.xfsc.fc.core.dao.validation.GraphSyncStatus;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidationResultRepository;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ValidationResultStoreImplTest {

  @Mock
  private ValidationResultRepository repository;
  @Mock
  private GraphStore graphStore;
  @Mock
  private ValidationResultGraphWriter graphWriter;
  @Mock
  private ValidationResultHasher hasher;

  @InjectMocks
  private ValidationResultStoreImpl service;

  // ===== store — graph write succeeds =====

  @Test
  void store_graphWriteSucceeds_statusTransitionsToSynced() {
    ValidationResultRecord record = buildRecord(true);
    ValidationResult saved = buildEntityWithId(1L);
    when(hasher.hash(any())).thenReturn("aabbcc");
    when(repository.save(any())).thenReturn(saved);

    service.store(record);

    ArgumentCaptor<ValidationResult> captor = ArgumentCaptor.forClass(ValidationResult.class);
    // Called twice: first save (PostgreSQL commit), second save (status update to SYNCED)
    verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
    List<ValidationResult> savedEntities = captor.getAllValues();
    assertEquals(GraphSyncStatus.SYNCED,
        savedEntities.get(1).getGraphSyncStatus());
  }

  @Test
  void store_graphWriteSucceeds_returnsId() {
    ValidationResultRecord record = buildRecord(false);
    ValidationResult saved = buildEntityWithId(99L);
    when(hasher.hash(any())).thenReturn("hash");
    when(repository.save(any())).thenReturn(saved);

    Long id = service.store(record);

    assertEquals(99L, id);
  }

  // ===== store — graph write fails =====

  @Test
  void store_graphWriteFails_statusMarkedFailed() {
    ValidationResultRecord record = buildRecord(true);
    ValidationResult saved = buildEntityWithId(2L);
    when(hasher.hash(any())).thenReturn("aabbcc");
    when(repository.save(any())).thenReturn(saved);
    doThrow(new RuntimeException("graph unavailable"))
        .when(graphWriter).write(any(), any());

    service.store(record);

    // Two saves: initial PG commit + FAILED status update
    verify(repository, org.mockito.Mockito.times(2)).save(any());
    assertEquals(GraphSyncStatus.FAILED, saved.getGraphSyncStatus());
  }

  // ===== getByAssetId =====

  @Test
  void getByAssetId_delegatesWithPageable() {
    ValidationResult entity = buildEntityWithId(5L);
    Pageable pageable = PageRequest.of(0, 10);
    Page<ValidationResult> page = new PageImpl<>(List.of(entity));
    when(repository.findByAssetId(eq("https://example.org/asset/1"), eq(pageable)))
        .thenReturn(page);

    Page<ValidationResult> result = service.getByAssetId("https://example.org/asset/1", pageable);

    assertEquals(1, result.getTotalElements());
    assertEquals(5L, result.getContent().get(0).getId());
  }

  // ===== getById =====

  @Test
  void getById_existingId_returnsPopulatedOptional() {
    ValidationResult entity = buildEntityWithId(10L);
    when(repository.findById(10L)).thenReturn(Optional.of(entity));

    Optional<ValidationResult> result = service.getById(10L);

    assertTrue(result.isPresent());
    assertEquals(10L, result.get().getId());
  }

  @Test
  void getById_unknownId_returnsEmptyOptional() {
    when(repository.findById(999L)).thenReturn(Optional.empty());

    Optional<ValidationResult> result = service.getById(999L);

    assertFalse(result.isPresent());
  }

  // ===== deleteByAssetId =====

  @Test
  void deleteByAssetId_existingResults_deletesGraphAndPostgresRows() {
    ValidationResult result1 = buildEntityWithId(10L);
    ValidationResult result2 = buildEntityWithId(11L);
    when(repository.findAllByAssetId("https://example.org/asset/1"))
        .thenReturn(List.of(result1, result2));
    when(graphWriter.resultIri(10L)).thenReturn("https://fc.example.org/meta/ValidationResult/10");
    when(graphWriter.resultIri(11L)).thenReturn("https://fc.example.org/meta/ValidationResult/11");

    service.deleteByAssetId("https://example.org/asset/1");

    verify(graphStore).deleteValidationResultClaims("https://fc.example.org/meta/ValidationResult/10");
    verify(graphStore).deleteValidationResultClaims("https://fc.example.org/meta/ValidationResult/11");
    verify(repository).deleteAllByAssetId("https://example.org/asset/1");
  }

  @Test
  void deleteByAssetId_noResults_performsNoop() {
    when(repository.findAllByAssetId("https://example.org/asset/unknown"))
        .thenReturn(List.of());

    service.deleteByAssetId("https://example.org/asset/unknown");

    verify(repository).deleteAllByAssetId("https://example.org/asset/unknown");
    org.mockito.Mockito.verifyNoInteractions(graphStore);
  }

  @Test
  void deleteByAssetId_graphThrows_deletesDbRowsAnyway() {
    ValidationResult result = buildEntityWithId(20L);
    when(repository.findAllByAssetId("https://example.org/asset/1"))
        .thenReturn(List.of(result));
    when(graphWriter.resultIri(20L)).thenReturn("https://fc.example.org/meta/ValidationResult/20");
    doThrow(new RuntimeException("graph unavailable"))
        .when(graphStore).deleteValidationResultClaims(any());

    service.deleteByAssetId("https://example.org/asset/1");

    verify(repository).deleteAllByAssetId("https://example.org/asset/1");
  }


  private static ValidationResultRecord buildRecord(boolean conforms) {
    return new ValidationResultRecord(
        List.of("https://example.org/asset/1"),
        List.of("https://example.org/schema/1"),
        ValidatorType.SHACL,
        conforms,
        Instant.parse("2024-06-01T12:00:00Z"),
        null);
  }

  private static ValidationResult buildEntityWithId(long id) {
    ValidationResult e = new ValidationResult();
    e.setAssetIds(new String[]{"https://example.org/asset/1"});
    e.setValidatorIds(new String[]{"https://example.org/schema/1"});
    e.setValidatorType(ValidatorType.SHACL);
    e.setConforms(true);
    e.setValidatedAt(Instant.parse("2024-06-01T12:00:00Z"));
    e.setId(id);
    return e;
  }
}
