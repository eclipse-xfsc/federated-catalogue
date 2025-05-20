package eu.xfsc.fc.core.pojo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.FileUtils;
import java.util.Objects;

/**
 * A file-reading implementation of the ContentAccessor interface.
 */
@lombok.AllArgsConstructor
public class ContentAccessorFile implements ContentAccessor {

  private final File file;

  @Override
  public String getContentAsString() {
    try {
      return FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public InputStream getContentAsStream() {
    try {
      return FileUtils.openInputStream(file);
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ContentAccessorFile) {
      ContentAccessorFile caf = (ContentAccessorFile) obj;
      return file.equals(caf.file);
    }
    if (obj instanceof ContentAccessor) {
      ContentAccessor ca = (ContentAccessor) obj;
      return getContentAsString().equals(ca.getContentAsString());
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(this.file);
  }

}
