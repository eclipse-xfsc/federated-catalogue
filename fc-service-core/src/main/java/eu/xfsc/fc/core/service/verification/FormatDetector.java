package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.service.verification.VerificationConstants.JWT_PREFIX;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;

import eu.xfsc.fc.core.pojo.ContentAccessor;

import java.text.ParseException;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

/**
 * Routes incoming credential payloads to the correct processing path by delegating to an
 * ordered list of {@link FormatMatcher} beans.
 *
 * <p>The credential is pre-parsed once into a {@link DetectionContext} and then each matcher
 * is tried in order until one claims the format. If no matcher matches,
 * {@link CredentialFormat#UNKNOWN} is returned.
 *
 * <p>To add support for a new trust framework, implement {@link FormatMatcher}, annotate it
 * with {@code @Component} and {@code @Order}, and Spring will pick it up automatically.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FormatDetector {

    private final ObjectMapper objectMapper;
    private final List<FormatMatcher> matchers;

    /**
     * Detects the credential format of the given payload.
     *
     * @param content the incoming credential content
     * @return the detected format; never null
     */
    public CredentialFormat detect(ContentAccessor content) {
        String body = content.getContentAsString().strip();
        DetectionContext ctx = buildContext(body);
        CredentialFormat format = matchers.stream()
                .map(m -> m.match(ctx))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst()
                .orElse(CredentialFormat.UNKNOWN);
        log.debug("detect; resolved format: {}", format);
        return format;
    }

    private DetectionContext buildContext(String body) {
        if (!body.startsWith(JWT_PREFIX)) {
            return new DetectionContext(body, null, parseJson(body));
        }
        try {
            SignedJWT jwt = SignedJWT.parse(body);
            return new DetectionContext(body, jwt, parseJson(jwt.getPayload().toString()));
        } catch (ParseException ex) {
            log.debug("buildContext; JWT parse failed: {} → treating as non-JWT", ex.getMessage());
            return new DetectionContext(body, null, null);
        }
    }

    private @Nullable JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            log.debug("buildContext; JSON parse failed → no parsed context available");
            return null;
        }
    }
}
