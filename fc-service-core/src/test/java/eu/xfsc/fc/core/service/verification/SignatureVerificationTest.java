package eu.xfsc.fc.core.service.verification;

import static eu.xfsc.fc.core.util.TestUtil.getAccessor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import java.io.IOException;

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
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

@SpringBootTest(properties = {
	"federated-catalogue.verification.signature-verifier=uni-res",
	"federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
	"federated-catalogue.verification.drop-validators=true",
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

	@AfterEach
	public void storageSelfCleaning() throws IOException {
	    // Clear validator cache to prevent interference with other tests
	    jdbcTemplate.update("DELETE FROM validatorcache");
	    schemaStore.clear();
	}
	
	//@Test
	void verifyCredential_v1SignedParticipant_validatorFound() {
	    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
	    String path = "VerificationService/sign-unires/participant_v1_signed.jsonld";
	    CredentialVerificationResult result = verificationService.verifyCredential(getAccessor(path));
	    assertEquals(1, result.getValidators().size(), "Incorrect number of validators found");
	}

	@Test
	void verifyCredential_jwkWithoutX5uAndGaiaxEnabled_throwsVerificationException() {
	    // This test verifies that when Gaia-X trust framework is ENABLED,
	    // credentials without x5u (trust anchor URL) in the JWK are rejected.
	    // Gaia-X enabled state is read from the trust_frameworks DB table.
	    jdbcTemplate.update("UPDATE trust_frameworks SET enabled = true WHERE id = 'gaia-x'");
	    try {
	        schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
	        String path = "VerificationService/sign-unires/participant_jwk_signed.jsonld";
	        Exception ex = assertThrowsExactly(VerificationException.class, ()
	                -> verificationService.verifyCredential(getAccessor(path), true, true, true, true));
	        assertEquals("Signatures error; no trust anchor url found", ex.getMessage());
	    } finally {
	        jdbcTemplate.update("UPDATE trust_frameworks SET enabled = false WHERE id = 'gaia-x'");
	    }
	}

}
