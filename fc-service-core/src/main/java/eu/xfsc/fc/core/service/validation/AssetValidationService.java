package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.api.generated.model.ValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationResponse;

/**
 * On-demand validation of stored assets against stored schemas.
 *
 * <p>Results are persisted via {@link ValidationResultStore} and retrievable
 * through the GET /assets/{id}/validations endpoint.</p>
 *
 * <p>Exception to HTTP-status mapping:</p>
 * <ul>
 *   <li>{@link eu.xfsc.fc.core.exception.ClientException} &rarr; 400 Bad Request.
 *       Covers malformed input, empty/oversize {@code assetIds}, and the
 *       {@code module_disabled:<MODULE>} case when a required schema validation
 *       module is administratively disabled.</li>
 *   <li>{@link eu.xfsc.fc.core.exception.NotFoundException} &rarr; 404 Not Found.</li>
 *   <li>{@link eu.xfsc.fc.core.exception.VerificationException} &rarr; 422 Unprocessable Entity.
 *       Reserved for assets whose type is not validatable (not RDF, unsupported content type).</li>
 * </ul>
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
   * @throws eu.xfsc.fc.core.exception.ClientException         if {@code assetIds} is empty,
   *     exceeds the maximum, or a required schema validation module is disabled
   *     ({@code module_disabled:<MODULE>})
   */
  ValidationResponse validateAssets(ValidationRequest request);
}
