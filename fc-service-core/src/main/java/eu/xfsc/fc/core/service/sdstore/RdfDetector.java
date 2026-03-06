package eu.xfsc.fc.core.service.sdstore;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import eu.xfsc.fc.core.config.RdfContentTypeProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Determines whether uploaded content should be treated as RDF (and thus go through
 * verification + graph storage) or as a non-RDF asset (stored in FileStore only).
 *
 * <p>Detection is based on a configurable allowlist of RDF MIME types. The only content
 * inspection is for {@code application/json}: a lightweight {@code @context} check
 * distinguishes JSON-LD from plain JSON.</p>
 *
 * <p><b>Note:</b> Not all RDF content types in the allowlist can currently be processed
 * by {@code VerificationService} — only {@code application/ld+json} is fully supported.
 * Other RDF serializations (Turtle, N-Triples, etc.) will be detected as RDF but fail
 * verification. A strategy pattern for format-specific verification would allow each
 * RDF serialization to have its own processing pipeline. For now, operators can remove
 * unsupported types from the allowlist to accept them as non-RDF files instead.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RdfDetector {

    private static final String APPLICATION_JSON = "application/json";

    // Matches "@context" as a JSON key followed by colon and array/string/http — reduces false positives from "@context" as a value
    private static final Pattern JSON_LD_CONTEXT_PATTERN = Pattern.compile("\"@context\"\\s*:\\s*[\\[\"h]");

    private final RdfContentTypeProperties rdfProperties;

    /**
     * Check whether the given content type (and optionally the content bytes) indicates RDF.
     *
     * @param contentType the MIME content-type from the upload
     * @param content     the raw content bytes (used only for {@code application/json} to check for {@code @context})
     * @return {@code true} if the content should follow the RDF verification + graph storage path
     */
    public boolean isRdf(String contentType, byte[] content) {
        if (contentType == null) {
            return false;
        }

        String normalized = contentType.strip().toLowerCase();

        // Direct match against configured RDF content types
        if (rdfProperties.getContentTypes().contains(normalized)) {
            log.debug("isRdf; contentType '{}' is in configured RDF allowlist", contentType);
            return true;
        }

        // Special case: application/json needs @context check to distinguish JSON-LD from plain JSON
        if (APPLICATION_JSON.equals(normalized) && content != null) {
            String text = new String(content, StandardCharsets.UTF_8);
            boolean hasContext = JSON_LD_CONTEXT_PATTERN.matcher(text).find();
            log.debug("isRdf; application/json @context check: {}", hasContext);
            return hasContext;
        }

        return false;
    }

}