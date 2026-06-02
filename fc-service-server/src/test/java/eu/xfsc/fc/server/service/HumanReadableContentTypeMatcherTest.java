package eu.xfsc.fc.server.service;

import eu.xfsc.fc.server.config.AssetProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the human-readable companion content-type filter. The accepted set is the intersection
 * of a family-aware allowlist with an exact-match denylist. Browser-executable scripting types,
 * inline-scriptable SVG, and opaque/native-binary types are refused even when the broader family
 * is admitted.
 */
class HumanReadableContentTypeMatcherTest {

  private final HumanReadableContentTypeMatcher matcher = new HumanReadableContentTypeMatcher(new AssetProperties());

  @ParameterizedTest
  @ValueSource(strings = {
      // exact entries
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "application/json",
      "application/ld+json",
      "application/xml",
      "application/yaml",
      "application/x-yaml",
      // text/* family
      "text/html",
      "text/plain",
      "text/markdown",
      "text/csv",
      "text/yaml",
      "text/x-rst",
      "text/asciidoc",
      // image/* family
      "image/png",
      "image/jpeg",
      "image/webp",
      "image/gif",
      // audio/* family
      "audio/mpeg",
      "audio/wav",
      "audio/ogg",
      // video/* family
      "video/mp4",
      "video/webm"
  })
  void isAccepted_typeOnDefaultAllowlist_returnsTrue(String contentType) {
    assertThat(matcher.isAccepted(contentType)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "text/javascript",
      "text/ecmascript",
      "text/vbscript",
      "application/javascript",
      "application/ecmascript",
      "application/x-javascript",
      "image/svg+xml",
      "application/octet-stream",
      "application/x-msdownload",
      "application/x-executable",
      "application/x-sh",
      "application/x-shockwave-flash",
      "application/x-httpd-php"
  })
  void isAccepted_typeOnDefaultDenylist_returnsFalse(String contentType) {
    assertThat(matcher.isAccepted(contentType)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "model/gltf+json",
      "multipart/form-data",
      "message/rfc822",
      "chemical/x-pdb"
  })
  void isAccepted_typeOutsideEveryAllowedFamily_returnsFalse(String contentType) {
    assertThat(matcher.isAccepted(contentType)).isFalse();
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "text/markdown; charset=utf-8",
      "application/json; charset=UTF-8",
      "text/plain;charset=ISO-8859-1"
  })
  void isAccepted_typeWithCharsetParameter_strippedBeforeMatching(String contentType) {
    assertThat(matcher.isAccepted(contentType)).isTrue();
  }

  @Test
  void isAccepted_null_returnsFalse() {
    assertThat(matcher.isAccepted(null)).isFalse();
  }

  @Test
  void isAccepted_malformedHeader_returnsFalse() {
    assertThat(matcher.isAccepted("not a mime type")).isFalse();
  }

  @Test
  void isAccepted_denylistOverridesFamilyAllow_forSvgInsideImageFamily() {
    // image/* is on the allowlist, but image/svg+xml is on the denylist — the denylist must win.
    assertThat(matcher.isAccepted("image/svg+xml")).isFalse();
    assertThat(matcher.isAccepted("image/png")).isTrue();
  }

  @Test
  void isAccepted_operatorOverrides_areHonoured() {
    AssetProperties props = new AssetProperties();
    props.setHumanReadableContentTypes(Set.of("application/pdf"));
    props.setHumanReadableContentTypeDenylist(Set.of());

    HumanReadableContentTypeMatcher customMatcher = new HumanReadableContentTypeMatcher(props);

    assertThat(customMatcher.isAccepted("application/pdf")).isTrue();
    assertThat(customMatcher.isAccepted("text/markdown")).isFalse();
  }

  @Test
  void describeAllowlist_listsAllowAndDenyEntries_alphabetically() {
    String description = matcher.describeAllowlist();
    assertThat(description).contains("Accepted families:").contains("Always refused:");
    assertThat(description).contains("text/*").contains("image/*");
    assertThat(description).contains("application/javascript").contains("image/svg+xml");
  }
}
