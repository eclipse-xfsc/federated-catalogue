package eu.xfsc.fc.core.pojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.jena.rdf.model.Statement;

/**
 * Subclass of {@link RdfClaim} representing a claim extracted from a Verifiable Credential.
 * Currently, it does not add any additional fields or methods, but serves as a semantic marker
 * for claims originating from credentials, allowing for future extensions specific to credential claims.
 */
public class CredentialClaim extends RdfClaim {

    public CredentialClaim(Statement triple, ObjectMapper objectMapper) {
        super(triple, objectMapper);
    }

    public CredentialClaim(String subject, String predicate, String object) {
        super(subject, predicate, object);
    }
}
