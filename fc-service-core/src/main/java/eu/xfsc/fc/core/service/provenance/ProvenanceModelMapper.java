package eu.xfsc.fc.core.service.provenance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.api.generated.model.ProvenanceCredential;
import eu.xfsc.fc.api.generated.model.ProvenanceVerificationResult;
import eu.xfsc.fc.core.dao.provenance.ProvenanceRecord;
import eu.xfsc.fc.core.dao.provenance.ProvenanceType;
import eu.xfsc.fc.core.exception.ServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Maps {@link ProvenanceRecord} entities to API model objects and applies verification results.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvenanceModelMapper {

  private final ObjectMapper objectMapper;

  /**
   * Converts a persisted provenance record to the API response model.
   */
  public ProvenanceCredential toModel(ProvenanceRecord entity) {
    ProvenanceCredential model = new ProvenanceCredential();
    model.setId(entity.getId());
    model.setAssetId(entity.getAssetId());
    model.setAssetVersion(entity.getAssetVersion());
    model.setCredentialId(entity.getCredentialId());
    model.setIssuer(entity.getIssuer());
    model.setIssuedAt(entity.getIssuedAt());
    model.setProvenanceType(mapProvenanceType(entity.getProvenanceType()));
    model.setCredentialFormat(mapCredentialFormat(entity.getCredentialFormat()));
    model.setCredentialContent(entity.getCredentialContent());
    model.setVerified(entity.isVerified());
    model.setVerificationTimestamp(entity.getVerificationTimestamp());
    model.setVerificationResult(deserializeVerificationResult(entity.getVerificationResult()));
    return model;
  }

  /**
   * Applies a verification result to the entity fields.
   *
   * <p>Serialisation is performed first; if it fails a {@link ServerException} is thrown so the
   * enclosing {@code REQUIRES_NEW} transaction rolls back rather than persisting a partially-updated
   * record.</p>
   */
  public void applyVerificationResult(ProvenanceRecord entity, ProvenanceVerificationResult result) {
    try {
      String serialized = objectMapper.writeValueAsString(result);
      entity.setVerified(Boolean.TRUE.equals(result.getIsValid()));
      entity.setVerificationTimestamp(result.getVerificationTimestamp());
      entity.setVerificationResult(serialized);
    } catch (JsonProcessingException ex) {
      throw new ServerException(
          "Failed to serialize verification result for credentialId=" + entity.getCredentialId(), ex);
    }
  }

  private ProvenanceCredential.ProvenanceTypeEnum mapProvenanceType(ProvenanceType type) {
    if (type == null) {
      return null;
    }
    return switch (type) {
      case CREATION -> ProvenanceCredential.ProvenanceTypeEnum.CREATION;
      case DERIVATION -> ProvenanceCredential.ProvenanceTypeEnum.DERIVATION;
      case ATTRIBUTION -> ProvenanceCredential.ProvenanceTypeEnum.ATTRIBUTION;
      case MODIFICATION -> ProvenanceCredential.ProvenanceTypeEnum.MODIFICATION;
    };
  }

  private ProvenanceCredential.CredentialFormatEnum mapCredentialFormat(String format) {
    if (format == null) {
      return null;
    }
    return switch (format) {
      case "JWT" -> ProvenanceCredential.CredentialFormatEnum.JWT;
      case "JSONLD_JWT" -> ProvenanceCredential.CredentialFormatEnum.JSONLD_JWT;
      case "JSONLD" -> ProvenanceCredential.CredentialFormatEnum.JSONLD;
      default -> throw new IllegalArgumentException("Unknown credential format: " + format);
    };
  }

  private ProvenanceVerificationResult deserializeVerificationResult(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, ProvenanceVerificationResult.class);
    } catch (JsonProcessingException ex) {
      log.warn("deserializeVerificationResult; failed to deserialize: {}", ex.getMessage(), ex);
      return null;
    }
  }
}
