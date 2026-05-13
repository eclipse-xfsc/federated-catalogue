package eu.xfsc.fc.core.service.validation.strategy;

import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.service.validation.report.ValidationReportFactory;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.schemastore.SchemaRecord;
import eu.xfsc.fc.core.service.schemastore.SchemaStore.SchemaType;
import eu.xfsc.fc.core.service.validation.rdf.RdfAssetParser;
import eu.xfsc.fc.core.service.verification.SchemaModuleType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link ValidationStrategy} implementation for SHACL validation of RDF assets.
 *
 * <p>Supports all RDF serializations — Turtle, N-Triples, JSON-LD, RDF/XML — including
 * Loire JWT-wrapped credentials. For multi-asset requests, all asset models are merged
 * before validation. All shape models are also merged when multiple schemas are provided.</p>
 *
 * <p>Parsing is delegated to {@link RdfAssetParser}; this strategy owns only the
 * TopBraid SHACL execution and result mapping.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ShaclValidationStrategy implements ValidationStrategy {

  private final RdfAssetParser rdfAssetParser;
  private final ShaclValidationExecutor shaclValidationExecutor;

  @Override
  public ValidatorType type() {
    return ValidatorType.SHACL;
  }

  @Override
  public String moduleType() {
    return SchemaModuleType.SHACL;
  }

  /** Returns {@code true} for any asset that has RDF content (content accessor is non-null). */
  @Override
  public boolean appliesTo(AssetMetadata asset) {
    return asset.getContentAccessor() != null;
  }

  @Override
  public boolean acceptsSchema(SchemaRecord record) {
    return record.type() == SchemaType.SHAPE;
  }

  /**
   * Validates one or more RDF assets against one or more SHACL shape graphs.
   *
   * <p>All asset models are merged into a single data graph before validation.
   * All schema ContentAccessors are parsed and merged into a single shapes graph.</p>
   *
   * @param assets   RDF assets; each must have a non-null content accessor
   * @param schemas  SHACL shape documents (Turtle); must not be empty
   * @return validation report with conforms flag, violations, and raw Turtle report
   */
  @Override
  public ValidationReport validate(List<AssetMetadata> assets, List<ContentAccessor> schemas) {
    Model shapesModel = buildMergedShapesModel(schemas);
    Model dataModel = buildMergedDataModel(assets);
    return ValidationReportFactory.fromShacl(shaclValidationExecutor.validate(dataModel, shapesModel));
  }

  private Model buildMergedShapesModel(List<ContentAccessor> schemas) {
    Model merged = ModelFactory.createDefaultModel();
    for (ContentAccessor schema : schemas) {
      merged.add(rdfAssetParser.parseShape(schema));
    }
    return merged;
  }

  private Model buildMergedDataModel(List<AssetMetadata> assets) {
    Model merged = ModelFactory.createDefaultModel();
    for (AssetMetadata asset : assets) {
      merged.add(rdfAssetParser.parse(asset));
    }
    return merged;
  }

}
