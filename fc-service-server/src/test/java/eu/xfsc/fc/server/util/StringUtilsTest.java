package eu.xfsc.fc.server.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link StringUtils}.
 */
class StringUtilsTest {

  @Nested
  class SanitizeFilenameTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
        // name,                             input,                      expected
        "normalName_unchanged,               document.pdf,               document.pdf",
        "pathTraversalUnix_stripped,         ../secret/file.pdf,         ..secretfile.pdf",
        "pathTraversalWindows_stripped,      ..\\\\secret\\\\file,       ..secretfile",
        "mixedSeparators_stripped,           /foo\\\\bar/baz,            foobarbaz",
        "empty_returnsEmpty,                 '',                         ''",
    })
    void sanitizeFilename_parameterized(String name, String input, String expected) {
      // treat the literal '' in @CsvSource as an empty string
      String actualInput = "''".equals(input) ? "" : input;
      String actualExpected = "''".equals(expected) ? "" : expected;
      assertEquals(actualExpected, StringUtils.sanitizeFilename(actualInput), name);
    }

    @Test
    void sanitizeFilename_null_returnsNull() {
      assertNull(StringUtils.sanitizeFilename(null));
    }

    @Test
    void sanitizeFilename_nullByte_stripped() {
      // \u0000 falls in \x00-\x1F — must be removed
      assertEquals("file.pdf", StringUtils.sanitizeFilename("file\u0000.pdf"));
    }

    @Test
    void sanitizeFilename_controlChar_stripped() {
      // \u001F (US, unit separator) falls in \x00-\x1F — must be removed
      assertEquals("filename.pdf", StringUtils.sanitizeFilename("file\u001Fname.pdf"));
    }

    @Test
    void sanitizeFilename_del_stripped() {
      // \u007F (DEL) matches \x7F — must be removed
      assertEquals("filename.pdf", StringUtils.sanitizeFilename("file\u007Fname.pdf"));
    }

    @Test
    void sanitizeFilename_lengthTruncation_truncated() {
      String longName = "a".repeat(300);
      String result = StringUtils.sanitizeFilename(longName);
      assertTrue(result.length() <= 255, "Expected length <= 255 but got " + result.length());
      assertEquals(255, result.length());
    }

    @Test
    void sanitizeFilename_exactly255chars_notTruncated() {
      String name = "a".repeat(255);
      assertEquals(name, StringUtils.sanitizeFilename(name));
    }

    @Test
    void sanitizeFilename_noPathSeparatorsRemain_afterUnixTraversal() {
      String result = StringUtils.sanitizeFilename("../secret/file.pdf");
      assertTrue(result != null && !result.contains("/"),
          "Result must not contain '/' but was: " + result);
    }

    @Test
    void sanitizeFilename_noPathSeparatorsRemain_afterWindowsTraversal() {
      String result = StringUtils.sanitizeFilename("..\\secret\\file");
      assertTrue(result != null && !result.contains("\\"),
          "Result must not contain '\\' but was: " + result);
    }
  }
}

