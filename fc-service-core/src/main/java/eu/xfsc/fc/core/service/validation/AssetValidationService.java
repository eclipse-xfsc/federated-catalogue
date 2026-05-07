package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.api.generated.model.ValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationResponse;

/**
 * On-demand validation of stored assets against stored schemas.
 *
 * <p>Results are persisted via {@link ValidationResultStore} and retrievable
 * through the GET /assets/{id}/validations endpoint.</p>
 */
public interface AssetValidationService {

  /**
   * Validates one or more stored assets against stored schemas.
   *
   * <p>If {@code assetIds} contains a single entry, the asset is dispatched through the
   * full type-based pipeline (SHACL for RDF, JSON Schema for JSON, XML Schema for XML).
   * If {@code assetIds} contains multiple entries, all assets are merged into a single
   * data graph and validated via SHACL only.</p>
   *
   * @param request asset IRIs and schema selection (schemaIds or validateAgainstAllSchemas)
   * @return validation response with result ID(s) and optional report
   * @throws eu.xfsc.fc.core.exception.NotFoundException       if an asset or schema ID does not exist
   * @throws eu.xfsc.fc.core.exception.VerificationException   if an asset type is not validatable
   * @throws eu.xfsc.fc.core.exception.ClientException         if assetIds is empty or exceeds the maximum
   */
  ValidationResponse validateAssets(ValidationRequest request);
}
