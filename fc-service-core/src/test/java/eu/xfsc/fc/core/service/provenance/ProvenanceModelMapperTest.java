package eu.xfsc.fc.core.service.provenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.ProvenanceCredential;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult;
import eu.xfsc.fc.core.dao.provenance.ProvenanceRecord;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import java.time.Instant;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ProvenanceModelMapperTest {

  private ProvenanceModelMapper mapper;

  @BeforeEach
  void setUp() {
    mapper = new ProvenanceModelMapper(new ObjectMapper().findAndRegisterModules());
  }

  static Stream<Arguments> provenanceTypeMappings() {
    return Stream.of(
        Arguments.of(ProvenanceType.CREATION, ProvenanceCredential.ProvenanceTypeEnum.CREATION),
        Arguments.of(ProvenanceType.DERIVATION, ProvenanceCredential.ProvenanceTypeEnum.DERIVATION),
        Arguments.of(ProvenanceType.ATTRIBUTION, ProvenanceCredential.ProvenanceTypeEnum.ATTRIBUTION),
        Arguments.of(ProvenanceType.MODIFICATION, ProvenanceCredential.ProvenanceTypeEnum.MODIFICATION)
    );
  }

  @ParameterizedTest
  @MethodSource("provenanceTypeMappings")
  void toModel_provenanceType_mapsToCorrectEnum(
      ProvenanceType type, ProvenanceCredential.ProvenanceTypeEnum expected) {
    ProvenanceRecord entity = ProvenanceRecord.builder()
        .provenanceType(type)
        .credentialFormat("JWT")
        .verified(false)
        .build();

    ProvenanceCredential result = mapper.toModel(entity);

    assertEquals(expected, result.getProvenanceType());
  }

  static Stream<Arguments> credentialFormatMappings() {
    return Stream.of(
        Arguments.of("JWT", ProvenanceCredential.CredentialFormatEnum.JWT),
        Arguments.of("JSONLD_JWT", ProvenanceCredential.CredentialFormatEnum.JSONLD_JWT),
        Arguments.of("JSONLD", ProvenanceCredential.CredentialFormatEnum.JSONLD)
    );
  }

  @ParameterizedTest
  @MethodSource("credentialFormatMappings")
  void toModel_credentialFormat_mapsToCorrectEnum(
      String format, ProvenanceCredential.CredentialFormatEnum expected) {
    ProvenanceRecord entity = ProvenanceRecord.builder()
        .credentialFormat(format)
        .verified(false)
        .build();

    ProvenanceCredential result = mapper.toModel(entity);

    assertEquals(expected, result.getCredentialFormat());
  }

  @Test
  void toModel_unknownCredentialFormat_throwsIllegalArgumentException() {
    ProvenanceRecord entity = ProvenanceRecord.builder()
        .credentialFormat("UNKNOWN")
        .verified(false)
        .build();

    assertThrows(IllegalArgumentException.class, () -> mapper.toModel(entity));
  }

  @Test
  void toModel_malformedVerificationResult_returnsNullVerificationResult() {
    ProvenanceRecord entity = ProvenanceRecord.builder()
        .credentialFormat("JWT")
        .verified(false)
        .verificationResult("{not valid json")
        .build();

    ProvenanceCredential result = mapper.toModel(entity);

    assertNull(result.getVerificationResult());
  }

  @Test
  void applyVerificationResult_valid_setsVerifiedTrue() {
    ProvenanceRecord entity = ProvenanceRecord.builder().verified(false).build();
    Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
    ProvenanceVerificationResult verificationResult = new ProvenanceVerificationResult();
    verificationResult.setIsValid(true);
    verificationResult.setVerificationTimestamp(timestamp);

    mapper.applyVerificationResult(entity, verificationResult);

    assertTrue(entity.isVerified());
    assertEquals(timestamp, entity.getVerificationTimestamp());
  }

  @Test
  void applyVerificationResult_invalid_setsVerifiedFalse() {
    ProvenanceRecord entity = ProvenanceRecord.builder().verified(true).build();
    ProvenanceVerificationResult verificationResult = new ProvenanceVerificationResult();
    verificationResult.setIsValid(false);

    mapper.applyVerificationResult(entity, verificationResult);

    assertFalse(entity.isVerified());
  }
}
