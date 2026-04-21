package eu.xfsc.fc.core.service.verification;

import java.util.List;

import org.apache.jena.riot.system.stream.StreamManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import com.apicatalog.jsonld.loader.DocumentLoader;
import com.apicatalog.jsonld.loader.SchemeRouter;
import jakarta.annotation.PostConstruct;

import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.SchemaValidationResult;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.verification.cache.CachingLocator;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractor;
import eu.xfsc.fc.core.service.verification.claims.DanubeTechClaimExtractor;
import eu.xfsc.fc.core.service.verification.claims.CredentialSubjectClaimExtractor;
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
public class SchemaValidationServiceImpl implements SchemaValidationService {

    /**
     * Ordered array of claim extractors tried in sequence until one succeeds.
     * {@link CredentialSubjectClaimExtractor} is tried first (Titanium JSON-LD processor),
     * falling back to {@link DanubeTechClaimExtractor} (Danube Tech LD library).
     */
    private static final ClaimExtractor[] EXTRACTORS = new ClaimExtractor[]{
        new CredentialSubjectClaimExtractor(), new DanubeTechClaimExtractor()
    };

    @Autowired
    private SchemaStore schemaStore;

    @Autowired
    @Qualifier("contextCacheFileStore")
    private FileStore fileStore;

    @Autowired
    private DocumentLoader documentLoader;

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
            List<CredentialClaim> claims = extractClaims(payload);
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
    public SchemaValidationResult validateClaimsAgainstCompositeSchema(List<CredentialClaim> claims) {
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
    public SchemaValidationResult validateClaimsAgainstSchema(List<CredentialClaim> claims, ContentAccessor schema) {
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

    /**
     * Extracts RDF claims from a credential payload by trying each
     * {@link ClaimExtractor} in order. Returns the first successful extraction
     * result, or {@code null} if all extractors fail.
     *
     * @param payload the credential to extract claims from
     * @return extracted claims, or {@code null} if extraction fails
     */
    private List<CredentialClaim> extractClaims(ContentAccessor payload) {
        List<CredentialClaim> claims = null;
        for (ClaimExtractor extractor : EXTRACTORS) {
            try {
                claims = extractor.extractClaims(payload);
                if (claims != null && !claims.isEmpty()) {
                    break;
                }
            } catch (Exception ex) {
                log.error("extractClaims.error using {}: {}", extractor.getClass().getName(), ex.getMessage());
            }
        }
        return claims;
    }
}
