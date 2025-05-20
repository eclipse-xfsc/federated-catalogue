package eu.xfsc.fc.server.util;

import java.io.File;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorFile;

public class TestUtil {

  public static ContentAccessor getAccessor(String path) {
    return getAccessor(TestUtil.class, path);
  }

  public static ContentAccessor getAccessor(Class<?> testClass, String fileName) {
    URL url = testClass.getClassLoader().getResource(fileName);
    String str = URLDecoder.decode(url.getFile(), StandardCharsets.UTF_8);
    return new ContentAccessorFile(new File(str));
  }

}
