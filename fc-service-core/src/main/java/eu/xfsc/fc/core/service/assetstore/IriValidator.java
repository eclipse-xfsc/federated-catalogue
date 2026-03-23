package eu.xfsc.fc.core.service.assetstore;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Validates IRI formats accepted as asset identifiers.
 *
 * <p>Returns {@code true}/{@code false} — invalid input is a normal outcome,
 * not an exception.
 *
 * <p>Supported formats:
 * <ul>
 *   <li>{@code did:{method}:{method-specific-id}} — Decentralized Identifiers (DID Core spec)</li>
 *   <li>{@code urn:uuid:{uuid-v4}} — UUID URNs per RFC 4122</li>
 *   <li>{@code urn:{nid}:{nss}} — Generic URN syntax per RFC 8141</li>
 *   <li>{@code http://...} or {@code https://...} — HTTP IRIs</li>
 * </ul>
 */
@Component
public class IriValidator {

  // DID syntax: did:method-name:method-specific-id
  // method-name = 1*methodchar, methodchar = %x61-7A / DIGIT (lowercase + digits)
  // method-specific-id allows alphanumeric, dots, dashes, underscores, percent-encoded, colons
  private static final Pattern DID_PATTERN =
      Pattern.compile("^did:[a-z0-9]+:[a-zA-Z0-9._:%-]+$");

  // UUID v4 URN: urn:uuid:xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
  private static final Pattern UUID_URN_PATTERN =
      Pattern.compile("^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$",
                      Pattern.CASE_INSENSITIVE);

  // Generic URN: urn:nid:nss (nid = 2-32 alphanumeric/hyphens, nss = at least 1 char)
  private static final Pattern URN_PATTERN =
      Pattern.compile("^urn:[a-zA-Z0-9][a-zA-Z0-9-]{0,31}:.+$");

  /**
   * Check whether the given string is an accepted IRI format.
   *
   * @param iri the IRI to check
   * @return {@code true} if valid, {@code false} otherwise
   */
  public boolean isValid(String iri) {
    if (iri == null || iri.isBlank()) {
      return false;
    }

    if (iri.startsWith("did:")) {
      return isValidDid(iri);
    } else if (iri.startsWith("urn:uuid:")) {
      return isValidUuidUrn(iri);
    } else if (iri.startsWith("urn:")) {
      return isValidUrn(iri);
    } else if (iri.startsWith("http://") || iri.startsWith("https://")) {
      return isValidHttpIri(iri);
    }
    return false;
  }

  private boolean isValidDid(String did) {
    return DID_PATTERN.matcher(did).matches();
  }

  private boolean isValidUuidUrn(String urn) {
    return UUID_URN_PATTERN.matcher(urn).matches();
  }

  private boolean isValidUrn(String urn) {
    return URN_PATTERN.matcher(urn).matches();
  }

  private boolean isValidHttpIri(String iri) {
    try {
      URI uri = new URI(iri);
      return uri.getHost() != null && !uri.getHost().isBlank();
    } catch (URISyntaxException e) {
      return false;
    }
  }
}
