package eu.xfsc.fc.core.service.validation.report;

import com.networknt.schema.Error;
import eu.xfsc.fc.api.generated.model.ValidationReport;
import eu.xfsc.fc.api.generated.model.ValidationViolation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.topbraid.shacl.util.ModelPrinter;
import org.topbraid.shacl.vocabulary.SH;
import org.xml.sax.SAXException;

/**
 * Factory for assembling {@link ValidationReport} instances from raw validator output.
 *
 * <p>Centralises all report construction so that individual {@code ValidationStrategy}
 * implementations contain no {@code new ValidationReport()} / {@code new ValidationViolation()}
 * boilerplate.</p>
 */
@UtilityClass
public class ValidationReportFactory {

  private static final String SHACL_NS = "http://www.w3.org/ns/shacl#";
  private static final Map<String, ValidationViolation.SeverityEnum> SHACL_SEVERITY_MAP = Map.of(
      SHACL_NS + "Violation", ValidationViolation.SeverityEnum.VIOLATION,
      SHACL_NS + "Warning", ValidationViolation.SeverityEnum.WARNING,
      SHACL_NS + "Info", ValidationViolation.SeverityEnum.INFO
  );

  /** Returns a conforming report with an empty violations list. */
  public static ValidationReport conforming() {
    return new ValidationReport().conforms(true).violations(List.of());
  }

  /**
   * Builds a report from a TopBraid SHACL {@code sh:ValidationReport} resource.
   *
   * @param reportResource the SHACL result resource produced by {@code ValidationUtil}
   * @return report with conforms flag, violations, and raw Turtle report on failure
   */
  public static ValidationReport fromShacl(Resource reportResource) {
    boolean conforms = reportResource.getProperty(SH.conforms).getBoolean();
    if (conforms) {
      return conforming();
    }

    List<ValidationViolation> violations = new ArrayList<>();
    StmtIterator results = reportResource.getModel().listStatements(null, SH.result, (RDFNode) null);
    while (results.hasNext()) {
      Resource result = results.next().getObject().asResource();
      ValidationViolation violation = new ValidationViolation();
      violation.setFocusNode(getStringProperty(result, SH.focusNode));
      violation.setResultPath(getStringProperty(result, SH.resultPath));
      violation.setMessage(getStringProperty(result, SH.resultMessage));
      String severityUri = getStringProperty(result, SH.resultSeverity);
      violation.setSeverity(
          SHACL_SEVERITY_MAP.getOrDefault(severityUri, ValidationViolation.SeverityEnum.VIOLATION));
      violation.setSourceShape(getStringProperty(result, SH.sourceShape));
      violations.add(violation);
    }
    return new ValidationReport()
        .conforms(false)
        .violations(violations)
        .rawReport(ModelPrinter.get().print(reportResource.getModel()));
  }

  /**
   * Builds a report from a list of networknt JSON Schema {@link Error} objects.
   *
   * @param errors validation errors; empty list produces a conforming report
   * @return report with conforms flag, violations, and newline-joined raw report on failure
   */
  public static ValidationReport fromJsonErrors(List<Error> errors) {
    if (errors.isEmpty()) {
      return conforming();
    }
    return new ValidationReport()
        .conforms(false)
        .violations(errors.stream().map(ValidationReportFactory::toJsonViolation).toList())
        .rawReport(errors.stream()
            .map(Error::getMessage)
            .reduce((a, b) -> a + "\n" + b)
            .orElse(null));
  }

  /**
   * Builds a non-conforming report from a single SAX validation exception.
   *
   * @param e the SAX exception thrown by the XML {@code Validator}
   * @return report with conforms=false, one violation, and the exception message as raw report
   */
  public static ValidationReport fromSaxException(SAXException e) {
    return new ValidationReport()
        .conforms(false)
        .violations(List.of(new ValidationViolation()
        .message(e.getMessage())
            .severity(ValidationViolation.SeverityEnum.VIOLATION)))
        .rawReport(e.getMessage());
  }

  private static ValidationViolation toJsonViolation(Error error) {
    ValidationViolation violation = new ValidationViolation();
    violation.setFocusNode(
        error.getInstanceLocation() != null ? error.getInstanceLocation().toString() : null);
    violation.setMessage(error.getMessage());
    violation.setSeverity(ValidationViolation.SeverityEnum.VIOLATION);
    violation.setSourceShape(
        error.getSchemaLocation() != null ? error.getSchemaLocation().toString() : null);
    return violation;
  }

  private static String getStringProperty(Resource resource, Property property) {
    var stmt = resource.getProperty(property);
    if (stmt == null) {
      return null;
    }
    RDFNode node = stmt.getObject();
    if (node.isLiteral()) {
      return node.asLiteral().getString();
    }
    if (node.isResource()) {
      return node.asResource().getURI();
    }
    return null;
  }
}
