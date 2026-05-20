package eu.xfsc.fc.core.service.trustframework.compliance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.service.validation.ValidationResultRecord;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

/**
 * Unit tests for {@link ComplianceResultStoreImpl}.
 * Pure Mockito — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class ComplianceResultStoreImplTest {

  @Mock
  private ValidationResultStore validationResultStore;

  private ComplianceResultStoreImpl subject;

  @BeforeEach
  void setUp() {
    subject = new ComplianceResultStoreImpl(validationResultStore, new ObjectMapper());
  }

  @Test
  void store_issuedAttestation_delegatesWithConformsTrueAndTrustFrameworkType() {
    var outcome = new IssuedAttestation("{\"vc\":\"jwt\"}", Instant.parse("2025-12-31T00:00:00Z"));
    when(validationResultStore.store(any())).thenReturn(42L);

    Long id = subject.store("asset:1", "gaia-x-2511", "gaia-x", outcome);

    assertEquals(42L, id);
    ArgumentCaptor<ValidationResultRecord> captor = forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());
    ValidationResultRecord record = captor.getValue();
    assertEquals(List.of("asset:1"), record.assetIds());
    assertEquals(List.of("gaia-x-2511", "gaia-x"), record.validatorIds());
    assertEquals(ValidatorType.TRUST_FRAMEWORK, record.validatorType());
    assertTrue(record.conforms());
    assertNotNull(record.validatedAt());
    assertNotNull(record.report());
    assertTrue(record.report().contains("attestationCredential"));
  }

  @Test
  void store_unverifiableAttestation_delegatesWithConformsFalseAndFailureCategory() {
    var outcome = new UnverifiableAttestation(
        FailureCategory.UNVERIFIABLE_ATTESTATION,
        "{\"raw\":\"jwt\"}",
        "Signature verification failed"
    );
    when(validationResultStore.store(any())).thenReturn(99L);

    Long id = subject.store("asset:2", "gaia-x-2511", "gaia-x", outcome);

    assertEquals(99L, id);
    ArgumentCaptor<ValidationResultRecord> captor = forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());
    ValidationResultRecord record = captor.getValue();
    assertEquals(ValidatorType.TRUST_FRAMEWORK, record.validatorType());
    assertFalse(record.conforms());
    assertNotNull(record.report());
    assertTrue(record.report().contains("UNVERIFIABLE_ATTESTATION"));
  }

  @Test
  void store_unverifiableAttestation_rawAttestationAtSizeLimit_storedVerbatim() {
    String atLimit = "x".repeat(65_536);
    var outcome = new UnverifiableAttestation(
        FailureCategory.UNVERIFIABLE_ATTESTATION, atLimit, null);
    when(validationResultStore.store(any())).thenReturn(1L);

    subject.store("asset:3", "gaia-x-2511", "gaia-x", outcome);

    ArgumentCaptor<ValidationResultRecord> captor = forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());
    String report = captor.getValue().report();
    assertFalse(report.contains("[TRUNCATED]"));
    assertTrue(report.contains(atLimit));
  }

  @Test
  void store_unverifiableAttestation_rawAttestationExceedsLimit_truncatedWithMarker() {
    String oversized = "x".repeat(65_537);
    var outcome = new UnverifiableAttestation(
        FailureCategory.UNVERIFIABLE_ATTESTATION, oversized, null);
    when(validationResultStore.store(any())).thenReturn(1L);

    subject.store("asset:4", "gaia-x-2511", "gaia-x", outcome);

    ArgumentCaptor<ValidationResultRecord> captor = forClass(ValidationResultRecord.class);
    verify(validationResultStore).store(captor.capture());
    String report = captor.getValue().report();
    assertTrue(report.contains("...[TRUNCATED]"));
    assertFalse(report.contains(oversized));
  }

  @Test
  void findByAssetId_delegatesToValidationResultStore() {
    var pageable = PageRequest.of(0, 10);
    Page<ValidationResult> expected = new PageImpl<>(List.of());
    String value = "asset:1";
    when(validationResultStore.getByAssetId(eq(value), eq(pageable))).thenReturn(expected);

    Page<ValidationResult> result = subject.findByAssetId(value, pageable);

    assertEquals(expected, result);
    verify(validationResultStore).getByAssetId(value, pageable);
  }
}
