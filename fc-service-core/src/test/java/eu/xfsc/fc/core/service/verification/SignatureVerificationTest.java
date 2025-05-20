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

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.dao.impl.SchemaDaoImpl;
import eu.xfsc.fc.core.dao.impl.ValidatorCacheDaoImpl;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;

@SpringBootTest(properties = { "federated-catalogue.verification.signature-verifier=uni-res", "federated-catalogue.verification.did.base-url=https://dev.uniresolver.io/1.0",
		"federated-catalogue.verification.drop-validators=true" })
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ActiveProfiles("test")
@ContextConfiguration(classes = {SignatureVerificationTest.TestApplication.class, FileStoreConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class,
        VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaDaoImpl.class, DatabaseConfig.class, DidResolverConfig.class, DidDocumentResolver.class, ValidatorCacheDaoImpl.class})
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

	@AfterEach
	public void storageSelfCleaning() throws IOException {
	    schemaStore.clear();
	}
	
	//@Test
	void testComplienceV1Signature() {
	    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
	    String path = "VerificationService/sign-unires/participant_v1_signed.jsonld";
	    VerificationResult result = verificationService.verifySelfDescription(getAccessor(path));
	    assertEquals(1, result.getValidators().size(), "Incorrect number of validators found");
	}

	@Test
	void testJWKCertificate() {
	    schemaStore.addSchema(getAccessor("Schema-Tests/gax-test-ontology.ttl"));
	    String path = "VerificationService/sign-unires/participant_jwk_signed.jsonld";
	    Exception ex = assertThrowsExactly(VerificationException.class, ()
	            -> verificationService.verifySelfDescription(getAccessor(path), true, true, true, true));
	    assertEquals("Signatures error; no trust anchor url found", ex.getMessage());
	}
	
}
