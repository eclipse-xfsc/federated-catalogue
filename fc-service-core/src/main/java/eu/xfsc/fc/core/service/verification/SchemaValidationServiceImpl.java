package eu.xfsc.fc.core.service.verification;

import static org.apache.jena.rdf.model.ResourceFactory.createProperty;
import static org.apache.jena.rdf.model.ResourceFactory.createResource;
import static org.apache.jena.rdf.model.ResourceFactory.createStatement;
import static org.apache.jena.rdf.model.ResourceFactory.createTypedLiteral;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.springframework.stereotype.Component;
import org.topbraid.shacl.util.ModelPrinter;
import org.topbraid.shacl.vocabulary.SH;

import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.validation.rdf.RdfAssetParser;
import eu.xfsc.fc.core.service.validation.strategy.ShaclValidationExecutor;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;

/**
 * Default implementation of {@link SchemaValidationService}.
 *
 * <p>Performs SHACL validation by extracting RDF claims from a credential payload,
 * building a Jena data model, parsing the shape via {@link RdfAssetParser}, and
 * executing SHACL via the shared {@link ShaclValidationExecutor}.</p>
 *
 * @see SchemaValidationService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaValidationServiceImpl implements SchemaValidationService {

  private final ClaimExtractionService claimExtractionService;
  private final SchemaStore schemaStore;
  private final RdfAssetParser rdfAssetParser;
  private final ShaclValidationExecutor shaclValidationExecutor;

  /** {@inheritDoc} Delegates to {@link #validateCredentialAgainstSchema} with a {@code null} schema. */
  @Override
  public SchemaValidationResult validateCredentialAgainstCompositeSchema(ContentAccessor payload) {
    return validateCredentialAgainstSchema(payload, null);
  }

  /** {@inheritDoc} */
  @Override
  public SchemaValidationResult validateCredentialAgainstSchema(ContentAccessor payload, ContentAccessor schema) {
    log.debug("validateCredentialAgainstSchema.enter;");
    SchemaValidationResult result = null;
    try {
      if (schema == null) {
        schema = schemaStore.getCompositeSchema(SchemaStore.SchemaType.SHAPE);
      }
      List<RdfClaim> claims = extractClaims(payload);
      result = validateClaimsAgainstSchema(claims, schema);
    } catch (Exception exc) {
      log.info("validateCredentialAgainstSchema.error: {}", exc.getMessage());
    }
    boolean conforms = result != null && result.isConforming();
    log.debug("validateCredentialAgainstSchema.exit; conforms: {}", conforms);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public SchemaValidationResult validateClaimsAgainstCompositeSchema(List<RdfClaim> claims) {
    log.debug("validateClaimsAgainstCompositeSchema.enter;");
    SchemaValidationResult result = null;
    try {
      ContentAccessor shaclShape = schemaStore.getCompositeSchema(SchemaStore.SchemaType.SHAPE);
      result = validateClaimsAgainstSchema(claims, shaclShape);
    } catch (Exception exc) {
      log.info("validateClaimsAgainstCompositeSchema.error: {}", exc.getMessage());
    }
    boolean conforms = result != null && result.isConforming();
    log.debug("validateClaimsAgainstCompositeSchema.exit; conforms: {}", conforms);
    return result;
  }

  /** {@inheritDoc} */
  @Override
  public SchemaValidationResult validateClaimsAgainstSchema(List<RdfClaim> claims, ContentAccessor schema) {
    Model shapesModel = rdfAssetParser.parseShape(schema);
    Model dataModel = buildDataModel(claims);
    Resource reportResource = shaclValidationExecutor.validate(dataModel, shapesModel);
    boolean conforms = reportResource.getProperty(SH.conforms).getBoolean();
    String rawReport = conforms ? null : ModelPrinter.get().print(reportResource.getModel());
    return new SchemaValidationResult(conforms, rawReport);
  }

  private List<RdfClaim> extractClaims(ContentAccessor payload) {
    return claimExtractionService.extractCredentialClaims(payload);
  }

  private Model buildDataModel(List<RdfClaim> claims) {
    Model data = ModelFactory.createDefaultModel();
    for (RdfClaim claim : claims) {
      log.trace("buildDataModel; {}", claim);
      Statement triple = claim.getTriple();
      if (triple != null) {
        data.add(triple);
        continue;
      }
      RDFNode node;
      String objectStr = claim.getObjectString();
      if (objectStr != null && objectStr.startsWith("\"")) {
        node = createTypedLiteral(claim.getObjectValue(), (RDFDatatype) null);
      } else {
        node = createResource(claim.getObjectValue());
      }
      data.add(createStatement(
          createResource(claim.getSubjectValue()),
          createProperty(claim.getPredicateValue()),
          node));
    }
    return data;
  }
}
