package eu.xfsc.fc.core.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.temporal.ChronoUnit;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorFile;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;

public class TestUtil {

  public static ContentAccessor getAccessor(String path) {
    return getAccessor(TestUtil.class, path);
  }

  public static ContentAccessor getAccessor(Class<?> testClass, String fileName) {
    URL url = testClass.getClassLoader().getResource(fileName);
    String str = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
    return new ContentAccessorFile(new File(str));
  }

  // Since SdMetaRecord class extends SelfDescriptionMetadata class instead of being formed from it, then check
  // in the equals method will always be false. Because we are downcasting SdMetaRecord to SelfDescriptionMetadata.
  public static void assertThatSdHasTheSameData(final SelfDescriptionMetadata expected, final SelfDescriptionMetadata actual, final boolean precise) {
    assertEquals(expected.getId(), actual.getId());
    assertEquals(expected.getSdHash(), actual.getSdHash());
    assertEquals(expected.getStatus(), actual.getStatus());
    assertEquals(expected.getIssuer(), actual.getIssuer());
    assertEquals(expected.getValidatorDids(), actual.getValidatorDids());
    assertEquals(expected.getSelfDescription().getContentAsString(), actual.getSelfDescription().getContentAsString());
    if (precise) {
      assertEquals(expected.getUploadDatetime(), actual.getUploadDatetime());
      assertEquals(expected.getStatusDatetime(), actual.getStatusDatetime());
    } else {
      assertEquals(expected.getUploadDatetime().truncatedTo(ChronoUnit.MILLIS), actual.getUploadDatetime().truncatedTo(ChronoUnit.MILLIS));
      assertEquals(expected.getStatusDatetime().truncatedTo(ChronoUnit.MILLIS), actual.getStatusDatetime().truncatedTo(ChronoUnit.MILLIS));
    }
  }
  
}
