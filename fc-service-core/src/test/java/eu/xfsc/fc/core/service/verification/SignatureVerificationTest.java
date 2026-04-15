package eu.xfsc.fc.core.service.verification;

import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.io.IOException;

import eu.xfsc.fc.core.util.TestUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.jdbc.core.JdbcTemplate;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.schemas.SchemaAuditRepository;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.Validator;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

@SpringBootTest(properties = {
	"federated-catalogue.verification.signature-verifier=uni-res",
	"federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
	"federated-catalogue.verification.trust-framework.gaiax.trust-anchor-url=https://registry.lab.gaia-x.eu/v1/api/trustAnchor/chain/file"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {SignatureVerificationTest.TestApplication.class, FileStoreConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class,
        VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaJpaDao.class, SchemaAuditRepository.class, DatabaseConfig.class, DidResolverConfig.class, DidDocumentResolver.class, ValidatorCacheJpaDao.class,
        ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class})
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class SignatureVerificationTest {

	@SpringBootApplication
	public static class TestApplication {

	    public static void main(final String[] args) {
	        SpringApplication.run(TestApplication.class, args);
	    }
	}

	@Autowired
	private SchemaStoreImpl schemaStore;
	@Autowired
	private VerificationServiceImpl verificationService;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@MockitoBean
	private JwtSignatureVerifier jwtVerifierMock;

	@AfterEach
	public void storageSelfCleaning() throws IOException {
	    // Clear validator cache to prevent interference with other tests
	    jdbcTemplate.update("DELETE FROM validatorcache");
	    schemaStore.clear();
	}
	
	@Test
	void verifyCredential_jwkWithoutX5uAndGaiaxEnabled_throwsVerificationException() {
	    // Loire credential whose DID document JWK lacks x5c/x5u must be rejected
	    // when Gaia-X trust framework is ENABLED.
	    jdbcTemplate.update("UPDATE trust_frameworks SET enabled = true WHERE id = 'gaia-x'");
	    try {
	        when(jwtVerifierMock.verify(any()))
	            .thenReturn(new Validator("did:web:example.com#key-1",
	                "{\"kty\":\"EC\",\"crv\":\"P-256\"}", null));
	        ContentAccessor content = new ContentAccessorDirect(TestUtil.fakeLoireJwt("did:web:example.com"));

	        Exception ex = assertThrowsExactly(VerificationException.class, ()
	                -> verificationService.verifyCredential(content, false, false, false, true));
	        assertTrue(ex.getMessage().contains("must contain x5c or x5u"),
	            "Should require trust chain. Got: " + ex.getMessage());
	    } finally {
	        jdbcTemplate.update("UPDATE trust_frameworks SET enabled = false WHERE id = 'gaia-x'");
	    }
	}

}
