package eu.xfsc.fc.graphdb.service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.FixMethodOrder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.runners.MethodSorters;
import org.neo4j.harness.Neo4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.QueryException;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.graphdb.config.EmbeddedNeo4JConfig;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@SpringBootTest
@ActiveProfiles({"test"}) 
@ContextConfiguration(classes = {Neo4jGraphStore.class})
@Import(EmbeddedNeo4JConfig.class)
public class Neo4jGraphStoreTest {

    @Value("${graphstore.query-timeout-in-seconds}")
    private int queryTimeoutInSeconds;

    @Autowired
    private Neo4j embeddedDatabaseServer;

    @Autowired
    private Neo4jGraphStore graphGaia;

    @AfterAll
    void closeNeo4j() {
        embeddedDatabaseServer.close();
    }

    @Test
    void testCypherQueriesSyntax() throws Exception {

        List<SdClaim> sdClaimFile = loadTestClaims("Claims-Tests/claimsForQuery.nt");
        List<Map<String, String>> resultListFull = new ArrayList<Map<String, String>>();
        Map<String, String> mapFull = new HashMap<String, String>();
        mapFull.put("n.uri", "http://w3id.org/gaia-x/indiv#serviceMVGPortal.json");
        resultListFull.add(mapFull);
        Map<String, String> mapFullES = new HashMap<String, String>();
        mapFullES.put("n.uri", "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json");
        resultListFull.add(mapFullES);
        for (SdClaim sdClaim : sdClaimFile) {
            List<SdClaim> sdClaimList = new ArrayList<>();
            sdClaimList.add(sdClaim);
            String credentialSubject = sdClaimList.get(0).getSubjectString();
            graphGaia.addClaims(
                    sdClaimList,
                    credentialSubject.substring(1, credentialSubject.length() - 1));
        }
        GraphQuery query = new GraphQuery("MATCH (n) RETURN * LIMIT 25", Map.of(), 
                QueryLanguage.OPENCYPHER, GraphQuery.QUERY_TIMEOUT, false);
        List<Map<String, Object>> responseFull = graphGaia.queryData(query).getResults();
        Assertions.assertEquals(6, responseFull.size());
    }


    @Test
    void testCypherQueriesSyntaxTotalCountFlagTrue() throws Exception {

        List<SdClaim> sdClaimFile = loadTestClaims("Claims-Tests/claimsForQuery.nt");
        List<Map<String, String>> resultListFull = new ArrayList<Map<String, String>>();
        Map<String, String> mapFull = new HashMap<String, String>();
        mapFull.put("n.uri", "http://w3id.org/gaia-x/indiv#serviceMVGPortal.json");
        resultListFull.add(mapFull);
        Map<String, String> mapFullES = new HashMap<String, String>();
        mapFullES.put("n.uri", "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json");
        resultListFull.add(mapFullES);
        for (SdClaim sdClaim : sdClaimFile) {
            List<SdClaim> sdClaimList = new ArrayList<>();
            sdClaimList.add(sdClaim);
            String credentialSubject = sdClaimList.get(0).getSubjectString();
            graphGaia.addClaims(
                sdClaimList,
                credentialSubject.substring(1, credentialSubject.length() - 1));
        }
        GraphQuery query = new GraphQuery("MATCH (n) RETURN * LIMIT 25", Map.of(),
            QueryLanguage.OPENCYPHER, GraphQuery.QUERY_TIMEOUT, true);
        List<Map<String, Object>> responseFull = graphGaia.queryData(query).getResults();
        long totalCount = graphGaia.queryData(query).getTotalCount();
        Assertions.assertEquals(6, responseFull.size());
        Assertions.assertEquals(6, totalCount);
    }
    /**
     * Given set of credentials connect to graph and upload self description.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form and upload to graph. Verify if the claim has been uploaded using
     * query service
     */
    @Test
    void testCypherQueriesFull() throws Exception {

        List<SdClaim> sdClaimFile = loadTestClaims("Claims-Tests/claimsForQuery.nt");
        List<Map<String, String>> resultListFull = new ArrayList<Map<String, String>>();
        Map<String, String> mapFull = new HashMap<String, String>();
        mapFull.put("n.uri", "http://w3id.org/gaia-x/indiv#serviceMVGPortal.json");
        resultListFull.add(mapFull);
        Map<String, String> mapFullES = new HashMap<String, String>();
        mapFullES.put("n.uri", "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json");
        resultListFull.add(mapFullES);
        for (SdClaim sdClaim : sdClaimFile) {
            List<SdClaim> sdClaimList = new ArrayList<>();
            sdClaimList.add(sdClaim);
            String credentialSubject = sdClaimList.get(0).getSubjectString();
            graphGaia.addClaims(
                    sdClaimList,
                    credentialSubject.substring(1, credentialSubject.length() - 1));
        }
        GraphQuery queryFull = new GraphQuery(
                "MATCH (n:ServiceOffering) RETURN n.uri LIMIT 25", Map.of());
        List<Map<String, Object>> responseFull = graphGaia.queryData(queryFull).getResults();
        Assertions.assertEquals(resultListFull.size(), responseFull.size());
    }

