package eu.xfsc.fc.graphdb.service;

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import eu.xfsc.fc.core.dao.schemas.SchemaJpaDao;
import eu.xfsc.fc.core.dao.assets.AssetJpaDao;
import eu.xfsc.fc.core.dao.validatorcache.ValidatorCacheJpaDao;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.CredentialVerificationResultOffering;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.service.assetstore.AssetStoreImpl;
import eu.xfsc.fc.core.service.assetstore.IriGenerator;
import eu.xfsc.fc.core.service.assetstore.IriValidator;
import eu.xfsc.fc.core.service.resolve.DidDocumentResolver;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.verification.CredentialVerificationStrategy;
import eu.xfsc.fc.core.service.verification.FormatDetector;
import eu.xfsc.fc.core.service.verification.JwtContentPreprocessor;
import eu.xfsc.fc.core.service.verification.LoireJwtParser;
import eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter;
import eu.xfsc.fc.core.service.verification.SchemaValidationServiceImpl;
import eu.xfsc.fc.core.service.verification.Vc11Processor;
import eu.xfsc.fc.core.service.verification.Vc2Processor;
import eu.xfsc.fc.core.service.verification.VerificationServiceImpl;
import eu.xfsc.fc.core.service.verification.signature.JwtSignatureVerifier;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import eu.xfsc.fc.graphdb.config.GraphDbConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.neo4j.harness.Neo4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {Neo4jGraphStoreAccuracyTest.TestApplication.class, DatabaseConfig.class, GraphDbConfig.class, FileStoreConfig.class, Neo4jGraphStoreAccuracyTest.class,
	Neo4jGraphStore.class, AssetStoreImpl.class, AssetJpaDao.class, IriGenerator.class, IriValidator.class,
	VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaJpaDao.class, ValidatorCacheJpaDao.class,
	DidResolverConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class, HttpDocumentResolver.class,
	CredentialVerificationStrategy.class, SchemaValidationServiceImpl.class, ProtectedNamespaceFilter.class, ProtectedNamespaceProperties.class,
	JwtContentPreprocessor.class, Vc11Processor.class, Vc2Processor.class,
	JwtSignatureVerifier.class, DidDocumentResolver.class,
	FormatDetector.class, LoireJwtParser.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class Neo4jGraphStoreAccuracyTest {
	
  //TODO:: We need to update credential file when final implementation of claim parsing is done .
  private final String SERVICE_CREDENTIAL_FILE_NAME = "serviceOfferingCredential.jsonld";
  private final String SERVICE_CREDENTIAL_FILE_NAME1 = "serviceOfferingCredential1.jsonld";
  private final String SERVICE_CREDENTIAL_FILE_NAME2 = "serviceOfferingCredential2.jsonld";
  private final String SERVICE_CREDENTIAL_FILE_NAME3 = "serviceOfferingCredential3.jsonld";

  @SpringBootApplication
  public static class TestApplication {

    public static void main(String[] args) {
      SpringApplication.run(TestApplication.class, args);
    }
  }
  
  @Autowired
  private Neo4j embeddedDatabaseServer;
  @Autowired
  private Neo4jGraphStore neo4jGraphStore;
  @Autowired
  private AssetStoreImpl assetStore;
  @Autowired
  private VerificationServiceImpl verificationService;

  
  @BeforeAll
  void addDBEntries() throws IOException {
    initialiseAllDataBaseWithManuallyAddingCredential();
  }

  @AfterAll
  void closeNeo4j() {
    embeddedDatabaseServer.close();
  }

  @Test
  public void testCypherServiceOfferingAccuracy() {


    List<Map<String, Object>> resultListExpected = List.of(Map.of("n", Map.of("name", "Portal", "claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal.json"))));

    GraphQuery queryDelta = new GraphQuery(
            "MATCH (n:ServiceOffering) WHERE n.name = $name RETURN n LIMIT $limit",
            Map.of("name", "Portal", "limit", 25));
    List<Map<String, Object>> responseList = neo4jGraphStore.queryData(queryDelta).getResults();
    Assertions.assertEquals(resultListExpected, responseList);
  }

  @Test
  public void testCypherServiceOfferingByURIAccuracy() {
    /*expected only one node as previous added claims with same ID deleted from code*/
    List<Map<String, String>> resultListExpected = List.of(
            Map.of("n.name", "Portal3"));

    GraphQuery queryDelta = new GraphQuery(
            "MATCH (n:ServiceOffering) WHERE n.uri = $uri RETURN n.name LIMIT $limit",
            Map.of("uri", "http://w3id.org/gaia-x/indiv#serviceMVGPortal3.json", "limit", 25));
    List<Map<String, Object>> responseList = neo4jGraphStore.queryData(queryDelta).getResults();
    Assertions.assertEquals(resultListExpected, responseList);
  }

  @Test
  public void testCypherAllServiceOfferingAccuracy() {

    List<Map<String, Object>> resultListExpected = List.of(Map.of("n", Map.of("name", "Portal", "claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal.json"))),Map.of("n", Map.of("name", "Portal2", "claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal2.json"))),Map.of("n", Map.of("name", "Portal3", "claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal3.json"))),Map.of("n",Map.of("name","Portal2","claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal4.json"))));

    GraphQuery queryDelta = new GraphQuery(
            "MATCH (n:ServiceOffering)  RETURN n LIMIT $limit", Map.of("limit", 25));
    List<Map<String, Object>> responseList = neo4jGraphStore.queryData(queryDelta).getResults();
    Assertions.assertEquals(resultListExpected.size(), responseList.size());
  }


  @Test
  void testCypherAllServiceOfferingWithNameAndURI_IN_ClauseAccuracy() {

    List<Map<String, String>> resultListExpected = List.of(
            Map.of("name", "Portal2", "uri", "http://w3id.org/gaia-x/indiv#serviceMVGPortal2.json"),
            Map.of("name", "Portal2", "uri", "http://w3id.org/gaia-x/indiv#serviceMVGPortal4.json"));

    GraphQuery queryDelta = new GraphQuery(
            "CALL {MATCH (n:ServiceOffering) WHERE n.name = $name RETURN n.uri as urlList} MATCH " +
                    "(n:ServiceOffering) WHERE n.uri IN [urlList] RETURN n.uri as uri, n.name as name LIMIT $limit",
            Map.of("name", "Portal2", "limit", 25));
    List<Map<String, Object>> responseList = neo4jGraphStore.queryData(queryDelta).getResults();
    Assertions.assertEquals(resultListExpected.size(), responseList.size());
  }

  @Test
  void testCypherTotalCount() {

    GraphQuery queryDelta = new GraphQuery(
            "MATCH (n)  RETURN n LIMIT $limit", Map.of("limit", 25));
    List<Map<String, Object>> responseList = neo4jGraphStore.queryData(queryDelta).getResults();
    Assertions.assertEquals(5, responseList.size());
  }

  @Test
  void testQueryForGroupBYAndLocation() {
    String credentialSubject1 = "http://example.org/test-issuer";
    List<CredentialClaim> claimList = Arrays.asList(
            new CredentialClaim(
                    "<http://example.org/test-issuer>",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Provider>"
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer>",
                    "<http://w3id.org/gaia-x/participant#legalAddress>",
                    "_:23"
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer>",
                    "<http://w3id.org/gaia-x/participant#legalName>",
                    "\"deltaDAO AG\""
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer>",
                    "<http://w3id.org/gaia-x/participant#name>",
                    "\"deltaDAO AG\""
            ),
            new CredentialClaim("_:23",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Address>"
            ),
            new CredentialClaim("_:23",
                    "<http://w3id.org/gaia-x/participant#country>",
                    "\"DE\""
            ),
            new CredentialClaim("_:23",
                    "<http://w3id.org/gaia-x/participant#locality>",
                    "\"Hamburg\""
            ),
            new CredentialClaim("_:23",
                    "<http://w3id.org/gaia-x/participant#postal-code>",
                    "\"22303\""
            ),
            new CredentialClaim("_:23",
                    "<http://w3id.org/gaia-x/participant#street-address>",
                    "\"GeibelstraГџe 46b\""
            )
    );

    String credentialSubject2 = "http://example.org/test-issuer2";
    List<CredentialClaim> claimList2 = Arrays.asList(
            new CredentialClaim(
                    "<http://example.org/test-issuer2>",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Provider>"
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer2>",
                    "<http://w3id.org/gaia-x/participant#legalAddress>",
                    "_:b1"
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer2>",
                    "<http://w3id.org/gaia-x/participant#legalName>",
                    "\"deltaDAO AGE\""
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer2>",
                    "<http://w3id.org/gaia-x/participant#name>",
                    "\"deltaDAO AGE\""
            ),
            new CredentialClaim("_:b1",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Address>"
            ),
            new CredentialClaim("_:b1",
                    "<http://w3id.org/gaia-x/participant#country>",
                    "\"DE\""
            ),
            new CredentialClaim("_:b1",
                    "<http://w3id.org/gaia-x/participant#locality>",
                    "\"Dresden\""
            ),
            new CredentialClaim("_:b1",
                    "<http://w3id.org/gaia-x/participant#postal-code>",
                    "\"01067\""
            ),
            new CredentialClaim("_:b1",
                    "<http://w3id.org/gaia-x/participant#street-address>",
                    "\"Tried str 46b\""
            )
    );

    String credentialSubject3 = "http://example.org/test-issuer3";
    List<CredentialClaim> claimList3 = Arrays.asList(
            new CredentialClaim(
                    "<http://example.org/test-issuer3>",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Provider>"
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer3>",
                    "<http://w3id.org/gaia-x/participant#legalAddress>",
                    "_:b2"
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer3>",
                    "<http://w3id.org/gaia-x/participant#legalName>",
                    "\"deltaDAO AGEF\""
            ),
            new CredentialClaim(
                    "<http://example.org/test-issuer3>",
                    "<http://w3id.org/gaia-x/participant#name>",
                    "\"deltaDAO AGEF\""
            ),
            new CredentialClaim("_:b2",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Address>"
            ),
            new CredentialClaim("_:b2",
                    "<http://w3id.org/gaia-x/participant#country>",
                    "\"DE\""
            ),
            new CredentialClaim("_:b2",
                    "<http://w3id.org/gaia-x/participant#locality>",
                    "\"Dresden\""
            ),
            new CredentialClaim("_:b2",
                    "<http://w3id.org/gaia-x/participant#postal-code>",
                    "\"01069\""
            ),
            new CredentialClaim("_:b2",
                    "<http://w3id.org/gaia-x/participant#street-address>",
                    "\"Fried str 46b\""
            )
    );

    neo4jGraphStore.addClaims(claimList, credentialSubject1);
    neo4jGraphStore.addClaims(claimList2, credentialSubject2);
    neo4jGraphStore.addClaims(claimList3, credentialSubject3);

    //Query for the group by locality
    GraphQuery queryCypher = new GraphQuery("MATCH (n) where n.locality IS NOT NULL return COUNT(n.locality)  as countLocation ,n" +
            ".locality as locality ORDER BY countLocation ASC ", null);

    List<Map<String, Object>> responseCypher = neo4jGraphStore.queryData(queryCypher).getResults();
    List<Map<String, Object>> resultListCountLocation = List.of(Map.of("countLocation", 1L, "locality", "Hamburg"), Map.of("countLocation", 2L, "locality", "Dresden"));
    Assertions.assertEquals(resultListCountLocation, responseCypher);

    //Query for the getting by locality
    GraphQuery queryCypherByLocality = new GraphQuery("MATCH (n) where n.locality = $locality return n", //, n.claimsGraphUri" +
            Map.of("locality", "Dresden"));


    List<Map<String, Object>> responseCypherByLocality = neo4jGraphStore.queryData(queryCypherByLocality).getResults();
    Assertions.assertEquals(2,responseCypherByLocality.size());

    //cleanup
    neo4jGraphStore.deleteClaims(credentialSubject1);
    neo4jGraphStore.deleteClaims(credentialSubject2);
    neo4jGraphStore.deleteClaims(credentialSubject3);
  }

  private void initialiseAllDataBaseWithManuallyAddingCredential() throws IOException {

    ContentAccessorDirect contentAccessor = new ContentAccessorDirect(getMockFileDataAsString(SERVICE_CREDENTIAL_FILE_NAME));
    CredentialVerificationResultOffering verificationResult =
            (CredentialVerificationResultOffering) verificationService.verifyCredential(contentAccessor, true, false, false, false);

    //TODO:: adding manually claims, after final implementation we will remove it and change the query according to credential file content

    CredentialClaim claim = new CredentialClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal.json>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://w3id.org/gaia-x/service#ServiceOffering>");

    CredentialClaim claimName = new CredentialClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal.json>",
            "<http://w3id.org/gaia-x/service#name>",
            "\"Portal\"");

    List<CredentialClaim> claimFile = List.of(claim, claimName);

    verificationResult.setClaims(claimFile);
    verificationResult.setId(claimFile.getFirst().getSubjectValue());

    AssetMetadata assetMetadata = new AssetMetadata(verificationResult.getId(),
            verificationResult.getIssuer(), new ArrayList<>(), contentAccessor);
    assetStore.storeCredential(assetMetadata, verificationResult);

    //adding 2 credential skipping credential validation as we don't have full implementation
    ContentAccessorDirect contentAccessorDirect2 =
            new ContentAccessorDirect(getMockFileDataAsString(SERVICE_CREDENTIAL_FILE_NAME1));
    CredentialVerificationResultOffering verificationResult2 =
            (CredentialVerificationResultOffering) verificationService.verifyCredential(contentAccessor, true, false, false, false);

    CredentialClaim claim1 = new CredentialClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal2.json>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://w3id.org/gaia-x/service#ServiceOffering>");

    CredentialClaim claimName1 = new CredentialClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal2.json>",
            "<http://w3id.org/gaia-x/service#name>",
            "\"Portal2\"");

    List<CredentialClaim> claimFile1 = List.of(claim1, claimName1);

    verificationResult2.setClaims(claimFile1);
    verificationResult2.setId(claimFile1.getFirst().getSubjectValue());

    AssetMetadata assetMetadata2 = new AssetMetadata(
            verificationResult2.getId(),
            verificationResult2.getIssuer(), new ArrayList<>(), contentAccessorDirect2);
    assetStore.storeCredential(assetMetadata2, verificationResult2);

    //adding credential 3
    ContentAccessorDirect contentAccessorDirect3 =
            new ContentAccessorDirect(getMockFileDataAsString(SERVICE_CREDENTIAL_FILE_NAME2));
    CredentialVerificationResultOffering verificationResult3 =
            (CredentialVerificationResultOffering) verificationService.verifyCredential(contentAccessorDirect3, true, false, false, false);

    CredentialClaim claim3 = new CredentialClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal3.json>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://w3id.org/gaia-x/service#ServiceOffering>");

    CredentialClaim claimName3 = new CredentialClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal3.json>",
            "<http://w3id.org/gaia-x/service#name>",
            " \"Portal3\"");

    List<CredentialClaim> claimFile3 = List.of(claim3, claimName3);

    verificationResult3.setClaims(claimFile3);
    verificationResult3.setId(claimFile3.getFirst().getSubjectValue());

    AssetMetadata assetMetadata3 = new AssetMetadata(
            verificationResult3.getId(),
            verificationResult3.getIssuer(), new ArrayList<>(), contentAccessorDirect3);
    assetStore.storeCredential(assetMetadata3, verificationResult3);


    //adding credential 4
    ContentAccessorDirect contentAccessorDirect4 =
            new ContentAccessorDirect(getMockFileDataAsString(SERVICE_CREDENTIAL_FILE_NAME3));
    CredentialVerificationResultOffering verificationResult4 =
            (CredentialVerificationResultOffering) verificationService.verifyCredential(contentAccessor, true, false, false, false);


    CredentialClaim claim4 = new CredentialClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal4.json>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://w3id.org/gaia-x/service#ServiceOffering>");

    CredentialClaim claimName4 = new CredentialClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal4.json>",
            "<http://w3id.org/gaia-x/service#name>",
            "\"Portal2\"");


    List<CredentialClaim> claimFile4 = List.of(claim4, claimName4);

    verificationResult4.setClaims(claimFile4);
    verificationResult4.setId(claimFile4.getFirst().getSubjectValue());

    AssetMetadata assetMetadata4 = new AssetMetadata(
            verificationResult4.getId(),
            verificationResult4.getIssuer(), new ArrayList<>(), contentAccessorDirect4);
    assetStore.storeCredential(assetMetadata4, verificationResult4);
  }

  public static String getMockFileDataAsString(String filename) throws IOException {
    Path resourceDirectory = Paths.get("src", "test", "resources", "Query-Tests");
    String absolutePath = resourceDirectory.toFile().getAbsolutePath();
    return new String(Files.readAllBytes(Paths.get(absolutePath + "/" + filename)));
  }
}