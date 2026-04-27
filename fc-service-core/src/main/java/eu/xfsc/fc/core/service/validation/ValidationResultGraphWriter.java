package eu.xfsc.fc.core.service.validation;

import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Writes validation result metadata to the graph DB as {@code fcmeta:} RDF triples.
 *
 * <p>Calls {@link GraphStore#addClaims(List, String)} directly, bypassing
 * {@code ProtectedNamespaceFilter}. This is intentional: the filter only blocks
 * user-supplied content at the external ingestion boundary. Internal services writing
 * trusted {@code fcmeta:} predicates must bypass it — this writer is the only permitted
 * path for writing validation result triples.</p>
 *
 * <p>Triple structure per result:</p>
 * <pre>
 *   &lt;assetId&gt; fcmeta:hasValidationResult &lt;validationResultIri&gt;
 *   &lt;validationResultIri&gt; fcmeta:validatorId &lt;schemaId1&gt;
 *   &lt;validationResultIri&gt; fcmeta:validatorId &lt;schemaId2&gt;
 *   &lt;validationResultIri&gt; fcmeta:conforms "true"^^xsd:boolean
 *   &lt;validationResultIri&gt; fcmeta:validatorType "SCHEMA"
 *   &lt;validationResultIri&gt; fcmeta:validatedAt "&lt;ISO timestamp&gt;"^^xsd:dateTime
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationResultGraphWriter {

  private static final String XSD = "http://www.w3.org/2001/XMLSchema#";

  private final ProtectedNamespaceProperties namespaceProperties;

  /**
   * Writes all triples for the given validation result to the graph store.
   *
   * @param result      persisted entity with a non-null ID
   * @param graphStore  the active graph store implementation
   */
  public void write(ValidationResult result, GraphStore graphStore) {
    String fcmeta = namespaceProperties.getNamespace();
    String resultIri = buildResultIri(result.getId(), fcmeta);
    List<RdfClaim> claims = new ArrayList<>();

    // Link each asset subject IRI → this validation result
    for (String assetId : result.getAssetIds()) {
      claims.add(iriTriple(assetId, fcmeta + "hasValidationResult", resultIri));
    }

    // Link each schema IRI used in this validation
    for (String validatorId : result.getValidatorIds()) {
      claims.add(iriTriple(resultIri, fcmeta + "validatorId", validatorId));
    }

    // Validation result properties
    claims.add(literalTriple(resultIri, fcmeta + "conforms",
        "\"" + result.isConforms() + "\"^^<" + XSD + "boolean>"));
    claims.add(literalTriple(resultIri, fcmeta + "validatorType",
        "\"" + result.getValidatorType() + "\""));
    claims.add(literalTriple(resultIri, fcmeta + "validatedAt",
        "\"" + result.getValidatedAt().toString() + "\"^^<" + XSD + "dateTime>"));

    // Use the first assetId as the credentialSubject grouping key for graph partitioning
    String credentialSubject = result.getAssetIds()[0];
    graphStore.addClaims(claims, credentialSubject);
    log.debug("write; wrote {} fcmeta triples for result id={}", claims.size(), result.getId());
  }

  private static String buildResultIri(Long id, String fcmeta) {
    return fcmeta + "ValidationResult/" + id;
  }

  private static CredentialClaim iriTriple(String subject, String predicate, String object) {
    return new CredentialClaim(
        "<" + subject + ">",
        "<" + predicate + ">",
        "<" + object + ">");
  }

  private static CredentialClaim literalTriple(String subject, String predicate, String literal) {
    return new CredentialClaim(
        "<" + subject + ">",
        "<" + predicate + ">",
        literal);
  }

}

