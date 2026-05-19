package eu.xfsc.fc.core.service.trustframework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

class TrustFrameworkBundleLoaderTest {

  private final TrustFrameworkBundleLoader loader = new TrustFrameworkBundleLoader();

  private static final String MINIMAL_YAML =
      "id: test-fw\nfamily: test\nnamespace: \"https://example.org/test#\"\nvalidation_type: shacl\n";

  // ──────────────────────────────────────────────────────────────────
  // Patch 1 — loadSibling: InputStream not closed on readAllBytes exception
  // ──────────────────────────────────────────────────────────────────

  @Test
  void loadBundle_siblingStream_closedEvenWhenReadThrows() throws Exception {
    var streamClosed = new AtomicBoolean(false);

    // Sibling stream that throws during bulk-read but tracks close()
    var failingStream = new FilterInputStream(InputStream.nullInputStream()) {
      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        throw new IOException("simulated read error");
      }

      @Override
      public void close() throws IOException {
        streamClosed.set(true);
        super.close();
      }
    };

    var siblingResource = mock(Resource.class);
    when(siblingResource.exists()).thenReturn(true);
    when(siblingResource.getInputStream()).thenReturn(failingStream);

    var noSibling = mock(Resource.class);
    when(noSibling.exists()).thenReturn(false);

    var yamlResource = mock(Resource.class);
    when(yamlResource.getInputStream())
        .thenAnswer(inv -> new ByteArrayInputStream(MINIMAL_YAML.getBytes(StandardCharsets.UTF_8)));
    when(yamlResource.createRelative("ontology.ttl")).thenReturn(siblingResource);
    when(yamlResource.createRelative("shapes.ttl")).thenReturn(noSibling);
    when(yamlResource.getFilename()).thenReturn("framework.yaml");

    // Read error in sibling is caught; bundle is returned with null ontology
    var bundle = loader.loadBundle(yamlResource);
    assertThat(bundle.ontology()).isNull();

