package eu.xfsc.fc.core.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.security.SecurityAuditorAware;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.validation.rdf.RdfAssetParser;
import eu.xfsc.fc.core.service.validation.strategy.ShaclValidationExecutor;
import eu.xfsc.fc.core.service.verification.CredentialFormatDetector;
import eu.xfsc.fc.core.service.verification.CredentialVerificationStrategy;
import eu.xfsc.fc.core.service.verification.DanubeTechFormatMatcher;
import eu.xfsc.fc.core.service.verification.JwtContentPreprocessor;
import eu.xfsc.fc.core.service.verification.LoireJwtParser;
import eu.xfsc.fc.core.service.verification.LoireMatcher;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.SchemaModuleConfigService;
import eu.xfsc.fc.core.service.verification.SchemaValidationServiceImpl;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkService;
import eu.xfsc.fc.core.service.verification.Vc2Processor;
import eu.xfsc.fc.core.service.verification.VerificationServiceImpl;
import eu.xfsc.fc.core.service.verification.claims.ClaimExtractionService;
import eu.xfsc.fc.core.service.verification.claims.JenaAllTriplesExtractor;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Shared test configuration for the verification stack.
 *
 * <p>Import this class in any test that needs {@link VerificationServiceImpl} or
 * {@link CredentialVerificationStrategy}. Tests that mock {@link JwtSignatureVerifier}
 * declare {@code @MockitoBean} in the test class — it overrides the real bean registered here.
 *
 * <p>When the verification stack gains a new dependency, add the configuration class here;
 * all test classes automatically pick it up.
 */
@TestConfiguration
@Import({
    ClaimExtractionService.class,
    CredentialFormatDetector.class,
    CredentialVerificationStrategy.class,
    DanubeTechFormatMatcher.class,
    DatabaseConfig.class,
    DidDocumentResolver.class,
    DidResolverConfig.class,
    DocumentLoaderConfig.class,
    DocumentLoaderProperties.class,
    FileStoreConfig.class,
    HttpDocumentResolver.class,
    JenaAllTriplesExtractor.class,
    JwtContentPreprocessor.class,
    JwtSignatureVerifier.class,
    LoireJwtParser.class,
    LoireMatcher.class,
    ObjectMapper.class,
    ProtectedNamespaceFilter.class,
    ProtectedNamespaceProperties.class,
    RdfAssetParser.class,
    SchemaAuditRepository.class,
    SchemaJpaDao.class,
    SchemaModuleConfigService.class,
    SchemaStoreImpl.class,
    SchemaValidationServiceImpl.class,
    SecurityAuditorAware.class,
    ShaclValidationExecutor.class,
    TrustFrameworkRegistryConfig.class,
    TrustFrameworkService.class,
    ValidatorCacheJpaDao.class,
    Vc2Processor.class,
    VerificationServiceImpl.class
})
public class VerificationStackTestConfig {
}
