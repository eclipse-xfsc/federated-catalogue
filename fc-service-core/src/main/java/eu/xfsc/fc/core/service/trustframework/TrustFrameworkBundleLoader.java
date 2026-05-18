package eu.xfsc.fc.core.service.trustframework;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Scans the classpath for trust-framework bundles under {@code trustframeworks/<bundleId>/framework.yaml}
 * and constructs a {@link TrustFrameworkBundle} for each.
 */
@Slf4j
public class TrustFrameworkBundleLoader {

  private static final String BUNDLE_PATTERN = "classpath:trustframeworks/*/framework.yaml";
  private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build();

  /**
   * Scans the classpath for {@code trustframeworks/<bundleId>/framework.yaml} files and loads each as a bundle.
   * Non-loadable bundles are skipped with a warning; they do not abort the load of remaining bundles.
   */
  public List<TrustFrameworkBundle> loadFromClasspath() throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] yamls = resolver.getResources(BUNDLE_PATTERN);
    return loadBundles(yamls);
  }

  /**
   * Loads bundles from an explicit array of YAML resources.
   * Non-loadable bundles are skipped with a warning; they do not abort the load of remaining bundles.
   * Package-private for testing.
   */
  List<TrustFrameworkBundle> loadBundles(Resource[] yamls) {
    var bundles = new ArrayList<TrustFrameworkBundle>(yamls.length);
    for (Resource yaml : yamls) {
      try {
        bundles.add(loadBundle(yaml));
      } catch (Exception e) {
        // getDescription() never throws, unlike getURI() which declares throws IOException
        log.warn("Skipping bundle at '{}' — failed to load: {}", yaml.getDescription(), e.getMessage());
      }
    }
    if (bundles.isEmpty()) {
      log.warn("No trust-framework bundles loaded — catalogue may not function correctly");
    } else {
      log.info("Loaded {} trust-framework bundle(s) from classpath", bundles.size());
    }
    return bundles;
  }

  /**
   * Loads a single bundle from the given framework.yaml resource.
   * Package-private for testing.
   */
  TrustFrameworkBundle loadBundle(Resource yamlResource) throws IOException {
    FrameworkBundleConfig config;
    try (var stream = yamlResource.getInputStream()) {
      config = YAML_MAPPER.readValue(stream, FrameworkBundleConfig.class);
    }
    // null id would silently register the bundle under key null in bundleIndex
    if (config.id() == null || config.id().isBlank()) {
      throw new IllegalArgumentException(
          "Bundle at '" + yamlResource.getDescription() + "' is missing the required 'id' field");
    }
    var ontology = loadSibling(yamlResource, "ontology.ttl");
    var shapes = loadSibling(yamlResource, "shapes.ttl");
    log.debug("Loaded bundle '{}' (validationType={})", config.id(), config.validationType());
    return new TrustFrameworkBundle(config, ontology, shapes);
  }

  private static ContentAccessorDirect loadSibling(Resource yamlResource, String filename) {
    try {
      Resource sibling = yamlResource.createRelative(filename);
      if (!sibling.exists()) {
        return null;
      }
      try (var stream = sibling.getInputStream()) {
        return new ContentAccessorDirect(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
      }
    } catch (IOException e) {
      log.warn("Could not load '{}' alongside '{}': {}", filename, yamlResource.getFilename(), e.getMessage());
      return null;
    }
  }
}
