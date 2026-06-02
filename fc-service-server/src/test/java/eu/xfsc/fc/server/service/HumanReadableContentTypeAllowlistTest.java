package eu.xfsc.fc.server.service;

import eu.xfsc.fc.core.exception.ClientException;
import eu.xfsc.fc.server.config.AssetProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the human-readable companion content-type filter. The accepted set is computed from a
 * family-aware allowlist intersected with an exact-match denylist. Browser-executable scripting
 * types, inline-scriptable SVG, and opaque/native-binary types are refused even when the broader
 * family is admitted.
 */
class HumanReadableContentTypeAllowlistTest {

  // Allowlist accepts entire safe document, structured-data, image, audio, and video families.

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
  void acceptedTypes_default_pass(String contentType) {
    assertDoesNotThrow(() -> invokeValidator(newAssetService(), contentType));
  }

  // Denylist overrides any family match — script-execution and opaque-binary risks.

  @ParameterizedTest
  @ValueSource(strings = {
      // scripting MIME types
      "text/javascript",
      "text/ecmascript",
      "text/vbscript",
      "application/javascript",
      "application/ecmascript",
      "application/x-javascript",
      // inline-scriptable SVG
      "image/svg+xml",
      // opaque / native-binary
      "application/octet-stream",
      "application/x-msdownload",
      "application/x-executable",
      "application/x-sh",
      "application/x-shockwave-flash",
      "application/x-httpd-php"
  })
  void refusedTypes_denylist_overrideFamilyMatch(String contentType) {
    assertThrows(ClientException.class, () -> invokeValidator(newAssetService(), contentType));
  }

  // Types outside every family — refused by absence rather than denylist.

  @ParameterizedTest
  @ValueSource(strings = {
      "model/gltf+json",
      "multipart/form-data",
      "message/rfc822",
      "chemical/x-pdb"
  })
  void unmatchedTypes_outsideAllowedFamilies_refused(String contentType) {
    assertThrows(ClientException.class, () -> invokeValidator(newAssetService(), contentType));
  }

  // Parameter-bearing MIME types match by their base type — charset and similar are stripped.

  @ParameterizedTest
  @ValueSource(strings = {
      "text/markdown; charset=utf-8",
      "application/json; charset=UTF-8",
      "text/plain;charset=ISO-8859-1"
  })
  void parameterisedType_strippedBeforeMatching_passes(String contentType) {
    assertDoesNotThrow(() -> invokeValidator(newAssetService(), contentType));
  }

  // Malformed or absent values are refused with the same client error as a real mismatch.

  @Test
  void nullContentType_refused() {
    assertThrows(ClientException.class, () -> invokeValidator(newAssetService(), null));
  }

  @Test
  void malformedContentType_refused() {
    assertThrows(ClientException.class, () -> invokeValidator(newAssetService(), "not a mime type"));
  }

  private AssetService newAssetService() {
    AssetService service = (AssetService) org.springframework.beans.BeanUtils.instantiateClass(AssetService.class);
    ReflectionTestUtils.setField(service, "assetProperties", new AssetProperties());
    return service;
  }

  private void invokeValidator(AssetService service, String contentType) throws Throwable {
    try {
      Method method = AssetService.class.getDeclaredMethod("validateHumanReadableContentType", String.class);
      method.setAccessible(true);
      method.invoke(service, contentType);
    } catch (InvocationTargetException ex) {
      throw ex.getCause();
    }
  }
}
