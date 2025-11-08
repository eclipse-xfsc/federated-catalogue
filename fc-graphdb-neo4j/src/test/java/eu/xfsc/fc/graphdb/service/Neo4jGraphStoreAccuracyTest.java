package eu.xfsc.fc.graphdb.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

import eu.xfsc.fc.core.config.DatabaseConfig;
import eu.xfsc.fc.core.config.DidResolverConfig;
import eu.xfsc.fc.core.config.DocumentLoaderConfig;
import eu.xfsc.fc.core.config.DocumentLoaderProperties;
import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.dao.impl.SchemaDaoImpl;
import eu.xfsc.fc.core.dao.impl.SelfDescriptionDaoImpl;
import eu.xfsc.fc.core.dao.impl.ValidatorCacheDaoImpl;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResultOffering;
import eu.xfsc.fc.core.service.resolve.HttpDocumentResolver;
import eu.xfsc.fc.core.service.schemastore.SchemaStoreImpl;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStoreImpl;
import eu.xfsc.fc.core.service.verification.VerificationServiceImpl;
import eu.xfsc.fc.graphdb.config.GraphDbConfig;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase.DatabaseProvider;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {Neo4jGraphStoreAccuracyTest.TestApplication.class, DatabaseConfig.class, GraphDbConfig.class, FileStoreConfig.class, Neo4jGraphStoreAccuracyTest.class, 
	Neo4jGraphStore.class, SelfDescriptionStoreImpl.class, SelfDescriptionDaoImpl.class, VerificationServiceImpl.class, SchemaStoreImpl.class, SchemaDaoImpl.class, ValidatorCacheDaoImpl.class,
	DidResolverConfig.class, DocumentLoaderConfig.class, DocumentLoaderProperties.class, HttpDocumentResolver.class})
@AutoConfigureEmbeddedDatabase(provider = DatabaseProvider.ZONKY)
@Import(EmbeddedNeo4JConfig.class)
public class Neo4jGraphStoreAccuracyTest {
	
  //TODO:: We need to update SD  file when final implementation of claim parsing is done .
  private final String SERVICE_SD_FILE_NAME = "serviceOfferingSD.jsonld";
  private final String SERVICE_SD_FILE_NAME1 = "serviceOfferingSD1.jsonld";
  private final String SERVICE_SD_FILE_NAME2 = "serviceOfferingSD2.jsonld";
  private final String SERVICE_SD_FILE_NAME3 = "serviceOfferingSD3.jsonld";

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
  private SelfDescriptionStoreImpl sdStore;
  @Autowired
  private VerificationServiceImpl verificationService;

  
  @BeforeAll
  void addDBEntries() throws Exception {
    initialiseAllDataBaseWithManuallyAddingSD();
  }

  @AfterAll
  void closeNeo4j() {
    embeddedDatabaseServer.close();
  }

  @Test
  public void testCypherServiceOfferingAccuracy() throws Exception {


    List<Map<String, Object>> resultListExpected = List.of(Map.of("n", Map.of("name", "Portal", "claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal.json"))));

