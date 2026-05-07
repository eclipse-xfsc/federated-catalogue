package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationRequest;
import eu.xfsc.fc.api.generated.model.ValidationResponse;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.exception.NotFoundException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.validation.strategy.ValidationStrategy;
import eu.xfsc.fc.core.service.verification.SchemaModuleConfigService;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Default implementation of {@link AssetValidationService}.
 *
 * <p>Orchestrates on-demand validation of stored assets against stored schemas.
 * Dispatches to registered {@link ValidationStrategy} implementations based on schema type
 * and asset format. Handles module toggles, schema resolution, and result storage via
 * {@link ValidationResultStore}.</p>
 *
 * <p>Multi-asset requests are restricted to SHACL validation. Single-asset requests support
 * all registered strategies (SHACL for RDF assets, JSON Schema for non-RDF JSON,
 * XML Schema for non-RDF XML). Each applicable strategy stores an independent
 * {@link eu.xfsc.fc.core.dao.validation.ValidationResult}.</p>
 */
@Service
@RequiredArgsConstructor
public class AssetValidationServiceImpl implements AssetValidationService {

  // increase with caution: each extra asset adds a full SHACL merge pass
  @Value("${federated-catalogue.validation.max-assets-per-request:20}")
  private int maxAssetsPerRequest;

  private final AssetStore assetStore;
  private final SchemaStore schemaStore;
  private final SchemaModuleConfigService moduleConfig;
  private final List<ValidationStrategy> strategies;
  private final ValidationResultStore validationResultStore;

  @Override
  @Transactional
  public ValidationResponse validateAssets(ValidationRequest request) {
    List<String> assetIds = request.getAssetIds();
    if (assetIds == null || assetIds.isEmpty()) {
      throw new ClientException("assetIds must contain at least one asset ID");
    }
    if (assetIds.size() > maxAssetsPerRequest) {
      throw new ClientException(
          "Too many assets in request: " + assetIds.size() + " (maximum: " + maxAssetsPerRequest + ")");
    }

    List<String> schemaIds = request.getSchemaIds();
    Boolean validateAll = request.getValidateAgainstAllSchemas();
    Instant validatedAt = Instant.now();

    if (assetIds.size() > 1) {
      return validateMultipleAssets(assetIds, schemaIds, validateAll, validatedAt);
    }

    return validateSingleAsset(assetIds.get(0), schemaIds, validateAll, validatedAt);
  }

  private ValidationResponse validateSingleAsset(
      String assetId, List<String> schemaIds, Boolean validateAll, Instant validatedAt) {
    AssetMetadata asset = assetStore.getById(assetId);

    if (asset.getContentAccessor() == null) {
      boolean anyApplies = strategies.stream().anyMatch(s -> s.appliesTo(asset));
      if (!anyApplies) {
        String ct = asset.getContentType();
        if (ct == null) {
          throw new VerificationException(
              "Asset " + asset.getId() + " has no content type. Cannot determine validation schema type. "
                  + "Validatable types: " + MediaType.APPLICATION_JSON_VALUE
                  + ", " + MediaType.APPLICATION_XML_VALUE);
        }
        throw new VerificationException(
            "Asset " + asset.getId() + " has unsupported content type for validation: " + ct
                + ". Validatable types: " + MediaType.APPLICATION_JSON_VALUE
                + ", " + MediaType.APPLICATION_XML_VALUE);
      }
    }

    List<StrategyJob> jobs;
    if (schemaIds != null && !schemaIds.isEmpty()) {
      jobs = planExplicit(asset, schemaIds);
    } else if (Boolean.TRUE.equals(validateAll)) {
      jobs = planAllApplicable(asset);
    } else {
      throw new NotFoundException(
          "No schemas specified for validation. Provide schemaIds or set validateAgainstAllSchemas=true.");
    }

    return execute(List.of(asset), jobs, validatedAt);
  }

  private ValidationResponse validateMultipleAssets(
      List<String> assetIds, List<String> schemaIds, Boolean validateAll, Instant validatedAt) {
    requireModuleEnabled(SchemaModuleType.SHACL);

    List<AssetMetadata> assets = new ArrayList<>();
    for (String id : assetIds) {
      AssetMetadata asset = assetStore.getById(id);
      if (asset.getContentAccessor() == null) {
        throw new VerificationException(
            "Asset " + id + " is not an RDF asset. Multi-asset validation only supports RDF assets for SHACL.");
      }
      assets.add(asset);
    }

    SchemaResolutionResult schemas = resolveShaclSchemas(schemaIds, validateAll);
    ValidationStrategy shaclStrategy = strategies.stream()
        .filter(s -> s.type() == ValidatorType.SHACL)
        .findFirst()
        .orElseThrow(() -> new ServerException("SHACL validation strategy not registered"));

    ValidationReport report = shaclStrategy.validate(assets, schemas.schemaContents());
    Long resultId = storeResult(assetIds, schemas.validatorIds(), ValidatorType.SHACL, report, validatedAt);

    return buildResponse(assetIds, schemas.schemaIds(), report, List.of(resultId), validatedAt);
  }

