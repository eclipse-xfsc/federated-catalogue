package eu.xfsc.fc.core.service.trustframework;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Scans the classpath for trust-framework bundles under {@code trustframeworks/<bundleId>/framework.yaml}
 * and constructs a {@link TrustFrameworkBundle} for each.
 *
 * <p>When an override path is configured, filesystem bundles found there are applied on top of classpath bundles
 * using one of two strategies:
 * <ul>
 *   <li><strong>Overlay</strong> — if the filesystem YAML's {@code id} matches an existing classpath bundle, the
 *       filesystem map is deep-merged onto the classpath config (scalar and nested-map fields are overridden per key;
 *       list fields are replaced wholesale). Sibling files ({@code ontology.ttl}, {@code shapes.ttl}) are inherited
 *       from the classpath bundle when the filesystem directory does not provide its own copy.</li>
 *   <li><strong>Add</strong> — if the {@code id} is brand-new (no classpath match), the filesystem bundle is loaded
 *       as a full bundle via the normal {@link #loadBundle(Resource)} path and appended to the result.</li>
 * </ul>
 * Filesystem YAMLs without an {@code id} field are skipped with a WARN log. Non-loadable bundles do not abort
 * the load of remaining bundles.
 */
@Slf4j
public class TrustFrameworkBundleLoader {

  private static final String BUNDLE_PATTERN = "classpath:trustframeworks/*/framework.yaml";
  private static final String FILESYSTEM_BUNDLE_PATTERN = "file:%s/*/framework.yaml";
  private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
  };
  private static final YAMLMapper YAML_MAPPER = YAMLMapper.builder()
      .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
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
   *
   * <p>If an override path is configured and exists, each filesystem bundle is applied as either an
   * <em>overlay</em> (deep-merge onto a classpath bundle with the same {@code id}) or an <em>add</em>
   * (appended as a new bundle when no classpath bundle with that {@code id} exists).
   *
   * <p>Overlay semantics: nested maps are recursively merged with filesystem keys winning at the leaf level;
   * scalars are replaced when the filesystem provides a value; lists are replaced wholesale.
   * Sibling files ({@code ontology.ttl}, {@code shapes.ttl}) are inherited from the classpath bundle when
   * the filesystem directory does not supply its own copy.
   *
   * <p>Non-loadable bundles are skipped with a warning; they do not abort the load of remaining bundles.
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
    applyFilesystemOverrides(resolver, byId, classpathCount);
    return new ArrayList<>(byId.values());
  }

  private void applyFilesystemOverrides(PathMatchingResourcePatternResolver resolver,
                                        LinkedHashMap<String, TrustFrameworkBundle> byId, int classpathCount)
      throws IOException {
    if (overridePath == null || overridePath.isBlank()) {
      return;
    }
    var overrideDir = new File(overridePath);
    if (!overrideDir.exists() || !overrideDir.isDirectory()) {
      log.warn("Trust-framework override path '{}' does not exist or is not a directory — skipping", overridePath);
      return;
    }
    var pattern = String.format(FILESYSTEM_BUNDLE_PATTERN, overridePath);
    Resource[] fsYamls = resolver.getResources(pattern);
    int overlayCount = 0;
    int addedCount = 0;
    for (Resource yaml : fsYamls) {
      try {
        Map<String, Object> fsMap;
        try (var stream = yaml.getInputStream()) {
          fsMap = YAML_MAPPER.readValue(stream, MAP_TYPE);
        }
        Object rawId = fsMap.get("id");
        if (rawId == null || rawId.toString().isBlank()) {
          log.warn("Skipping override at '{}' — missing 'id' field", yaml.getDescription());
          continue;
        }
        String id = rawId.toString();
        TrustFrameworkBundle existing = byId.get(id);
        if (existing != null) {
          Map<String, Object> baseMap = YAML_MAPPER.convertValue(existing.config(), MAP_TYPE);
          Map<String, Object> merged = deepMerge(baseMap, fsMap);
          FrameworkBundleConfig mergedConfig = YAML_MAPPER.convertValue(merged, FrameworkBundleConfig.class);
          var fsOntology = loadSibling(yaml, "ontology.ttl");
          var fsShapes = loadSibling(yaml, "shapes.ttl");
          var ontology = fsOntology != null ? fsOntology : existing.ontology();
          var shapes = fsShapes != null ? fsShapes : existing.shapes();
          byId.put(id, new TrustFrameworkBundle(mergedConfig, ontology, shapes));
          overlayCount++;
        } else {
          var bundle = loadBundle(yaml);
          byId.put(bundle.config().id(), bundle);
          addedCount++;
        }
      } catch (Exception e) {
        log.warn("Skipping override at '{}' — failed: {}", yaml.getDescription(), e.getMessage());
      }
    }
    log.info("Trust-framework bundles loaded — classpath={}, overlay={}, added={}, total={}, overridePath={}",
        classpathCount, overlayCount, addedCount, byId.size(), overridePath);
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

  /**
   * Produces a new map by deep-merging {@code patch} onto {@code base}.
   *
   * <p>Merge rules:
   * <ul>
   *   <li>If both values for a key are {@link Map}s, the values are recursively merged.</li>
   *   <li>In all other cases (scalar, list, null, or type mismatch), the patch value wins.</li>
   * </ul>
   * Neither input map is mutated.
   *
   * @param base  the map providing default values
   * @param patch the map whose values take precedence
   * @return a new map containing the merged result
   */
  @SuppressWarnings("unchecked") // safe: both values are verified to be Map<String,Object> before cast
  private static Map<String, Object> deepMerge(Map<String, Object> base, Map<String, Object> patch) {
    var result = new LinkedHashMap<>(base);
    for (Map.Entry<String, Object> entry : patch.entrySet()) {
      String key = entry.getKey();
      Object patchValue = entry.getValue();
      Object baseValue = result.get(key);
      if (baseValue instanceof Map && patchValue instanceof Map) {
        result.put(key, deepMerge((Map<String, Object>) baseValue, (Map<String, Object>) patchValue));
      } else {
        result.put(key, patchValue);
      }
    }
    return result;
  }
}
