package eu.xfsc.fc.core.pojo;

import org.apache.jena.rdf.model.Model;

/**
 * Result of filtering a Jena RDF model against the protected RDF namespace.
 * {@code warning} is {@code null} when nothing was filtered.
 *
 * @see eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter
 */
public record FilteredModel(Model model, String warning) {

  public boolean hasWarning() {
    return warning != null;
  }

}
