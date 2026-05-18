package eu.xfsc.fc.core.service.validation.rdf;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.verification.LoireJwtParser;
import eu.xfsc.fc.core.service.verification.VerificationConstants;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import eu.xfsc.fc.core.util.FormatDetectionConstants;
import eu.xfsc.fc.core.util.RdfFormatDetector;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * Parses RDF asset content and SHACL shapes into Jena {@link Model} instances.
 *
 * <p>This class is a Spring bean because it owns the application-scoped {@link StreamManager}
 * lifecycle. The {@link StreamManager} is initialised once in {@link #init()} and shared across
 * all parsing calls. {@link FileStore} is injected solely to back the {@link CachingLocator}
 * registered with that {@link StreamManager}; it is not used in any parsing method directly.</p>
 *
 * <p>The {@link #parse(AssetMetadata)} method supports two runtime branches:</p>
 * <ul>
 *   <li>Loire JWT (starts with {@code "eyJ"}): unwrapped via {@link LoireJwtParser},
 *       then parsed as JSON-LD 1.1.</li>
 *   <li>All non-JWT RDF content (including JSON-LD): format detected via
 *       {@link RdfFormatDetector}.</li>
 * </ul>
 *
 * <p>The helper methods {@link #isJsonLd(AssetMetadata)} and {@link #isRdfXml(AssetMetadata)}
 * are separate from parse-branching and are used only by validation strategies to determine
 * applicability.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RdfAssetParser {

  @Qualifier("contextCacheFileStore")
  private final FileStore fileStore;
  private final DocumentLoader documentLoader;
  private final LoireJwtParser loireJwtParser;

  private StreamManager streamManager;

  @PostConstruct
  void init() {
    SchemeRouter loader = (SchemeRouter) SchemeRouter.defaultInstance();
    loader.set("file", documentLoader);
    loader.set("http", documentLoader);
    loader.set("https", documentLoader);
    StreamManager clone = StreamManager.get().clone();
    clone.clearLocators();
    clone.addLocator(new CachingLocator(fileStore));
    streamManager = clone;
  }

  /**
   * Parses an RDF asset into a Jena {@link Model}.
   *
    * <p>Runtime flow:</p>
    * <ul>
    *   <li>JWT branch: unwrap via {@link LoireJwtParser} and parse as JSON-LD 1.1.</li>
    *   <li>Non-JWT branch: detect RDF syntax via {@link RdfFormatDetector}
    *       (including JSON-LD), then parse using the detected Jena {@link Lang}.</li>
    * </ul>
   *
   * @param asset asset with non-null {@link AssetMetadata#getContentAccessor()}
   * @throws ClientException if the asset has no content or the RDF content is malformed
   */
  public Model parse(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();
    if (content == null) {
      throw new ClientException(
          "Asset " + asset.getId() + " is not an RDF asset and cannot be validated with SHACL");
    }
    String rawContent = content.getContentAsString().strip();
    if (rawContent.startsWith(VerificationConstants.JWT_PREFIX)) {
      ContentAccessor unwrapped = loireJwtParser.unwrap(content);
      return parseRdfContent(unwrapped, Lang.JSONLD11);
    }
    Lang lang = RdfFormatDetector.detect(asset.getContentType(), rawContent);
    return parseRdfContent(content, lang);
  }

  /**
   * Parses a SHACL shapes document (Turtle) into a Jena {@link Model}.
   *
   * @param shape ContentAccessor containing a valid SHACL Turtle document
   * @throws ClientException if the Turtle content is malformed
   */
  public Model parseShape(ContentAccessor shape) {
    try {
      Model model = ModelFactory.createDefaultModel();
      RDFParser.create()
          .streamManager(streamManager)
          .source(shape.getContentAsStream())
          .lang(Lang.TURTLE)
          .parse(model);
      return model;
    } catch (RiotException e) {
      throw new ClientException("Invalid SHACL shape: " + e.getMessage(), e);
    }
  }

  /**
   * Returns {@code true} if the asset content is JSON-LD (content starts with '{').
   * Falls back to content-type inspection for assets without a content accessor.
   */
  public boolean isJsonLd(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();
    if (content != null) {
      return content.getContentAsString().strip().startsWith(FormatDetectionConstants.JSON_LD_PREFIX);
    }
    String ct = asset.getContentType();
    return ct != null && (ct.contains(VerificationConstants.MEDIA_TYPE_LD_JSON)
        || ct.contains(VerificationConstants.MEDIA_TYPE_VC_LD_JSON)
        || ct.contains(VerificationConstants.MEDIA_TYPE_VP_LD_JSON));
  }

  /**
   * Returns {@code true} if the asset content is RDF/XML (content starts with {@code "<?xml"}
   * or {@code "<rdf:RDF"}). Falls back to content-type inspection for assets without a content accessor.
   */
  public boolean isRdfXml(AssetMetadata asset) {
    ContentAccessor content = asset.getContentAccessor();
    if (content != null) {
      String raw = content.getContentAsString().strip();
      return raw.startsWith(FormatDetectionConstants.RDF_XML_PREFIX_1)
          || raw.startsWith(FormatDetectionConstants.RDF_XML_PREFIX_2);
    }
    String ct = asset.getContentType();
    return ct != null && ct.contains(VerificationConstants.MEDIA_TYPE_RDF_XML);
  }

  private Model parseRdfContent(ContentAccessor content, Lang lang) {
    try {
      Model model = ModelFactory.createDefaultModel();
      RDFParser.create()
          .streamManager(streamManager)
          .source(content.getContentAsStream())
          .lang(lang)
          .parse(model);
      return model;
    } catch (RiotException e) {
      throw new ClientException("Invalid RDF asset content: " + e.getMessage(), e);
    }
  }
}
