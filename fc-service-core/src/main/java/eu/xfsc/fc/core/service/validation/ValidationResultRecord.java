package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.core.dao.validation.ValidatorType;
import java.time.Instant;
import java.util.List;

/**
 * Data transfer record for persisting the outcome of an asset validation run.
 *
 * <p>Input DTO for {@link ValidationResultStoreImpl#store(ValidationResultRecord)}.
 * All resolved schema IDs are passed explicitly.
 * This ensures complete temporal reconstruction of which schemas were used.</p>
 */
public record ValidationResultRecord(
    List<String> assetIds,
    List<String> validatorIds,
    ValidatorType validatorType,
    boolean conforms,
    Instant validatedAt,
    String report             // nullable; SHACL Turtle / JSON violations / XSD error
) {}
