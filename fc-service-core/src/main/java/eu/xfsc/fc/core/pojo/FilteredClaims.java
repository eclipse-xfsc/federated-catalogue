package eu.xfsc.fc.core.pojo;

import java.util.List;

/**
 * Result of filtering claims against the protected RDF namespace.
 * {@code warning} is {@code null} when nothing was filtered.
 *
 * @see eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter
 */
public record FilteredClaims(List<SdClaim> claims, String warning) {

  public boolean hasWarning() {
    return warning != null;
  }

}