    /**
     * Given set of credentials connect to graph and upload self description.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form along with literals and upload to graph. Verify if the claim has
     * been uploaded using query service
     */

    @Test
    void testCypherDelta() throws Exception {

        List<SdClaim> sdClaimFile = loadTestClaims("Claims-Tests/claimsForQuery.nt");
        List<Map<String, String>> resultListDelta = new ArrayList<Map<String, String>>();
        Map<String, String> mapDelta = new HashMap<String, String>();
        mapDelta.put("n.uri", "https://delta-dao.com/.well-known/participant.json");
        resultListDelta.add(mapDelta);
        for (SdClaim sdClaim : sdClaimFile) {
            List<SdClaim> sdClaimList = new ArrayList<>();
            sdClaimList.add(sdClaim);
            String credentialSubject = sdClaimList.get(0).getSubjectString();
            graphGaia.addClaims(sdClaimList, credentialSubject.substring(1, credentialSubject.length() - 1));
        }
        //GraphQuery queryDeltaTest = new GraphQuery("Match(n) RETURN n", null);
        GraphQuery queryDelta = new GraphQuery(
                "MATCH (n:LegalPerson) WHERE n.name = $name RETURN n LIMIT $limit", Map.of("name", "deltaDAO AG", "limit", 25));
        List<Map<String, Object>> responseDelta = graphGaia.queryData(queryDelta).getResults();
        Assertions.assertEquals(1, responseDelta.size());
    }


    /**
     * Given set of credentials connect to graph and upload self description.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form along with literals and upload to graph. Verify if the claim has
     * been uploaded using query service by asking for its signature
     */