    assertThat(streamClosed.get())
        .as("sibling InputStream must be closed even when readAllBytes() throws")
        .isTrue();
  }

  // ──────────────────────────────────────────────────────────────────
  // Patch 2 — loadBundle: YAML InputStream not closed on parse error
  // ──────────────────────────────────────────────────────────────────

  @Test
  void loadBundle_yamlStream_closedEvenWhenIOExceptionDuringRead() throws Exception {
    var streamClosed = new AtomicBoolean(false);

    // Stream that throws on every read, simulating mid-parse I/O failure
    var failingStream = new InputStream() {
      @Override
      public int read() throws IOException {
        throw new IOException("forced I/O error during YAML read");
      }

      @Override
      public void close() throws IOException {
        streamClosed.set(true);
      }
    };

    var yamlResource = mock(Resource.class);
    when(yamlResource.getInputStream()).thenReturn(failingStream);
    when(yamlResource.getFilename()).thenReturn("framework.yaml");

    assertThatThrownBy(() -> loader.loadBundle(yamlResource))
        .isInstanceOf(Exception.class);

    assertThat(streamClosed.get())
        .as("YAML InputStream must be closed even when Jackson throws during parse")
        .isTrue();
  }

  // ──────────────────────────────────────────────────────────────────
  // Patch 3 — loadBundles: yaml.getURI() in catch block aborts the loop
  // ──────────────────────────────────────────────────────────────────

  @Test
  void loadBundles_getURIThrowsInCatch_doesNotAbortRemainingBundles() throws Exception {
    // First resource: YAML parse fails AND getURI() throws in the catch block
    var brokenYaml = mock(Resource.class);
    when(brokenYaml.getInputStream())
        .thenAnswer(inv -> new ByteArrayInputStream("{{invalid".getBytes(StandardCharsets.UTF_8)));
    when(brokenYaml.getURI()).thenThrow(new IOException("getURI() failed"));
    when(brokenYaml.getDescription()).thenReturn("mock://broken");
    when(brokenYaml.getFilename()).thenReturn("framework.yaml");
    var noSiblingBroken = mock(Resource.class);
    when(noSiblingBroken.exists()).thenReturn(false);
    when(brokenYaml.createRelative(anyString())).thenReturn(noSiblingBroken);

    // Second resource: valid bundle that must still be loaded
    var goodYaml = buildYamlResourceForId("good-fw");

    var bundles = loader.loadBundles(new Resource[] {brokenYaml, goodYaml});

    assertThat(bundles)
        .as("valid bundle must load even when the preceding bundle's getURI() throws in catch")
        .hasSize(1);
    assertThat(bundles.get(0).config().id()).isEqualTo("good-fw");
  }

  // ──────────────────────────────────────────────────────────────────
  // Patch 4 — loadBundles: zero-bundle result logged at INFO, not WARN
  // ──────────────────────────────────────────────────────────────────

  @Test
  void loadBundles_emptyArray_logsAtWARN() throws Exception {
    var logs = startCapturingLogs(TrustFrameworkBundleLoader.class);

    loader.loadBundles(new Resource[0]);

    assertThat(logs.list)
        .as("loading zero bundles must produce at least one WARN log")
        .anyMatch(e -> e.getLevel() == Level.WARN);
  }

  // ──────────────────────────────────────────────────────────────────
  // Patch 5 — loadBundle: null config.id() silently registered in bundleIndex
  // ──────────────────────────────────────────────────────────────────

  @Test
  void loadBundle_nullId_throwsIllegalArgumentException() throws Exception {
    var noIdYaml = "family: test\nnamespace: \"https://example.org/test#\"\nvalidation_type: shacl\n";
    var yamlResource = buildYamlResource(noIdYaml);

    assertThatThrownBy(() -> loader.loadBundle(yamlResource))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("id");
  }

  // ──────────────────────────────────────────────────────────────────
  // Filesystem override tests (load())
  // ──────────────────────────────────────────────────────────────────

  private static final String CLASSPATH_BUNDLE_ID = "gaia-x-2511";
  private static final String OVERRIDE_TRUST_ANCHOR_URL = "https://override.example.com/trustanchor";
  private static final String NEW_BUNDLE_ID = "custom-framework-test";

  @Test
  void load_overridePathBlank_returnsClasspathBundlesOnly() throws IOException {
    var loaderNoOverride = new TrustFrameworkBundleLoader("");

    var bundles = loaderNoOverride.load();

    assertThat(bundles)
        .as("blank override path must return classpath bundles unchanged")
        .anyMatch(b -> CLASSPATH_BUNDLE_ID.equals(b.config().id()));
    assertThat(bundles)
        .as("no extra bundles should be injected when override path is blank")
        .noneMatch(b -> NEW_BUNDLE_ID.equals(b.config().id()));
  }

  @Test
  void load_overridePathPointsToBundleWithSameId_overridesClasspathBundle(@TempDir Path tempDir) throws IOException {
    String overrideYaml = "id: " + CLASSPATH_BUNDLE_ID + "\n"
        + "family: gaia-x\n"
        + "namespace: \"https://w3id.org/gaia-x/2511#\"\n"
        + "validation_type: shacl\n"
        + "properties:\n"
        + "  trust_anchor_url: \"" + OVERRIDE_TRUST_ANCHOR_URL + "\"\n";
    Path bundleDir = tempDir.resolve(CLASSPATH_BUNDLE_ID);
    Files.createDirectories(bundleDir);
    Files.writeString(bundleDir.resolve("framework.yaml"), overrideYaml);

    var loaderWithOverride = new TrustFrameworkBundleLoader(tempDir.toString());

    var bundles = loaderWithOverride.load();

    var overridden = bundles.stream()
        .filter(b -> CLASSPATH_BUNDLE_ID.equals(b.config().id()))
        .findFirst();
    assertThat(overridden)
        .as("bundle with id '" + CLASSPATH_BUNDLE_ID + "' must be present after override")
        .isPresent();
    assertThat(overridden.get().config().properties().get("trust_anchor_url"))
        .as("trust_anchor_url must match the filesystem override, not the classpath default")
        .isEqualTo(OVERRIDE_TRUST_ANCHOR_URL);
  }

  @Test
  void load_overridePathPointsToBundleWithNewId_addsBundle(@TempDir Path tempDir) throws IOException {
    String newBundleYaml = "id: " + NEW_BUNDLE_ID + "\n"
        + "family: custom\n"
        + "namespace: \"https://example.com/custom#\"\n"
        + "validation_type: shacl\n";
    Path bundleDir = tempDir.resolve(NEW_BUNDLE_ID);
    Files.createDirectories(bundleDir);
    Files.writeString(bundleDir.resolve("framework.yaml"), newBundleYaml);

    var loaderWithOverride = new TrustFrameworkBundleLoader(tempDir.toString());

    var bundles = loaderWithOverride.load();

    assertThat(bundles)
        .as("new bundle id must appear in the returned list")
        .anyMatch(b -> NEW_BUNDLE_ID.equals(b.config().id()));
    assertThat(bundles.stream().filter(b -> NEW_BUNDLE_ID.equals(b.config().id())).count())
        .as("new bundle must appear exactly once")
        .isEqualTo(1);
  }

  @Test
  void load_overridePathDoesNotExist_logsWarningAndReturnsClasspathOnly() throws IOException {
    var logs = startCapturingLogs(TrustFrameworkBundleLoader.class);
    String nonExistentPath = "/tmp/nonexistent-trust-override-" + System.nanoTime();
    var loaderBadPath = new TrustFrameworkBundleLoader(nonExistentPath);

    var bundles = loaderBadPath.load();

    assertThat(bundles)
        .as("classpath bundles must still be returned when override path does not exist")
        .anyMatch(b -> CLASSPATH_BUNDLE_ID.equals(b.config().id()));
    assertThat(logs.list)
        .as("a WARN must be logged when the override directory does not exist")
        .anyMatch(e -> e.getLevel() == Level.WARN
            && e.getFormattedMessage().contains(nonExistentPath));
  }

  // ──────────────────────────────────────────────────────────────────
  // Helpers
  // ──────────────────────────────────────────────────────────────────

  /**
   * Builds a minimal valid Resource for a bundle with the given id.
   */
  private static Resource buildYamlResourceForId(String id) throws IOException {
    String yaml = "id: " + id + "\nfamily: test\nnamespace: \"https://example.org/" + id + "#\"\n"
        + "validation_type: shacl\n";
    return buildYamlResource(yaml);
  }

  /**
   * Builds a Resource whose getInputStream() returns fresh copies of the given YAML content.
   * createRelative() returns a non-existent sibling so TTL loading is skipped.
   */
  private static Resource buildYamlResource(String yamlContent) throws IOException {
    var bytes = yamlContent.getBytes(StandardCharsets.UTF_8);
    var resource = mock(Resource.class);
    when(resource.getInputStream()).thenAnswer(inv -> new ByteArrayInputStream(bytes));
    when(resource.getDescription()).thenReturn("mock://test-bundle");
    when(resource.getFilename()).thenReturn("framework.yaml");
    var noSibling = mock(Resource.class);
    when(noSibling.exists()).thenReturn(false);
    when(resource.createRelative(anyString())).thenReturn(noSibling);
    return resource;
  }

  private static ListAppender<ILoggingEvent> startCapturingLogs(Class<?> cls) {
    var logger = (Logger) LoggerFactory.getLogger(cls);
    var appender = new ListAppender<ILoggingEvent>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }
}
