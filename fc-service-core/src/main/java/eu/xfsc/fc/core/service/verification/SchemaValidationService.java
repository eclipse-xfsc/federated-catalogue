package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;

/**
 * Service interface for validating RDF data against SHACL schemas.
 *
 * <p>Defines the contract for SHACL-based schema validation of RDF payloads.
 * Supports validation against a specific schema or the composite schema built
 * from all stored SHACL shapes. Works on any RDF serialization format.</p>
 *
 * @see SchemaValidationServiceImpl
 * @see VerificationService
 */
public interface SchemaValidationService {

    /**
     * Validates an RDF payload against a specific SHACL shape graph.
     *
     * @param payload the RDF data whose claims are extracted and validated
     * @param schema  the SHACL shape graph to validate against, or {@code null}
     *                to use the composite schema from the schema store
     * @return a {@link SchemaValidationResult} indicating conformance and
     *         containing the SHACL validation report
     */
    SchemaValidationResult validateCredentialAgainstSchema(ContentAccessor payload, ContentAccessor schema);

    /**
     * Validates an RDF payload against the composite SHACL schema
     * assembled from all shapes currently stored in the
     * {@link eu.xfsc.fc.core.service.schemastore.SchemaStore}.
     *
     * @param payload the RDF data whose claims are extracted and validated
     * @return a {@link SchemaValidationResult} indicating conformance and
     *         containing the SHACL validation report
     */
    SchemaValidationResult validateCredentialAgainstCompositeSchema(ContentAccessor payload);

}
