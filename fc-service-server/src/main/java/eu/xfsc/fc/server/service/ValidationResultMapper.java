package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.StoredValidationResult;
import eu.xfsc.fc.api.generated.model.StoredValidationResult.ValidatorTypeEnum;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import java.util.Arrays;

/**
 * Converts {@link ValidationResult} entities to {@link StoredValidationResult} API models.
 */
class ValidationResultMapper {

  private ValidationResultMapper() {}

  static StoredValidationResult toDto(ValidationResult entity) {
    return new StoredValidationResult()
        .id(entity.getId())
        .assetIds(Arrays.asList(entity.getAssetIds()))
        .validatorIds(Arrays.asList(entity.getValidatorIds()))
        .validatorType(ValidatorTypeEnum.fromValue(entity.getValidatorType().name()))
        .conforms(entity.isConforms())
        .validatedAt(entity.getValidatedAt())
        .report(entity.getReport())
        .contentHash(entity.getContentHash())
        .createdAt(entity.getCreatedAt());
  }
}