    @Test
    void testCypherSignatureQuery() throws Exception {

        String credentialSubject = "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json";
        List<SdClaim> sdClaimList = Arrays.asList(
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                ),
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://w3id.org/gaia-x/service#name>",
                        "\"Elastic Search DB\""
                ),
                new SdClaim(
                        "<http:ex.com/some_service>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                ),
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://ex.com/some_property>",
                        "_:23"
                ),
                new SdClaim(
                        "_:23",
                        "<http://ex.com/some_other_property>",
                        "<http:ex.com/some_service>"
                )
        );
        graphGaia.addClaims(sdClaimList, credentialSubject);
        List<Map<String, String>> resultListDelta = new ArrayList<Map<String, String>>();
        Map<String, String> mapDelta = new HashMap<String, String>();
        mapDelta.put("n.uri", "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json");
        resultListDelta.add(mapDelta);
        GraphQuery queryDelta = new GraphQuery(
                "MATCH (n:ServiceOffering) WHERE n.name = $name RETURN n.uri LIMIT $limit", Map.of("name", "Elastic Search DB", "limit", 25));
        List<Map<String, Object>> responseDelta = graphGaia.queryData(queryDelta).getResults();
        Assertions.assertEquals(resultListDelta, responseDelta);
        //cleanup
        graphGaia.deleteClaims(credentialSubject);
    }

    /**
     * Given set of credentials connect to graph and upload self description.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form along with literals and upload to graph.
     */
    @Test
    void testAddClaims() throws Exception {
        List<SdClaim> sdClaimList = new ArrayList<>();
        SdClaim sdClaim = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<http://w3id.org/gaia-x/service#ServiceOffering>");
        sdClaimList.add(sdClaim);
        SdClaim sdClaimSecond = new SdClaim("<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>", "<http://w3id.org/gaia-x/service#providedBy>", "<https://delta-dao.com/.well-known/participant.json>");
        sdClaimList.add(sdClaimSecond);
        graphGaia.addClaims(sdClaimList, "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json");
        graphGaia.deleteClaims("http://w3id.org/gaia-x/indiv#serviceElasticSearch.json");
    }


    /**
     * Given set of credentials connect to graph and upload self description.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form which is invalid and try uploading to graphDB
     */
    @Test
    void testAddClaimsException() throws Exception {
        String credentialSubject = "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json";
        //String wrongCredentialSubject = "http://w3id.org/gaia-x/indiv#serviceElasticSearch";

        SdClaim syntacticallyCorrectClaim = new SdClaim(
                "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                "<http://w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://w3id.org/gaia-x/service#ServiceOffering>"
        );

        SdClaim claimWBrokenSubject = new SdClaim(
                "<__http://w3id.org/gaia-x/indiv#serviceElasticSearch.json__>",
                "<http://w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://w3id.org/gaia-x/service#ServiceOffering>"
        );

        SdClaim claimWBrokenPredicate = new SdClaim(
                "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                "<__http://w3.org/1999/02/22-rdf-syntax-ns#type__>",
                "<http://w3id.org/gaia-x/service#ServiceOffering>"
        );

        SdClaim claimWBrokenObjectIRI = new SdClaim(
                "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<__http://w3id.org/gaia-x/service#ServiceOffering__>"
        );

        SdClaim claimWBrokenLiteral01 = new SdClaim(
                "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                "<http://www.w3.org/2000/01/rdf-schema#label>",
                "\"Fourty two\"^^<http://www.w3.org/2001/XMLSchema#int>"
        );

        SdClaim claimWBrokenLiteral02 = new SdClaim(
                "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                "<http://www.w3.org/2000/01/rdf-schema#label>",
                "\"Missing quotes^^<http://www.w3.org/2001/XMLSchema#string>"
        );

        SdClaim claimWBlankNodeSubject = new SdClaim(
                "_:23",
                "<http://ex.com/some_property>",
                "<http://ex.com/resource23>"
        );

        SdClaim claimWBlankNodeObject = new SdClaim(
                "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                "<http://ex.com/some_property>",
                "_:23"
        );

        SdClaim claimWDIDSubject = new SdClaim(
                "<did:example:123456789#v1>",
                "<http://ex.com/some_property>",
                "<http://ex.com/resource23>"
        );

        SdClaim claimWDIDObject = new SdClaim(
                "<http://ex.com/resource42>",
                "<http://ex.com/some_property>",
                "<did:example:987654321#v2>"
        );

        // Everything should work well with the syntactically correct claim
        // and the correct credential subject
        Assertions.assertDoesNotThrow(
                () -> graphGaia.addClaims(
                        Collections.singletonList(syntacticallyCorrectClaim),
                        credentialSubject
                ),
                "A syntactically correct triple should pass but " +
                        "was rejected by the claim validation"
        );

        // If a claim with a broken subject was passed it should be rejected
        // with a server exception
        Exception exception = Assertions.assertThrows(
                QueryException.class,
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWBrokenSubject),
                        credentialSubject
                ),
                "A syntax error should have been found for the " +
                        "invalid URI of the input triple subject, but wasn't"
        );
        Assertions.assertTrue(
                exception.getMessage().contains("Subject in triple"),
                "Syntax error should have been found for the triple " +
                        "subject, but wasn't");

        // If a claim with a broken predicate was passed it should be rejected
        // with a server exception
        exception = Assertions.assertThrows(
                QueryException.class,
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWBrokenPredicate),
                        credentialSubject
                ),
                "A syntax error should have been found for the " +
                        "invalid URI of the input triple predicate, but wasn't"
        );
        Assertions.assertTrue(
                exception.getMessage().contains("Predicate in triple"),
                "A syntax error should have been found for the " +
                        "triple predicate, but wasn't");

        // If a claim with a resource on object position was passed and the URI
        // of the resource was broken, the claim should be rejected with a
        // server error
        exception = Assertions.assertThrows(
                QueryException.class,
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWBrokenObjectIRI),
                        credentialSubject
                ),
                "A syntax error should have been found for the " +
                        "invalid URI of the input triple object, but wasn't"
        );
        Assertions.assertTrue(
                exception.getMessage().contains("Object in triple"),
                "A syntax error should have been found for the " +
                        "triple object, but wasn't"
        );

        // If a claim with a literal on object position was passed and the
        // literal was broken, the claim should be rejected with a server error.
        // 1) Wrong datatype
        exception = Assertions.assertThrows(
                QueryException.class,
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWBrokenLiteral01),
                        credentialSubject
                ),
                "A syntax error should have been found for the " +
                        "broken input literal, but wasn't"
        );
        // 2) Syntax error
        exception = Assertions.assertThrows(
                QueryException.class,
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWBrokenLiteral02),
                        credentialSubject
                ),
                "A syntax error should have been found for the " +
                        "broken input literal, but wasn't"
        );

        // blank nodes should pass
        Assertions.assertDoesNotThrow(
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWBlankNodeSubject),
                        credentialSubject
                ),
                "A blank node should be accepted on a triple's " +
                        "subject position but was rejected by the claim " +
                        "validation"
        );

        Assertions.assertDoesNotThrow(
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWBlankNodeObject),
                        credentialSubject
                ),
                "A blank node should be accepted on a triple's " +
                        "object position but was rejected by the claim " +
                        "validation"
        );

        // DIDs should pass as well
        Assertions.assertDoesNotThrow(
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWDIDSubject),
                        credentialSubject
                ),
                "A DID should be accepted on a triple's subject " +
                        "position but was rejected by the claim validation"
        );

        Assertions.assertDoesNotThrow(
                () -> graphGaia.addClaims(
                        Collections.singletonList(claimWDIDObject),
                        credentialSubject
                ),
                "A DID should be accepted on a triple's object " +
                        "position but was rejected by the claim validation"
        );
        //cleanup
        graphGaia.deleteClaims(credentialSubject);
    }

    private List<SdClaim> loadTestClaims(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String strLine;
            List<SdClaim> sdClaimList = new ArrayList<>();
            while ((strLine = br.readLine()) != null) {
                Pattern regex = Pattern.compile("[^\\s\"']+|\"[^\"]*\"|'[^']*'");
                Matcher regexMatcher = regex.matcher(strLine);
                int i = 0;
                String subject = "";
                String predicate = "";
                String object = "";
                while (regexMatcher.find()) {
                    if (i == 0) {
                        subject = regexMatcher.group().toString();
                    } else if (i == 1) {
                        predicate = regexMatcher.group().toString();
                    } else if (i == 2) {
                        object = regexMatcher.group().toString();
                    }
                    i++;
                }
                SdClaim sdClaim = new SdClaim(subject, predicate, object);
                sdClaimList.add(sdClaim);
            }
            return sdClaimList;
        }
    }

    @Test
    void testRejectQueriesThatModifyData() throws Exception {
        GraphQuery queryDelete = new GraphQuery(
                "MATCH (n) DETACH DELETE n;", null);
        Assertions.assertThrows(
                ServerException.class,
                () -> {
                    graphGaia.queryData(queryDelete);
                }
        );

        GraphQuery queryUpdate = new GraphQuery(
                "MATCH (n) SET n.name = 'Santa' RETURN n;", null);
        Assertions.assertThrows(
                ServerException.class,
                () -> {
                    graphGaia.queryData(queryUpdate);
                }
        );
    }

    /**
     * This test adds two sets of claims and after deleting the first set -
     * there should be no nodes with their graphUri list containing the
     * credential subject of the first set - no added nodes referenced by their
     * URI directly.
     * <p>
     * But the nodes of the second set of claims should still be there, assuring
     * we do not delete more than the claims of the first set.
     * <p>
     * TODO: Extend the test to check shared nodes which are in both sets
     */
    @Test
    void testDeleteClaims() {
        String credentialSubject1 = "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json";
        List<SdClaim> sdClaimList = Arrays.asList(
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                ),
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://ex.com/some_property>",
                        "_:23"
                ),
                new SdClaim(
                        "_:23",
                        "<http://ex.com/some_other_property>",
                        "<http:ex.com/some_service>"
                ),
                new SdClaim(
                        "<http:ex.com/some_service>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                )
        );

        String credentialSubject2 = "http://ex.com/credentialSubject2";
        List<SdClaim> sdClaimsWOtherCredSubject = Arrays.asList(
                new SdClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                ),
                new SdClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://ex.com/some_property>",
                        "<http://ex.com/resource23>"
                )
        );

        try {
        graphGaia.addClaims(sdClaimList, credentialSubject1);
        graphGaia.addClaims(sdClaimsWOtherCredSubject, credentialSubject2);

        graphGaia.deleteClaims(credentialSubject1);
        } catch (Exception ex) {
        	ex.printStackTrace();
        	throw ex;
        }
        // The (virtual) graph of nodes belonging to credentialSubject1 should
        // be empty
        GraphQuery queryDelta = new GraphQuery(
                "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
                Map.of("graphUri", credentialSubject1));

        List<Map<String, Object>> responseDelta = graphGaia.queryData(queryDelta).getResults();
        Assertions.assertTrue(responseDelta.isEmpty());

        // The credentialSubject1 node should be gone
        queryDelta = new GraphQuery(
                "MATCH (n {uri: $uri}) RETURN n",
                Map.of("uri", credentialSubject1)
        );
        responseDelta = graphGaia.queryData(queryDelta).getResults();
        Assertions.assertTrue(responseDelta.isEmpty());

        // But the other claims belonging to the (virtual) graph of
        // credentialSubject2 should still be there. There are two:
        // - <http://ex.com/credentialSubject2>
        // - <http://ex.com/resource23>
        queryDelta = new GraphQuery(
                "MATCH (n) WHERE $graphUri IN n.claimsGraphUri RETURN n",
                Map.of("graphUri", credentialSubject2)
        );
        responseDelta = graphGaia.queryData(queryDelta).getResults();
        Assertions.assertEquals(2, responseDelta.size());

        // clean up
        graphGaia.deleteClaims(credentialSubject1);
        graphGaia.deleteClaims(credentialSubject2);
    }

    /**
     * This test checks for a property for a given credential subject and
     * returns uri of the subject if it exists
     */
    @Test
    void testAssertionQuery() {
        String credentialSubject1 = "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json";
        List<SdClaim> sdClaimList = Arrays.asList(
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                ),
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://ex.com/some_property>",
                        "_:23"
                ),
                new SdClaim(
                        "_:23",
                        "<http://ex.com/some_other_property>",
                        "<http:ex.com/some_service>"
                ),
                new SdClaim(
                        "<http:ex.com/some_service>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                )
        );

        String credentialSubject2 = "http://ex.com/credentialSubject2";
        List<SdClaim> sdClaimsWOtherCredSubject = Arrays.asList(
                new SdClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                ),
                new SdClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://ex.com/some_property>",
                        "<http://ex.com/resource23>"
                )
        );

        graphGaia.addClaims(sdClaimList, credentialSubject1);
        graphGaia.addClaims(sdClaimsWOtherCredSubject, credentialSubject2);
        GraphQuery queryCypher = new GraphQuery("MATCH (n)-[:some_property]->(m) RETURN n", null);
        List<Map<String, Object>> responseCypher = graphGaia.queryData(queryCypher).getResults();
        List<Map<String, Object>> resultListSomeProperty = List.of(Map.of("n", Map.of("claimsGraphUri", List.of("http://w3id.org/gaia-x/indiv#serviceElasticSearch.json"))), Map.of("n", Map.of("claimsGraphUri", List.of("http://ex.com/credentialSubject2"))));
        Assertions.assertEquals(resultListSomeProperty.size(), responseCypher.size());
        //cleanup
        graphGaia.deleteClaims(credentialSubject1);
        graphGaia.deleteClaims(credentialSubject2);
    }

    /**
     * This test checks for a pattern if subject of type service offering has
     * the given two properties and matches label against one to return uri of
     * the subject if true
     */
    @Test
    void testQueryOffering() {

        String credentialSubject = "http://example.org/test-issuer2";
        List<SdClaim> sdClaimList = Arrays.asList(
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

        graphGaia.addClaims(sdClaimList, credentialSubject);
        GraphQuery queryCypher = new GraphQuery("MATCH (m)-[:legalAddress]->(n) RETURN LABELS(m) as type,n as legalAddress,m.legalName as legalName,m.name as name", null);
        List<Map<String, Object>> responseCypher = graphGaia.queryData(queryCypher).getResults();
        Assertions.assertEquals(1, responseCypher.size());
        //cleanup
        graphGaia.deleteClaims(credentialSubject);
    }

    /**
     * This test checks for a pattern if subject of type service offering has
     * the given two properties and matches label against one to return uri of
     * the subject if true
     */
    @Test
    void testAssertionComplexQuery() {
        String credentialSubject1 = "http://w3id.org/gaia-x/indiv#serviceElasticSearch.json";
        List<SdClaim> sdClaimList = Arrays.asList(
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                ),
                new SdClaim(
                        "<http://w3id.org/gaia-x/indiv#serviceElasticSearch.json>",
                        "<http://ex.com/some_property>",
                        "_:23"
                ),
                new SdClaim(
                        "_:23",
                        "<http://ex.com/some_other_property>",
                        "<http:ex.com/some_service>"
                ),
                new SdClaim(
                        "<http:ex.com/some_service>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                )
        );

        String credentialSubject2 = "http://ex.com/credentialSubject2";
        List<SdClaim> sdClaimsWOtherCredSubject = Arrays.asList(
                new SdClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<http://w3id.org/gaia-x/service#ServiceOffering>"
                ),
                new SdClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://ex.com/some_property>",
                        "<http://ex.com/resource23>"
                ),
                new SdClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://ex.com/some_other_property>",
                        "<http://ex.com/resource24>"
                ),
                new SdClaim(
                        "<http://ex.com/resource24>",
                        "<http://www.w3.org/2000/01/rdf-schema#label>",
                        "\"resource24\""
                )
        );

        graphGaia.addClaims(sdClaimList, credentialSubject1);
        graphGaia.addClaims(sdClaimsWOtherCredSubject, credentialSubject2);
        GraphQuery queryCypher = new GraphQuery("MATCH (o)<-[:some_other_property]-(n:ServiceOffering)-[:some_property]->(m) WHERE o.label= $some_other_property RETURN n.uri", Map.of("some_other_property", "resource24"));
        List<Map<String, Object>> responseCypher = graphGaia.queryData(queryCypher).getResults();
        List<Map<String, String>> resultListSomeProperty = new ArrayList<Map<String, String>>();
        Map<String, String> mapES = new HashMap<String, String>();
        Map<String, String> mapCredentialSubject2 = new HashMap<String, String>();
        mapCredentialSubject2.put("n.uri", "http://ex.com/credentialSubject2");
        resultListSomeProperty.add(mapCredentialSubject2);
        Assertions.assertEquals(resultListSomeProperty, responseCypher);
        //cleanup
        graphGaia.deleteClaims(credentialSubject1);
        graphGaia.deleteClaims(credentialSubject2);
    }


    @Test
    void AssertionRelationshipNode() {
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

        graphGaia.addClaims(sdClaimList, credentialSubject1);
        graphGaia.addClaims(sdClaimList2, credentialSubject2);
        graphGaia.addClaims(sdClaimList3, credentialSubject3);

        GraphQuery queryCypher = new GraphQuery("MATCH (m) -[relation]-> (n) WHERE 'http://example.org/test-issuer3' in m.claimsGraphUri RETURN m.name as name , relation, n.country as country, n.locality as locality ", null);
        List<Map<String, Object>> responseCypher = graphGaia.queryData(queryCypher).getResults();
        List<Map<String, Object>> resultListRelationship = List.of(Map.of("country", "DE", "name", "deltaDAO AGEF", "locality", "Dresden", "relation", "legalAddress"));
        Assertions.assertEquals(resultListRelationship, responseCypher);

        //cleanup
        graphGaia.deleteClaims(credentialSubject1);
        graphGaia.deleteClaims(credentialSubject2);
        graphGaia.deleteClaims(credentialSubject3);
    }


    @Test
    void testClaimsMissing() {
        String credentialSubject = "https://www.example.org/mySoftwareOffering";
        List<SdClaim> sdClaimList = Arrays.asList(new SdClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/gax-trust-framework#SoftwareOffering>"),
                new SdClaim("_:b0", "<https://w3id.org/gaia-x/gax-trust-framework#accessType>", "\"access type\""),
                new SdClaim("_:b0", "<https://w3id.org/gaia-x/gax-trust-framework#formatType>", "\"format type\""),
                new SdClaim("_:b0", "<https://w3id.org/gaia-x/gax-trust-framework#requestType>", "\"request type\""),
                new SdClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/core#TermsAndConditions>"),
                new SdClaim("_:b1", "<https://w3id.org/gaia-x/gax-trust-framework#content>", "\"http://example.org/tac\""),
                new SdClaim("_:b1", "<https://w3id.org/gaia-x/gax-trust-framework#hash>", "\"1234\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/gax-trust-framework#ServiceOffering>"),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_1\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_2\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_3\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_4\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_5\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_6\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_7\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<https://w3id.org/gaia-x/gax-trust-framework#mySoftwareOffering>", "_:b0"),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<https://w3id.org/gaia-x/gax-trust-framework#providedBy>", "<gax-participant:Provider1>"),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<https://w3id.org/gaia-x/gax-trust-framework#serviceTitle>", "\"Software Title\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering>", "<https://w3id.org/gaia-x/gax-trust-framework#termsAndConditions>", "_:b1"));
        graphGaia.addClaims(sdClaimList, credentialSubject);
        GraphQuery queryCypher = new GraphQuery("match(m:ServiceOffering) return m", null);
        List<Map<String, Object>> responseCypher = graphGaia.queryData(queryCypher).getResults();
        Assertions.assertEquals(2, responseCypher.size());
        String credentialSubject2 = "https://www.example.org/mySoftwareOffering2";
        List<SdClaim> sdClaimList2 = Arrays.asList(new SdClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/gax-trust-framework#SoftwareOffering2>"),
                new SdClaim("_:b0", "<https://w3id.org/gaia-x/gax-trust-framework#accessType>", "\"access type\""),
                new SdClaim("_:b0", "<https://w3id.org/gaia-x/gax-trust-framework#formatType>", "\"format type\""),
                new SdClaim("_:b0", "<https://w3id.org/gaia-x/gax-trust-framework#requestType>", "\"request type\""),
                new SdClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/core#TermsAndConditions>"),
                new SdClaim("_:b1", "<https://w3id.org/gaia-x/gax-trust-framework#content>", "\"http://example.org/tac\""),
                new SdClaim("_:b1", "<https://w3id.org/gaia-x/gax-trust-framework#hash>", "\"12345\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/gax-trust-framework#ServiceOffering2>"),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword2>", "\"Keyword1_1\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword2>", "\"Keyword1_2\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword2>", "\"Keyword1_3\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_4\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_5\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_6\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_7\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<https://w3id.org/gaia-x/gax-trust-framework#mySoftwareOffering>", "_:b0"),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<https://w3id.org/gaia-x/gax-trust-framework#providedBy>", "<gax-participant:Provider1>"),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<https://w3id.org/gaia-x/gax-trust-framework#serviceTitle>", "\"Software Title 2\""),
                new SdClaim("<https://www.example.org/mySoftwareOffering2>", "<https://w3id.org/gaia-x/gax-trust-framework#termsAndConditions>", "_:b1"));

        graphGaia.addClaims(sdClaimList2, credentialSubject2);
        GraphQuery queryCypher2 = new GraphQuery("match(m:ServiceOffering2)-[relation]-> (n) return m,relation,n", null);
        List<Map<String, Object>> responseCypher2 = graphGaia.queryData(queryCypher2).getResults();

        for (Map<String, Object> map : responseCypher2) {

            Map<String, Object> offering = (Map<String, Object>) map.get("m");
            Object claimsGraphUri = offering.get("claimsGraphUri");
            Assertions.assertNotNull(claimsGraphUri);
            Assertions.assertTrue(claimsGraphUri instanceof List);
            List<String> urls = (List<String>) claimsGraphUri;
            Assertions.assertEquals(List.of("https://www.example.org/mySoftwareOffering2"), urls);
            Assertions.assertEquals("Software Title 2", offering.get("serviceTitle"));
            Object keywords_1 = offering.get("keyword");
            List<String> keywords = (List<String>) keywords_1;
            Object keywords_2 = offering.get("keyword2");
            List<String> keywords2 = (List<String>) keywords_2;

            Assertions.assertEquals(4, keywords.size()); // [Keyword1_7, Keyword1_5, Keyword1_6, Keyword1_4]
            Assertions.assertEquals(3, keywords2.size()); // [Keyword1_1, Keyword1_3, Keyword1_2]

            String rel = (String) map.get("relation");
            if ("mySoftwareOffering".equals(rel)) {
                Map<String, Object> soft = (Map<String, Object>) map.get("n");
                Assertions.assertEquals("access type", soft.get("accessType"));
                Assertions.assertEquals("request type", soft.get("requestType"));
                Assertions.assertEquals("format type", soft.get("formatType"));
            } else if ("termsAndConditions".equals(rel)) {
                Map<String, Object> terms = (Map<String, Object>) map.get("n");
                Assertions.assertEquals("http://example.org/tac", terms.get("content"));
                Assertions.assertEquals("12345", terms.get("hash"));
            } else if ("providedBy".equals(rel)) {
                Map<String, Object> prov = (Map<String, Object>) map.get("n");
                Object nClaimsGraphUri = prov.get("claimsGraphUri");
                List<String> urlGraphuri = (List<String>) nClaimsGraphUri;
                Assertions.assertEquals(List.of("https://www.example.org/mySoftwareOffering", "https://www.example.org/mySoftwareOffering2"), urlGraphuri);
            } else {
                Assertions.assertFalse(true, "unexpected relation: " + rel);
            }

            Assertions.assertEquals(2, responseCypher.size());
            graphGaia.deleteClaims(credentialSubject);
            graphGaia.deleteClaims(credentialSubject2);

        }
    }


    @Test
    void testQueryDataTimeout() {
        int acceptableDuration = (queryTimeoutInSeconds - 1) * 1000;
        int tooLongDuration = (queryTimeoutInSeconds + 2) * 1000;  // two seconds more than acceptable

        Assertions.assertDoesNotThrow(
                () -> graphGaia.queryData(
                        new GraphQuery(
                                "CALL apoc.util.sleep(" + acceptableDuration + ")", null
                        )
                )
        );

        Assertions.assertThrows(
                TimeoutException.class,
                () -> graphGaia.queryData(
                        new GraphQuery(
                                "CALL apoc.util.sleep(" + tooLongDuration + ")", null
                        )
                )
        );
    }
}
