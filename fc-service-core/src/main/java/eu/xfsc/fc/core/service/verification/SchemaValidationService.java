package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;

import java.util.List;

/**
 * Service interface for validating self-descriptions against SHACL schemas.
 *
 * <p>Defines the contract for SHACL-based schema validation of self-descriptions and their
 * extracted claims. Supports validation against a specific schema or the composite schema
 * built from all stored SHACL shapes.</p>
 *
 * @see SchemaValidationServiceImpl
 * @see VerificationService
 */
public interface SchemaValidationService {

    /**
     * Validates a self-description payload against a specific SHACL shape graph.
     *
     * @param payload the self-description whose claims are extracted and validated
     * @param schema  the SHACL shape graph to validate against, or {@code null}
     *                to use the composite schema from the schema store
     * @return a {@link SchemaValidationResult} indicating conformance and
     *         containing the SHACL validation report
     */
    SchemaValidationResult validateSelfDescriptionAgainstSchema(ContentAccessor payload, ContentAccessor schema);

    /**
     * Validates a self-description payload against the composite SHACL schema
     * assembled from all shapes currently stored in the
     * {@link eu.xfsc.fc.core.service.schemastore.SchemaStore}.
     *
     * @param payload the self-description whose claims are extracted and validated
     * @return a {@link SchemaValidationResult} indicating conformance and
     *         containing the SHACL validation report
     */
    SchemaValidationResult validateSelfDescriptionAgainstCompositeSchema(ContentAccessor payload);

    /**
     * Validates pre-extracted RDF claims against a specific SHACL shape graph.
     *
     * @param claims the pre-extracted {@link SdClaim} list from the self-description
     * @param schema the SHACL shape graph to validate the claims against, or {@code null}
     *                to use the composite schema from the schema store
     * @return a {@link SchemaValidationResult} indicating conformance and
     *         containing the SHACL validation report
     */
    SchemaValidationResult validateClaimsAgainstSchema(List<SdClaim> claims, ContentAccessor schema);

    /**
     * Validates pre-extracted RDF claims against the composite SHACL schema
     * assembled from all stored shapes.
     *
     * @param claims the pre-extracted {@link SdClaim} list from the self-description
     * @return a {@link SchemaValidationResult} indicating conformance and
     *         containing the SHACL validation report
     */
    SchemaValidationResult validateClaimsAgainstCompositeSchema(List<SdClaim> claims);

}
