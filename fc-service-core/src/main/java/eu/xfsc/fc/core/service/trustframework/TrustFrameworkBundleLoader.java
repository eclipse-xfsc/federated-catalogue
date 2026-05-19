package eu.xfsc.fc.core.service.trustframework;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Scans the classpath for trust-framework bundles under {@code trustframeworks/<bundleId>/framework.yaml}
 * and constructs a {@link TrustFrameworkBundle} for each.
 *
 * <p>When an override path is configured, bundles found on the filesystem at that path are merged
 * on top of the classpath bundles: a filesystem bundle with an existing {@code id} replaces the
 * classpath bundle; a filesystem bundle with a new {@code id} is appended to the list.
 */
@Slf4j
public class TrustFrameworkBundleLoader {

  private static final String BUNDLE_PATTERN = "classpath:trustframeworks/*/framework.yaml";
  private static final String FILESYSTEM_BUNDLE_PATTERN = "file:%s/*/framework.yaml";
  private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
      .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
      .build();

  private final String overridePath;

  /**
   * Constructs a loader with a filesystem override path.
   *
   * @param overridePath path to a directory containing bundle subdirectories; blank or {@code null} disables override
   */
  public TrustFrameworkBundleLoader(String overridePath) {
    this.overridePath = overridePath;
  }

  /**
   * Constructs a loader with no filesystem override path (classpath-only).
   */
  public TrustFrameworkBundleLoader() {
    this(null);
  }

  /**
   * Scans the classpath for {@code trustframeworks/<bundleId>/framework.yaml} files and loads each as a bundle.
   * If an override path is configured and the directory exists, bundles found there are merged on top: a filesystem
   * bundle with an existing {@code id} replaces the classpath bundle; a filesystem bundle with a new {@code id} is
   * appended. Non-loadable bundles are skipped with a warning; they do not abort the load of remaining bundles.
   */
  public List<TrustFrameworkBundle> load() throws IOException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] classpathYamls = resolver.getResources(BUNDLE_PATTERN);
    var byId = new LinkedHashMap<String, TrustFrameworkBundle>();
    for (Resource yaml : classpathYamls) {
      try {
        var bundle = loadBundle(yaml);
        byId.put(bundle.config().id(), bundle);
      } catch (Exception e) {
        log.warn("Skipping bundle at '{}' — failed to load: {}", yaml.getDescription(), e.getMessage());
      }
    }
    int classpathCount = byId.size();
    if (classpathCount == 0) {
      log.warn("No trust-framework bundles loaded from classpath — catalogue may not function correctly");
    } else {
      log.info("Loaded {} trust-framework bundle(s) from classpath", classpathCount);
    }

    int overrideCount = 0;
    if (overridePath != null && !overridePath.isBlank()) {
      var overrideDir = new File(overridePath);
      if (!overrideDir.exists() || !overrideDir.isDirectory()) {
        log.warn("Trust-framework override path '{}' does not exist or is not a directory — skipping", overridePath);
      } else {
        var pattern = String.format(FILESYSTEM_BUNDLE_PATTERN, overridePath);
        Resource[] fsYamls = resolver.getResources(pattern);
        for (Resource yaml : fsYamls) {
          try {
            var bundle = loadBundle(yaml);
            byId.put(bundle.config().id(), bundle);
            overrideCount++;
          } catch (Exception e) {
            log.warn("Skipping override bundle at '{}' — failed to load: {}", yaml.getDescription(), e.getMessage());
          }
        }
        log.info("Applied {} override bundle(s) from '{}'; final total: {}",
            overrideCount, overridePath, byId.size());
      }
    }

    return new ArrayList<>(byId.values());
  }

  /**
   * Scans the classpath for {@code trustframeworks/<bundleId>/framework.yaml} files and loads each as a bundle.
   * Non-loadable bundles are skipped with a warning; they do not abort the load of remaining bundles.
   *
   * @deprecated Use {@link #load()} instead.
   */
  @Deprecated
  public List<TrustFrameworkBundle> loadFromClasspath() throws IOException {
    return load();
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
