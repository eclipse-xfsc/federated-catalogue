package eu.xfsc.fc.core.service.verification;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.Validator;
import org.springframework.lang.Nullable;

/**
 * Result of {@link CredentialFormatProcessor#process}. Carries the unwrapped JSON-LD
 * payload, whether the input was a compact JWT, and (when signatures were verified)
 * the JWT validator the strategy will surface in the credential verification result.
 */
public record ProcessedEnvelope(
    ContentAccessor unwrappedPayload,
    boolean wasJwt,
    @Nullable Validator jwtValidator
) {
}