    GraphQuery queryDelta = new GraphQuery(
            "MATCH (n:ServiceOffering) WHERE n.name = $name RETURN n LIMIT $limit",
            Map.of("name", "Portal", "limit", 25));
    List<Map<String, Object>> responseList = neo4jGraphStore.queryData(queryDelta).getResults();
    Assertions.assertEquals(resultListExpected, responseList);
  }

  @Test
  public void testCypherServiceOfferingByURIAccuracy() throws Exception {
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
  public void testCypherAllServiceOfferingAccuracy() throws Exception {

    List<Map<String, Object>> resultListExpected = List.of(Map.of("n", Map.of("name", "Portal", "claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal.json"))),Map.of("n", Map.of("name", "Portal2", "claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal2.json"))),Map.of("n", Map.of("name", "Portal3", "claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal3.json"))),Map.of("n",Map.of("name","Portal2","claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceMVGPortal4.json"))));

    GraphQuery queryDelta = new GraphQuery(
            "MATCH (n:ServiceOffering)  RETURN n LIMIT $limit", Map.of("limit", 25));
    List<Map<String, Object>> responseList = neo4jGraphStore.queryData(queryDelta).getResults();
    Assertions.assertEquals(resultListExpected.size(), responseList.size());
  }


  @Test
  void testCypherAllServiceOfferingWithNameAndURI_IN_ClauseAccuracy() throws Exception {

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
  void testCypherTotalCount() throws Exception {

    GraphQuery queryDelta = new GraphQuery(
            "MATCH (n)  RETURN n LIMIT $limit", Map.of("limit", 25));
    List<Map<String, Object>> responseList = neo4jGraphStore.queryData(queryDelta).getResults();
    Assertions.assertEquals(5, responseList.size());
  }

  @Test
  void testQueryForGroupBYAndLocation() {
    String credentialSubject1 = "http://example.org/test-issuer";
    List<SdClaim> sdClaimList = Arrays.asList(
            new SdClaim(
                    "<http://example.org/test-issuer>",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Provider>"
            ),
            new SdClaim(
                    "<http://example.org/test-issuer>",
                    "<http://w3id.org/gaia-x/participant#legalAddress>",
                    "_:23"
            ),
            new SdClaim(
                    "<http://example.org/test-issuer>",
                    "<http://w3id.org/gaia-x/participant#legalName>",
                    "\"deltaDAO AG\""
            ),
            new SdClaim(
                    "<http://example.org/test-issuer>",
                    "<http://w3id.org/gaia-x/participant#name>",
                    "\"deltaDAO AG\""
            ),
            new SdClaim("_:23",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Address>"
            ),
            new SdClaim("_:23",
                    "<http://w3id.org/gaia-x/participant#country>",
                    "\"DE\""
            ),
            new SdClaim("_:23",
                    "<http://w3id.org/gaia-x/participant#locality>",
                    "\"Hamburg\""
            ),
            new SdClaim("_:23",
                    "<http://w3id.org/gaia-x/participant#postal-code>",
                    "\"22303\""
            ),
            new SdClaim("_:23",
                    "<http://w3id.org/gaia-x/participant#street-address>",
                    "\"GeibelstraГџe 46b\""
            )
    );

    String credentialSubject2 = "http://example.org/test-issuer2";
    List<SdClaim> sdClaimList2 = Arrays.asList(
            new SdClaim(
                    "<http://example.org/test-issuer2>",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Provider>"
            ),
            new SdClaim(
                    "<http://example.org/test-issuer2>",
                    "<http://w3id.org/gaia-x/participant#legalAddress>",
                    "_:b1"
            ),
            new SdClaim(
                    "<http://example.org/test-issuer2>",
                    "<http://w3id.org/gaia-x/participant#legalName>",
                    "\"deltaDAO AGE\""
            ),
            new SdClaim(
                    "<http://example.org/test-issuer2>",
                    "<http://w3id.org/gaia-x/participant#name>",
                    "\"deltaDAO AGE\""
            ),
            new SdClaim("_:b1",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Address>"
            ),
            new SdClaim("_:b1",
                    "<http://w3id.org/gaia-x/participant#country>",
                    "\"DE\""
            ),
            new SdClaim("_:b1",
                    "<http://w3id.org/gaia-x/participant#locality>",
                    "\"Dresden\""
            ),
            new SdClaim("_:b1",
                    "<http://w3id.org/gaia-x/participant#postal-code>",
                    "\"01067\""
            ),
            new SdClaim("_:b1",
                    "<http://w3id.org/gaia-x/participant#street-address>",
                    "\"Tried str 46b\""
            )
    );

    String credentialSubject3 = "http://example.org/test-issuer3";
    List<SdClaim> sdClaimList3 = Arrays.asList(
            new SdClaim(
                    "<http://example.org/test-issuer3>",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Provider>"
            ),
            new SdClaim(
                    "<http://example.org/test-issuer3>",
                    "<http://w3id.org/gaia-x/participant#legalAddress>",
                    "_:b2"
            ),
            new SdClaim(
                    "<http://example.org/test-issuer3>",
                    "<http://w3id.org/gaia-x/participant#legalName>",
                    "\"deltaDAO AGEF\""
            ),
            new SdClaim(
                    "<http://example.org/test-issuer3>",
                    "<http://w3id.org/gaia-x/participant#name>",
                    "\"deltaDAO AGEF\""
            ),
            new SdClaim("_:b2",
                    "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                    "<http://w3id.org/gaia-x/participant#Address>"
            ),
            new SdClaim("_:b2",
                    "<http://w3id.org/gaia-x/participant#country>",
                    "\"DE\""
            ),
            new SdClaim("_:b2",
                    "<http://w3id.org/gaia-x/participant#locality>",
                    "\"Dresden\""
            ),
            new SdClaim("_:b2",
                    "<http://w3id.org/gaia-x/participant#postal-code>",
                    "\"01069\""
            ),
            new SdClaim("_:b2",
                    "<http://w3id.org/gaia-x/participant#street-address>",
                    "\"Fried str 46b\""
            )
    );

    neo4jGraphStore.addClaims(sdClaimList, credentialSubject1);
    neo4jGraphStore.addClaims(sdClaimList2, credentialSubject2);
    neo4jGraphStore.addClaims(sdClaimList3, credentialSubject3);

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
    Map<String, Object>  resultActualMap = responseCypherByLocality.get(0);
    Assertions.assertEquals(2,responseCypherByLocality.size());
    //Assertions.assertEquals(Collections.singletonList("http://example.org/test-issuer2"), resultActualMap.get("n.claimsGraphUri"));

    //cleanup
    neo4jGraphStore.deleteClaims(credentialSubject1);
    neo4jGraphStore.deleteClaims(credentialSubject2);
    neo4jGraphStore.deleteClaims(credentialSubject3);
  }

  private void initialiseAllDataBaseWithManuallyAddingSD() throws Exception {

    ContentAccessorDirect contentAccessor = new ContentAccessorDirect(getMockFileDataAsString(SERVICE_SD_FILE_NAME));
    VerificationResultOffering verificationResult =
            (VerificationResultOffering) verificationService.verifySelfDescription(contentAccessor, true, false, false, false);

    //TODO:: adding manually claims, after final implementation we will remove it and change the query according to sd

    SdClaim sdClaim = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal.json>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://w3id.org/gaia-x/service#ServiceOffering>");

    SdClaim sdClaimName = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal.json>",
            "<http://w3id.org/gaia-x/service#name>",
            "\"Portal\"");

    List<SdClaim> sdClaimFile = List.of(sdClaim, sdClaimName);

    verificationResult.setClaims(sdClaimFile);
    verificationResult.setId(sdClaimFile.get(0).getSubjectValue());

    SelfDescriptionMetadata sdMetadata = new SelfDescriptionMetadata(verificationResult.getId(),
            verificationResult.getIssuer(), new ArrayList<>(), contentAccessor);
    sdStore.storeSelfDescription(sdMetadata, verificationResult);

    //adding 2 sd skipping sd validation as we don't have full implementation
    ContentAccessorDirect contentAccessorDirect2 =
            new ContentAccessorDirect(getMockFileDataAsString(SERVICE_SD_FILE_NAME1));
    VerificationResultOffering verificationResult2 =
            (VerificationResultOffering) verificationService.verifySelfDescription(contentAccessor, true, false, false, false);

    SdClaim sdClaim1 = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal2.json>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://w3id.org/gaia-x/service#ServiceOffering>");

    SdClaim sdClaimName1 = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal2.json>",
            "<http://w3id.org/gaia-x/service#name>",
            "\"Portal2\"");

    List<SdClaim> sdClaimFile1 = List.of(sdClaim1, sdClaimName1);

    verificationResult2.setClaims(sdClaimFile1);
    verificationResult2.setId(sdClaimFile1.get(0).getSubjectValue());

    SelfDescriptionMetadata sdMetadata2 = new SelfDescriptionMetadata(
            verificationResult2.getId(),
            verificationResult2.getIssuer(), new ArrayList<>(), contentAccessorDirect2);
    sdStore.storeSelfDescription(sdMetadata2, verificationResult2);

    //adding sd 3
    ContentAccessorDirect contentAccessorDirect3 =
            new ContentAccessorDirect(getMockFileDataAsString(SERVICE_SD_FILE_NAME2));
    VerificationResultOffering verificationResult3 =
            (VerificationResultOffering) verificationService.verifySelfDescription(contentAccessor, true, false, false, false);

    SdClaim sdClaim3 = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal3.json>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://w3id.org/gaia-x/service#ServiceOffering>");

    SdClaim sdClaimName3 = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal3.json>",
            "<http://w3id.org/gaia-x/service#name>",
            " \"Portal3\"");

    List<SdClaim> sdClaimFile3 = List.of(sdClaim3, sdClaimName3);

    verificationResult3.setClaims(sdClaimFile3);
    verificationResult3.setId(sdClaimFile3.get(0).getSubjectValue());

    SelfDescriptionMetadata sdMetadata3 = new SelfDescriptionMetadata(
            verificationResult3.getId(),
            verificationResult3.getIssuer(), new ArrayList<>(), contentAccessorDirect3);
    sdStore.storeSelfDescription(sdMetadata3, verificationResult3);


    //adding sd 3
    ContentAccessorDirect contentAccessorDirect4 =
            new ContentAccessorDirect(getMockFileDataAsString(SERVICE_SD_FILE_NAME3));
    VerificationResultOffering verificationResult4 =
            (VerificationResultOffering) verificationService.verifySelfDescription(contentAccessor, true, false, false, false);


    SdClaim sdClaim4 = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal4.json>",
            "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
            "<http://w3id.org/gaia-x/service#ServiceOffering>");

    SdClaim sdClaimName4 = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceMVGPortal4.json>",
            "<http://w3id.org/gaia-x/service#name>",
            "\"Portal2\"");


    List<SdClaim> sdClaimFile4 = List.of(sdClaim4, sdClaimName4);

    verificationResult4.setClaims(sdClaimFile4);
    verificationResult4.setId(sdClaimFile4.get(0).getSubjectValue());

    SelfDescriptionMetadata sdMetadata4 = new SelfDescriptionMetadata(
            verificationResult4.getId(),
            verificationResult4.getIssuer(), new ArrayList<>(), contentAccessorDirect4);
    sdStore.storeSelfDescription(sdMetadata4, verificationResult4);
  }

  public static String getMockFileDataAsString(String filename) throws IOException {
    Path resourceDirectory = Paths.get("src", "test", "resources", "Query-Tests");
    String absolutePath = resourceDirectory.toFile().getAbsolutePath();
    return new String(Files.readAllBytes(Paths.get(absolutePath + "/" + filename)));
  }
}