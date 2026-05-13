package eu.xfsc.fc.core.service.validation.rdf;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.apicatalog.jsonld.loader.DocumentLoader;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.verification.LoireJwtParser;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {RdfAssetParser.class})
class RdfAssetParserTest {

  private static final String TURTLE_PERSON =
      "@prefix ex: <http://example.org/> . ex:Alice a ex:Person ; ex:name \"Alice\" .";

  private static final String SHAPE_PERSON = """
      @prefix sh: <http://www.w3.org/ns/shacl#> .
      @prefix ex: <http://example.org/> .
      ex:PersonShape a sh:NodeShape ; sh:targetClass ex:Person .
      """;

  private static final String JSON_LD_PERSON = """
      {
        "@context": {"ex": "http://example.org/"},
        "@id": "http://example.org/Alice",
        "@type": "ex:Person",
        "ex:name": "Alice"
      }
      """;

  @MockitoBean(name = "contextCacheFileStore")
  private FileStore fileStore;
  @MockitoBean
  private DocumentLoader documentLoader;
  @MockitoBean
  private LoireJwtParser loireJwtParser;

  @Autowired
  private RdfAssetParser rdfAssetParser;

  @Test
  void parse_turtleAsset_returnsModel() {
    AssetMetadata asset = buildTurtleAsset("http://example.org/alice", TURTLE_PERSON);

    Model model = rdfAssetParser.parse(asset);

    assertNotNull(model);
    assertFalse(model.isEmpty(), "Parsed Turtle should produce a non-empty model");
  }

  @Test
  void parse_loireJwtAsset_unwrapsAndReturnsModel() {
    AssetMetadata asset = buildAssetWithContent("http://example.org/alice", "eyJfakeJwt");
    when(loireJwtParser.unwrap(any())).thenReturn(new ContentAccessorDirect(JSON_LD_PERSON));

    Model model = rdfAssetParser.parse(asset);

    assertNotNull(model);
    assertFalse(model.isEmpty(), "Parsed JSON-LD from unwrapped JWT should produce a non-empty model");
  }

  @Test
  void parseShape_turtleShape_returnsModel() {
    Model model = rdfAssetParser.parseShape(new ContentAccessorDirect(SHAPE_PERSON));

    assertNotNull(model);
    assertFalse(model.isEmpty(), "Parsed SHACL shape should produce a non-empty model");
  }

  @Test
  void isJsonLd_jsonLdContent_returnsTrue() {
    AssetMetadata asset = buildAssetWithContent("http://example.org/a", JSON_LD_PERSON.strip());

    assertTrue(rdfAssetParser.isJsonLd(asset));
  }

  @Test
  void isJsonLd_turtleContent_returnsFalse() {
    AssetMetadata asset = buildTurtleAsset("http://example.org/a", TURTLE_PERSON);

    assertFalse(rdfAssetParser.isJsonLd(asset));
  }

  @Test
  void isJsonLd_rdfXmlContent_returnsFalse() {
    String rdfXmlContent = "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"/>";
    AssetMetadata asset = buildAssetWithContent("http://example.org/a", rdfXmlContent);

    assertFalse(rdfAssetParser.isJsonLd(asset));
  }

  @Test
  void isRdfXml_xmlContent_returnsTrue() {
    String rdfXmlContent = "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\"/>";
    AssetMetadata asset = buildAssetWithContent("http://example.org/a", rdfXmlContent);

    assertTrue(rdfAssetParser.isRdfXml(asset));
  }

  @Test
  void isRdfXml_jsonContent_returnsFalse() {
    AssetMetadata asset = buildAssetWithContent("http://example.org/a", JSON_LD_PERSON.strip());

    assertFalse(rdfAssetParser.isRdfXml(asset));
  }

  
  @Test
  void isRdfXml_turtleContent_returnsFalse() {
    AssetMetadata asset = buildTurtleAsset("http://example.org/a", TURTLE_PERSON);

    assertFalse(rdfAssetParser.isRdfXml(asset));
  }


  private static AssetMetadata buildTurtleAsset(String id, String turtleContent) {
    AssetMetadata asset = new AssetMetadata();
    asset.setId(id);
    asset.setContentType("text/turtle");
    asset.setContentAccessor(new ContentAccessorDirect(turtleContent));
    return asset;
  }

  private static AssetMetadata buildAssetWithContent(String id, String content) {
    AssetMetadata asset = new AssetMetadata();
    asset.setId(id);
    asset.setContentAccessor(new ContentAccessorDirect(content));
    return asset;
  }
}
