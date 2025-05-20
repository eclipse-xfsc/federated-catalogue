package eu.xfsc.fc.core.pojo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.apache.commons.io.IOUtils;

/**
 * A direct string implementation of the ContentAccessor interface.
 */
@lombok.AllArgsConstructor
public class ContentAccessorDirect implements ContentAccessor {

    private final String content;

    @Override
    public String getContentAsString() {
        return content;
    }

    @Override
    public InputStream getContentAsStream() {
        return IOUtils.toInputStream(content, StandardCharsets.UTF_8);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ContentAccessorDirect) {
            ContentAccessorDirect cad = (ContentAccessorDirect) obj;
            return content.equals(cad.content);
        }
        if (obj instanceof ContentAccessor) {
            ContentAccessor ca = (ContentAccessor) obj;
            // TODO: This comparison is expensive, test if it is used in normal operation and optimise if it is.
            return content.equals(ca.getContentAsString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(this.content);
    }

}
