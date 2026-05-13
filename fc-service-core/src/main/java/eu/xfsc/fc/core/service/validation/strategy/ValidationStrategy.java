package eu.xfsc.fc.core.service.validation.strategy;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;

import java.util.List;

/**
 * SPI for a single-paradigm asset validator.
 *
 * <p>Each strategy is a Spring {@code @Service} bean. The orchestrator
 * ({@link eu.xfsc.fc.core.service.validation.AssetValidationServiceImpl}) collects all
 * strategies via {@code List<ValidationStrategy>} constructor injection and dispatches to
 * applicable ones based on {@link #appliesTo} and {@link #acceptsSchema}.</p>
 *
 * <p>Cardinality contract:
 * <ul>
 *   <li>SHACL accepts N assets + N schemas (all asset models merged; all shapes merged).</li>
 *   <li>JSON Schema and XML Schema accept exactly 1 asset + 1 schema; implementations
 *       throw {@link eu.xfsc.fc.core.exception.ClientException} with accepted values
 *       if called with more.</li>
 * </ul></p>
 *
 * <p>The {@link ValidatorType#TRUST_FRAMEWORK} case has no strategy implementation yet;
 * compliance evaluation is currently performed by the trust-framework orchestrator.</p>
 */
public interface ValidationStrategy {

  /**
   * Stable identifier persisted in {@code ValidationResult.validatorType}.
   */
  ValidatorType type();

  /**
   * Module gating key used to check whether this validator is enabled in admin config.
   * Values are constants from {@link eu.xfsc.fc.core.service.verification.SchemaModuleType}.
   */
  String moduleType();

  /**
   * Returns {@code true} when this strategy can validate the given asset based on its
   * content type and content shape.
   */
  boolean appliesTo(AssetMetadata asset);

  /**
   * Returns {@code true} when the given schema record is consumable by this strategy.
   */
  boolean acceptsSchema(SchemaRecord record);

  /**
   * Validates one or more assets against one or more schemas.
   *
   * @param assets   assets to validate; must not be empty
   * @param schemas  pre-loaded schema content accessors; must not be empty
   * @return validation report with conforms flag, violations, and optional raw report
   * @throws eu.xfsc.fc.core.exception.ClientException if cardinality or content-type
   *     constraints are violated
   */
  ValidationReport validate(List<AssetMetadata> assets, List<ContentAccessor> schemas);
}