  /**
   * Plans validation jobs for a single asset against explicit schema IDs.
   *
   * <p>Schema IDs are grouped by the strategy that accepts them. Each group produces one
   * {@link StrategyJob}. SHACL accepts multiple shapes per job; JSON and XML enforce exactly
   * one schema.</p>
   *
   * @throws ClientException if no registered strategy accepts a schema type,
   *     if a non-SHACL strategy receives more than one schema,
   *     or if the matched strategy does not apply to the asset's format
   * @throws VerificationException if the matched strategy's module is disabled
   */
  private List<StrategyJob> planExplicit(AssetMetadata asset, List<String> schemaIds) {
    Map<ValidationStrategy, List<SchemaRecord>> byStrategy = new LinkedHashMap<>();
    for (String id : schemaIds) {
      SchemaRecord record = schemaStore.getSchemaRecord(id);
      ValidationStrategy strategy = findStrategyFor(record);
      byStrategy.computeIfAbsent(strategy, k -> new ArrayList<>()).add(record);
    }

    List<StrategyJob> jobs = new ArrayList<>();
    for (Map.Entry<ValidationStrategy, List<SchemaRecord>> entry : byStrategy.entrySet()) {
      ValidationStrategy strategy = entry.getKey();
      List<SchemaRecord> records = entry.getValue();

      if (strategy.type() != ValidatorType.SHACL && records.size() > 1) {
        throw new ClientException(
            strategy.type() + " validation supports exactly one schema per request, but "
                + records.size() + " were provided.");
      }
      if (!strategy.appliesTo(asset)) {
        throw new ClientException(
            "Schema type " + records.get(0).type() + " is not applicable to asset " + asset.getId()
                + ". RDF assets must be validated via SHACL (SHAPE schema type)."
                + " Non-RDF assets use JSON or XML schemas.");
      }
      requireModuleEnabled(strategy.moduleType());

      List<ContentAccessor> contents = records.stream()
          .map(r -> schemaStore.getSchema(r.getId()))
          .toList();
      List<String> ids = records.stream().map(SchemaRecord::getId).toList();
      jobs.add(new StrategyJob(strategy, contents, ids));
    }
    return jobs;
  }

  /**
   * Plans validation jobs for a single asset against all applicable stored schemas.
   *
   * <p>Each enabled strategy that applies to the asset is checked for available schemas.
   * Strategies with no stored schemas are silently skipped. Throws if no job can be planned.</p>
   *
   * @throws NotFoundException if at least one applicable, enabled strategy has no stored schemas
   *     (applies to non-RDF assets with a single applicable schema type)
   * @throws VerificationException if no strategy is both enabled and applicable
   */
  private List<StrategyJob> planAllApplicable(AssetMetadata asset) {
    List<StrategyJob> jobs = new ArrayList<>();
    boolean anyApplicableEnabled = false;
    Map<SchemaType, List<String>> schemasByType = null;

    for (ValidationStrategy strategy : strategies) {
      if (!moduleConfig.isModuleEnabled(strategy.moduleType())) {
        continue;
      }
      if (!strategy.appliesTo(asset)) {
        continue;
      }

      anyApplicableEnabled = true;
      SchemaType schemaType = resolveSchemaStoreType(strategy);
      ContentAccessor content = resolveAllSchemasContent(strategy, schemaType);
      if (content == null) {
        continue;
      }

      if (schemasByType == null) {
        schemasByType = schemaStore.getSchemaList();
      }
      List<String> ids = schemasByType.getOrDefault(schemaType, List.of());
      jobs.add(new StrategyJob(strategy, List.of(content), ids));
    }

    if (jobs.isEmpty()) {
      if (asset.getContentAccessor() == null && anyApplicableEnabled) {
        throw new NotFoundException(
            "No schema found for validation of asset " + asset.getId()
                + ". Ensure schemas of the applicable type are stored.");
      }
      throw new VerificationException(
          "No validation module is enabled or applicable for asset " + asset.getId()
              + ". Enable at least one applicable module via admin settings (SHACL, "
              + SchemaModuleType.JSON_SCHEMA + ", or " + SchemaModuleType.XML_SCHEMA + ").");
    }
    return jobs;
  }

  private ValidationResponse execute(
      List<AssetMetadata> assets, List<StrategyJob> jobs, Instant validatedAt) {
    List<String> assetIds = assets.stream().map(AssetMetadata::getId).toList();
    List<String> allSchemaIds = new ArrayList<>();
    List<Long> allResultIds = new ArrayList<>();
    ValidationReport firstReport = null;
    ValidationReport failingReport = null;

    for (StrategyJob job : jobs) {
      ValidationReport report = job.strategy().validate(assets, job.schemaContents());
      allResultIds.add(storeResult(assetIds, job.schemaIds(), job.strategy().type(), report, validatedAt));
      allSchemaIds.addAll(job.schemaIds());
      if (firstReport == null) {
        firstReport = report;
      }
      if (!report.getConforms() && failingReport == null) {
        failingReport = report;
      }
    }

    ValidationReport responseReport = failingReport != null ? failingReport : firstReport;
    return buildResponse(assetIds, allSchemaIds, responseReport, allResultIds, validatedAt);
  }

