package eu.xfsc.fc.core.util;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import lombok.experimental.UtilityClass;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFLanguages;
import org.springframework.http.MediaType;

/**
 * Detects the RDF serialization format from an HTTP Content-Type header and/or content inspection.
 *
 * <p>Content-type is checked first using Jena's built-in media-type mapper.
 * If the content type is absent or unrecognised, the raw content string is inspected by prefix.
 * Throws {@link eu.xfsc.fc.core.exception.ClientException} when neither check produces a match.</p>
 *
 * <p>Also exposes content prefix constants shared with {@link eu.xfsc.fc.core.service.validation.rdf.RdfAssetParser}.</p>
 */
@UtilityClass
public class RdfFormatDetector {

  /**
   * Detects the RDF format from content-type and optional content inspection.
   *
   * @param contentType HTTP Content-Type value, or {@code null}
   * @param rawContent  raw content string for fallback inspection; {@code null} skips inspection
   * @return the detected {@link Lang}
   * @throws ClientException if the format cannot be determined from either the content-type or the content
   */
  public static Lang detect(String contentType, String rawContent) {
    if (contentType != null) {
      Lang mappedLang = RDFLanguages.contentTypeToLang(contentType);
      if (mappedLang != null) {
        // Keep detector output stable across Jena mappings for ld+json.
        return mappedLang == Lang.JSONLD ? Lang.JSONLD11 : mappedLang;
      }
      // VC/VP-specific JSON-LD media types are not registered with Jena's mapper.
      if (contentType.contains(VerificationConstants.MEDIA_TYPE_VC_LD_JSON)
          || contentType.contains(VerificationConstants.MEDIA_TYPE_VP_LD_JSON)
          || contentType.contains(VerificationConstants.MEDIA_TYPE_LD_JSON)
          || contentType.contains(MediaType.APPLICATION_JSON_VALUE)) {
        return Lang.JSONLD11;
      }
    }
    if (rawContent != null) {
      if (rawContent.startsWith(FormatDetectionConstants.JSON_LD_PREFIX)) {
        return Lang.JSONLD11;
      }
      if (rawContent.startsWith(FormatDetectionConstants.TURTLE_PREFIX_1)
          || rawContent.startsWith(FormatDetectionConstants.TURTLE_PREFIX_2)) {
        return Lang.TURTLE;
      }
      if (rawContent.startsWith(FormatDetectionConstants.RDF_XML_PREFIX_1)
          || rawContent.startsWith(FormatDetectionConstants.RDF_XML_PREFIX_2)) {
        return Lang.RDFXML;
      }
      // N-Triples have no keyword prefix. IRI subjects start with '<' (RDF/XML is already matched above),
      // blank-node subjects start with '_:', and comment lines start with '#'.
      if (rawContent.startsWith("<") || rawContent.startsWith("_:") || rawContent.startsWith("#")) {
        return Lang.NT;
      }
    }
    throw new ClientException(
        "Cannot determine RDF format: unrecognised content-type '" + contentType
        + "' and content does not match any known RDF serialization");
  }
}
