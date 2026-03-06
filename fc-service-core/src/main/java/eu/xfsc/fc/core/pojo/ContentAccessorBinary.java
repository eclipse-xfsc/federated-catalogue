package eu.xfsc.fc.core.pojo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A byte-array implementation of the ContentAccessor interface for binary content.
 * Unlike {@link ContentAccessorDirect}, this preserves raw bytes without assuming
 * UTF-8 text encoding. Prefer {@link #getContentAsStream()} or {@link #getContentAsBytes()}
 * for binary data. {@link #getContentAsString()} performs a lossy UTF-8 conversion
 * for compatibility with string-based storage (e.g. {@code CacheFileStore}).
 */
@lombok.AllArgsConstructor
public class ContentAccessorBinary implements ContentAccessor {

    private final byte[] content;

    @Override
    public String getContentAsString() {
        return new String(content, StandardCharsets.UTF_8);
    }

    @Override
    public InputStream getContentAsStream() {
        return new ByteArrayInputStream(content);
    }

    @Override
    public byte[] getContentAsBytes() {
        return content.clone();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof ContentAccessorBinary other) {
            return Arrays.equals(content, other.content);
        }
        if (obj instanceof ContentAccessor ca) {
            return Arrays.equals(content, ca.getContentAsBytes());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(content);
    }

}