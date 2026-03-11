package eu.xfsc.fc.core.pojo;

/**
 * POJO Class for holding Schema Validation Results.
 */
@lombok.EqualsAndHashCode
@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
public class SchemaValidationResult {
    private final boolean conforming;
    private final String validationReport;
}
