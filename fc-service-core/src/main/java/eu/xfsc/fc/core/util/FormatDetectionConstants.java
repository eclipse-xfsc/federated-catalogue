package eu.xfsc.fc.core.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Shared string prefixes used by content-based format detection heuristics.
 *
 * <p>For JWT detection use {@code VerificationConstants.JWT_PREFIX} directly — JWT is a
 * credential-envelope concern, not an RDF format heuristic, and lives in the verification
 * package as the single source of truth.</p>
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class FormatDetectionConstants {

  public static final String JSON_LD_PREFIX = "{";
  public static final String RDF_XML_PREFIX_1 = "<?xml";
  public static final String RDF_XML_PREFIX_2 = "<rdf:RDF";
  public static final String TURTLE_PREFIX_1 = "@prefix";
  public static final String TURTLE_PREFIX_2 = "@base";
}