  /**
   * Resolves SHACL shapes into a merged list of ContentAccessors.
   *
   * <p>If explicit schemaIds are given, each is type-checked (must be SHAPE) and loaded.
   * If validateAgainstAllSchemas=true, the composite schema (union of all stored shapes) is used.</p>
   */
  private SchemaResolutionResult resolveShaclSchemas(List<String> schemaIds, Boolean validateAll) {
    if (schemaIds != null && !schemaIds.isEmpty()) {
      List<ContentAccessor> contents = new ArrayList<>();
      List<String> resolvedIds = new ArrayList<>();
      for (String id : schemaIds) {
        SchemaRecord record = schemaStore.getSchemaRecord(id);
        if (record.type() != SchemaType.SHAPE) {
          throw new ClientException(
              "Schema " + id + " has type " + record.type()
                  + " and cannot be used for SHACL validation. Only SHAPE schemas are accepted.");
        }
        contents.add(schemaStore.getSchema(id));
        resolvedIds.add(id);
      }
      return new SchemaResolutionResult(contents, resolvedIds, resolvedIds);
    }

    if (Boolean.TRUE.equals(validateAll)) {
      ContentAccessor composite = schemaStore.getCompositeSchema(SchemaType.SHAPE);
      if (composite == null) {
        throw new NotFoundException("No SHACL shapes found for validation");
      }
      List<String> allIds = schemaStore.getSchemaList().getOrDefault(SchemaType.SHAPE, List.of());
      return new SchemaResolutionResult(List.of(composite), allIds, allIds);
    }

    throw new NotFoundException(
        "No schemas specified for validation. Provide schemaIds or set validateAgainstAllSchemas=true.");
  }

  private ValidationStrategy findStrategyFor(SchemaRecord record) {
    return strategies.stream()
        .filter(s -> s.acceptsSchema(record))
        .findFirst()
        .orElseThrow(() -> new ClientException(
            "Schema " + record.getId() + " has type " + record.type()
                + " which is not supported for on-demand validation. Supported types: SHAPE, JSON, XML."));
  }

  private SchemaType resolveSchemaStoreType(ValidationStrategy strategy) {
    return switch (strategy.type()) {
      case SHACL -> SchemaType.SHAPE;
      case JSON_SCHEMA -> SchemaType.JSON;
      case XML_SCHEMA -> SchemaType.XML;
      default -> throw new ServerException("Unknown validator type: " + strategy.type());
    };
  }

  private ContentAccessor resolveAllSchemasContent(ValidationStrategy strategy, SchemaType schemaType) {
    if (strategy.type() == ValidatorType.SHACL) {
      return schemaStore.getCompositeSchema(schemaType);
    }
    return schemaStore.getLatestSchemaByType(schemaType);
  }

  private void requireModuleEnabled(String moduleType) {
    if (!moduleConfig.isModuleEnabled(moduleType)) {
      throw new VerificationException("Validation module " + moduleType + " is disabled");
    }
  }

  private Long storeResult(List<String> assetIds, List<String> validatorIds,
      ValidatorType validatorType, ValidationReport report, Instant validatedAt) {
    String rawReport = report.getConforms() ? null : report.getRawReport();
    return validationResultStore.store(new ValidationResultRecord(
        assetIds,
        validatorIds,
        validatorType,
        report.getConforms(),
        validatedAt,
        rawReport));
  }


  /**
   * Builds the {@link ValidationResponse} for a completed validation run.
   *
   * <p>When multiple strategies ran (for example SHACL plus JSON Schema in a single request),
   * the response report is the first <em>failing</em> report, or the first report overall
   * if all strategies conformed. This means only one report is surfaced per response;
   * all result IDs are included regardless. The report is omitted entirely when the
   * overall result conforms.</p>
   */
  private ValidationResponse buildResponse(List<String> assetIds, List<String> schemaIds,
      ValidationReport report, List<Long> validationResultIds, Instant validatedAt) {
    ValidationResponse response = new ValidationResponse();
    response.setAssetIds(assetIds);
    response.setSchemaIds(schemaIds);
    response.setConforms(report.getConforms());
    response.setValidatedAt(validatedAt);
    response.setValidationResultIds(validationResultIds);
    if (!report.getConforms()) {
      response.setReport(report);
    }
    return response;
  }

  /**
   * Holds resolved SHACL schema ContentAccessors, schema IDs, and validator IDs for storage.
   */
  private record SchemaResolutionResult(
      List<ContentAccessor> schemaContents, List<String> schemaIds, List<String> validatorIds) {
  }

  /**
   * Binds a {@link ValidationStrategy} to the pre-resolved schema content and IDs for one dispatch.
   */
  private record StrategyJob(
      ValidationStrategy strategy,
      List<ContentAccessor> schemaContents,
      List<String> schemaIds) {
  }
}
