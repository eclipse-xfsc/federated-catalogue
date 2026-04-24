package eu.xfsc.fc.server.service;

import eu.xfsc.fc.api.generated.model.StoredValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;
import eu.xfsc.fc.server.generated.controller.ValidationsApiDelegate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/** REST delegate for the Validations API endpoints. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationResultService implements ValidationsApiDelegate {

  private final ValidationResultStore validationResultStore;

  @Override
  public ResponseEntity<StoredValidationResult> getValidationResult(Long id) {
    log.debug("getValidationResult; id={}", id);
    ValidationResult entity = validationResultStore.getById(id)
        .orElseThrow(() -> new NotFoundException("Validation result not found: " + id));
    return ResponseEntity.ok(ValidationResultMapper.toDto(entity));
  }
}
