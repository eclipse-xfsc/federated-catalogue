package eu.xfsc.fc.core.service.verification;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import jakarta.annotation.PostConstruct;

import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;
import eu.xfsc.fc.core.util.ClaimValidator;
import lombok.extern.slf4j.Slf4j;

/**
 * Default implementation of {@link SchemaValidationService}.
 *
 * <p>Performs SHACL validation by extracting RDF claims from a credential payload
 * and validating them against SHACL shape graphs using {@link ClaimValidator}.</p>
 *
 * @see SchemaValidationService
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SchemaValidationServiceImpl implements SchemaValidationService {

  private final ClaimExtractionService claimExtractionService;
  private final SchemaStore schemaStore;
    @Qualifier("contextCacheFileStore")
    private final FileStore fileStore;
  private final DocumentLoader documentLoader;

    private StreamManager streamManager;

    /** {@inheritDoc} Delegates to {@link #validateCredentialAgainstSchema} with a {@code null} schema. */
    @Override
    public SchemaValidationResult validateCredentialAgainstCompositeSchema(ContentAccessor payload) {
        return validateCredentialAgainstSchema(payload, null);
    }

    /** {@inheritDoc} */
    @Override
    public SchemaValidationResult validateCredentialAgainstSchema(ContentAccessor payload, ContentAccessor schema) {
        log.debug("validateCredentialAgainstSchema.enter;");
        SchemaValidationResult result = null;
        try {
            if (schema == null) {
                schema = schemaStore.getCompositeSchema(SchemaStore.SchemaType.SHAPE);
            }
            List<RdfClaim> claims = extractClaims(payload);
            result = validateClaimsAgainstSchema(claims, schema);
        } catch (Exception exc) {
            log.info("validateCredentialAgainstSchema.error: {}", exc.getMessage());
        }
        boolean conforms = result != null && result.isConforming();
        log.debug("validateCredentialAgainstSchema.exit; conforms: {}", conforms);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public SchemaValidationResult validateClaimsAgainstCompositeSchema(List<RdfClaim> claims) {
        log.debug("validateClaimsAgainstCompositeSchema.enter;");
        SchemaValidationResult result = null;
        try {
            ContentAccessor shaclShape = schemaStore.getCompositeSchema(SchemaStore.SchemaType.SHAPE);
            result = validateClaimsAgainstSchema(claims, shaclShape);
        } catch (Exception exc) {
            log.info("validateClaimsAgainstCompositeSchema.error: {}", exc.getMessage());
        }
        boolean conforms = result != null && result.isConforming();
        log.debug("validateClaimsAgainstCompositeSchema.exit; conforms: {}", conforms);
        return result;
    }

    /** {@inheritDoc} */
    @Override
    public SchemaValidationResult validateClaimsAgainstSchema(List<RdfClaim> claims, ContentAccessor schema) {
        String report = ClaimValidator.validateClaimsBySchema(claims, schema, streamManager);
        return new SchemaValidationResult(report == null, report);
    }

    /**
     * Initialises the JSON-LD {@link SchemeRouter} and Jena {@link StreamManager}
     * once after dependency injection, ensuring thread-safe setup.
     */
    @PostConstruct
    private void init() {
        log.debug("init; Setting up SchemeRouter");
        SchemeRouter loader = (SchemeRouter) SchemeRouter.defaultInstance();
        loader.set("file", documentLoader);
        loader.set("http", documentLoader);
        loader.set("https", documentLoader);

        log.debug("init; Setting up Jena caching Locator");
        StreamManager clone = StreamManager.get().clone();
        clone.clearLocators();
        clone.addLocator(new CachingLocator(fileStore));
        streamManager = clone;
    }

    private List<RdfClaim> extractClaims(ContentAccessor payload) {
        return claimExtractionService.extractCredentialClaims(payload);
    }
}
