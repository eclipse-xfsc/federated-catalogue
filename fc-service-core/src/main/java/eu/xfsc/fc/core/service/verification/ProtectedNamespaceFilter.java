package eu.xfsc.fc.core.service.verification;

import java.util.ArrayList;
import java.util.List;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.FilteredClaims;
import eu.xfsc.fc.core.pojo.FilteredModel;
import lombok.extern.slf4j.Slf4j;

/**
 * Filters triples that use the catalogue's protected RDF namespace from external inputs.
 *
 * <p>Applied at external ingestion boundaries (uploads, schema imports) to prevent
 * external actors from writing internal metadata. Internal services bypass this
 * filter by calling {@code GraphStore.addClaims()} directly.</p>
 */
@Slf4j
@Component
public class ProtectedNamespaceFilter {

  private final ProtectedNamespaceProperties properties;

  public ProtectedNamespaceFilter(ProtectedNamespaceProperties properties) {
    this.properties = properties;
  }

  /**
   * Filters claims that reference the protected namespace.
   *
   * <p>Only IRI objects are checked; literal values and blank nodes are never filtered,
   * even if they contain the namespace string.</p>
   *
   * @param claims the list of claims to filter
   * @param source description of the source for logging (e.g. "asset upload")
   * @return {@link FilteredClaims} containing the allowed claims and an optional user-visible warning
   */
  public FilteredClaims filterClaims(List<CredentialClaim> claims, String source) {
    if (claims == null || claims.isEmpty()) {
      return new FilteredClaims(claims, null);
    }

    String ns = properties.getNamespace();
    String namespaceIdentifier = "<" + ns;
    List<CredentialClaim> allowed = new ArrayList<>(claims.size());
    List<CredentialClaim> rejected = new ArrayList<>();

    for (CredentialClaim claim : claims) {
      if (claimUsesProtectedNamespace(claim, namespaceIdentifier)) {
        rejected.add(claim);
        log.debug("filterClaims; rejected triple from {}: {}", source, claim);
      } else {
        allowed.add(claim);
      }
    }

    if (!rejected.isEmpty()) {
      log.warn("filterClaims; filtered {} triple(s) using protected namespace '{}:' from {}",
          rejected.size(), properties.getPrefix(), source);
      return new FilteredClaims(allowed, buildUserWarning(rejected));
    }
    return new FilteredClaims(allowed, null);
  }

  private String buildUserWarning(List<CredentialClaim> rejected) {
    StringBuilder sb = new StringBuilder();
    sb.append(rejected.size()).append(" triple(s) were removed from your upload because they use the reserved")
        .append(" internal namespace <").append(properties.getNamespace()).append(">.")
        .append(" Removed triples:");
    for (CredentialClaim claim : rejected) {
      sb.append("\n  · ").append(claim.getSubjectString())
        .append(" ").append(claim.getPredicateString())
        .append(" ").append(claim.getObjectString());
    }
    sb.append("\nThis namespace is reserved for internal catalogue use only and cannot be set by external clients.");
    return sb.toString();
  }

  /**
   * Filters statements that reference the protected namespace from a Jena model.
   *
   * @param model the Jena model to filter
   * @param source description of the source for logging (e.g. "schema import")
   * @return {@link FilteredModel} containing the filtered model and an optional warning
   */
  public FilteredModel filterModel(Model model, String source) {
    if (model == null || model.isEmpty()) {
      return new FilteredModel(model, null);
    }

    String ns = properties.getNamespace();
    Model filtered = ModelFactory.createDefaultModel();
    filtered.setNsPrefixes(model.getNsPrefixMap());
    List<Statement> rejected = new ArrayList<>();

    StmtIterator iter = model.listStatements();
    while (iter.hasNext()) {
      Statement stmt = iter.nextStatement();
      if (statementUsesProtectedNamespace(stmt, ns)) {
        rejected.add(stmt);
        log.debug("filterModel; rejected statement from {}: {}", source, stmt);
      } else {
        filtered.add(stmt);
      }
    }

    if (!rejected.isEmpty()) {
      log.warn("filterModel; filtered {} statement(s) using protected namespace '{}:' from {}",
          rejected.size(), properties.getPrefix(), source);
      return new FilteredModel(filtered, buildModelWarning(rejected));
    }
    return new FilteredModel(filtered, null);
  }

  private String buildModelWarning(List<Statement> rejected) {
    StringBuilder sb = new StringBuilder();
    sb.append(rejected.size()).append(" statement(s) were removed from your schema because they use the reserved")
        .append(" internal namespace <").append(properties.getNamespace()).append(">.")
        .append(" Removed statements:");
    for (Statement stmt : rejected) {
      sb.append("\n  · ").append(stmt.getSubject())
        .append(" ").append(stmt.getPredicate())
        .append(" ").append(stmt.getObject());
    }
    sb.append("\nThis namespace is reserved for internal catalogue use only and cannot be set by external clients.");
    return sb.toString();
  }

  private boolean claimUsesProtectedNamespace(CredentialClaim claim, String angleBracketNs) {
    String subj = claim.getSubjectString();
    if (subj != null && subj.startsWith(angleBracketNs)) {
      return true;
    }

    String pred = claim.getPredicateString();
    if (pred != null && pred.startsWith(angleBracketNs)) {
      return true;
    }

    // Only filter IRI objects (start with '<'), not literals (start with '"') or blank nodes
    String obj = claim.getObjectString();
    if (obj != null && obj.startsWith(angleBracketNs)) {
      return true;
    }

    return false;
  }

  private boolean statementUsesProtectedNamespace(Statement stmt, String ns) {
    String subjUri = stmt.getSubject().isURIResource() ? stmt.getSubject().getURI() : null;
    if (subjUri != null && subjUri.startsWith(ns)) {
      return true;
    }

    String predUri = stmt.getPredicate().getURI();
    if (predUri != null && predUri.startsWith(ns)) {
      return true;
    }

    RDFNode obj = stmt.getObject();
    if (obj.isURIResource()) {
      String objUri = obj.asResource().getURI();
      if (objUri != null && objUri.startsWith(ns)) {
        return true;
      }
    }

    return false;
  }
}
