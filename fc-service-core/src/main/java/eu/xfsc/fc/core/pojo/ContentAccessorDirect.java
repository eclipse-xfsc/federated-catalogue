package eu.xfsc.fc.core.pojo;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;

/**
 * A direct string implementation of the ContentAccessor interface.
 */
@RequiredArgsConstructor
public class ContentAccessorDirect implements ContentAccessor {

    private final String content;
    private final String contentType;

    /** Creates a content accessor with no associated content-type (content-type is {@code null}). */
    public ContentAccessorDirect(String content) {
        this.content = content;
        this.contentType = null;
    }

    @Override
    public String getContentType() {
        return contentType;
    }

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
        if (obj instanceof ContentAccessorDirect cad) {
            return content.equals(cad.content);
        }
        if (obj instanceof ContentAccessor ca) {
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
