package eu.xfsc.fc.api;

import org.springframework.http.MediaType;

/**
 * Wire-format media types specific to the Federated Catalogue API.
 *
 * <p>Every media type is exposed as a {@link String} value. A {@link MediaType} companion is
 * added only where a caller needs that form: annotations and MockMvc
 * {@code .contentType(String)} take the string, WebClient and similar take the
 * {@code MediaType}.
 */
public final class FcMediaTypes {

  /**
   * Merge-patch content type per RFC 7396.
   */
  public static final String MERGE_PATCH_JSON_VALUE = "application/merge-patch+json";

  /**
   * {@link MediaType} form of {@link #MERGE_PATCH_JSON_VALUE}.
   */
  public static final MediaType MERGE_PATCH_JSON = MediaType.valueOf(MERGE_PATCH_JSON_VALUE);

  /**
   * Verifiable Presentation JWT content type (W3C VC-JOSE-COSE).
   */
  public static final String VP_JWT_VALUE = "application/vp+jwt";

  /**
   * {@link MediaType} form of {@link #VP_JWT_VALUE}.
   */
  public static final MediaType VP_JWT = MediaType.valueOf(VP_JWT_VALUE);

  /**
   * Verifiable Credential JWT content type (W3C VC-JOSE-COSE).
   */
  public static final String VC_JWT_VALUE = "application/vc+jwt";

  /**
   * Verifiable Credential JSON-LD content type (W3C VC-JOSE-COSE).
   */
  public static final String VC_LD_JSON_VALUE = "application/vc+ld+json";

  /**
   * Verifiable Presentation JSON-LD content type (W3C VC-JOSE-COSE).
   */
  public static final String VP_LD_JSON_VALUE = "application/vp+ld+json";

  /**
   * JSON-LD content type.
   */
  public static final String LD_JSON_VALUE = "application/ld+json";

  /**
   * Turtle RDF serialisation content type.
   */
  public static final String TURTLE_VALUE = "text/turtle";

  /**
   * N-Triples RDF serialisation content type.
   */
  public static final String NTRIPLES_VALUE = "application/n-triples";

  /**
   * RDF/XML serialisation content type.
   */
  public static final String RDF_XML_VALUE = "application/rdf+xml";

  private FcMediaTypes() {
  }
}
