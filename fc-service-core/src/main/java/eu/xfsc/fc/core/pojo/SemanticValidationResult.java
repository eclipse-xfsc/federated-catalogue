package eu.xfsc.fc.core.pojo;

/**
 * POJO Class for holding Semantic Validation Results.
 */
@lombok.EqualsAndHashCode
@lombok.Getter
@lombok.Setter
@lombok.AllArgsConstructor
public class SemanticValidationResult {
    private final boolean conforming;
    private final String validationReport;
}

