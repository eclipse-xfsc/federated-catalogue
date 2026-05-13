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

import eu.xfsc.fc.core.pojo.CredentialClaim;
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
import eu.xfsc.fc.core.pojo.RdfClaim;
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

        List<RdfClaim> claimFile = loadTestClaims("Claims-Tests/claimsForQuery.nt");
        List<Map<String, String>> resultListFull = new ArrayList<Map<String, String>>();
        Map<String, String> mapFull = new HashMap<String, String>();
        mapFull.put("n.uri", "https://w3id.org/gaia-x/2511#serviceMVGPortal.json");
        resultListFull.add(mapFull);
        Map<String, String> mapFullES = new HashMap<String, String>();
        mapFullES.put("n.uri", "https://w3id.org/gaia-x/2511#serviceElasticSearch.json");
        resultListFull.add(mapFullES);
        for (RdfClaim claim : claimFile) {
            List<RdfClaim> claimList = new ArrayList<>();
            claimList.add(claim);
            String credentialSubject = claimList.getFirst().getSubjectString();
            graphGaia.addClaims(
                    claimList,
                    credentialSubject.substring(1, credentialSubject.length() - 1));
        }
        GraphQuery query = new GraphQuery("MATCH (n) RETURN * LIMIT 25", Map.of(), 
                QueryLanguage.OPENCYPHER, GraphQuery.QUERY_TIMEOUT, false);
        List<Map<String, Object>> responseFull = graphGaia.queryData(query).getResults();
        Assertions.assertEquals(6, responseFull.size());
    }


    @Test
    void testCypherQueriesSyntaxTotalCountFlagTrue() throws Exception {

        List<RdfClaim> claimFile = loadTestClaims("Claims-Tests/claimsForQuery.nt");
        List<Map<String, String>> resultListFull = new ArrayList<Map<String, String>>();
        Map<String, String> mapFull = new HashMap<String, String>();
        mapFull.put("n.uri", "https://w3id.org/gaia-x/2511#serviceMVGPortal.json");
        resultListFull.add(mapFull);
        Map<String, String> mapFullES = new HashMap<String, String>();
        mapFullES.put("n.uri", "https://w3id.org/gaia-x/2511#serviceElasticSearch.json");
        resultListFull.add(mapFullES);
        for (RdfClaim claim : claimFile) {
            List<RdfClaim> claimList = new ArrayList<>();
            claimList.add(claim);
            String credentialSubject = claimList.getFirst().getSubjectString();
            graphGaia.addClaims(
                claimList,
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
     * Given set of credentials connect to graph and upload claims.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form and upload to graph. Verify if the claim has been uploaded using
     * query service
     */
    @Test
    void testCypherQueriesFull() throws Exception {

        List<RdfClaim> claimFile = loadTestClaims("Claims-Tests/claimsForQuery.nt");
        List<Map<String, String>> resultListFull = new ArrayList<Map<String, String>>();
        Map<String, String> mapFull = new HashMap<String, String>();
        mapFull.put("n.uri", "https://w3id.org/gaia-x/2511#serviceMVGPortal.json");
        resultListFull.add(mapFull);
        Map<String, String> mapFullES = new HashMap<String, String>();
        mapFullES.put("n.uri", "https://w3id.org/gaia-x/2511#serviceElasticSearch.json");
        resultListFull.add(mapFullES);
        for (RdfClaim claim : claimFile) {
            List<RdfClaim> claimList = new ArrayList<>();
            claimList.add(claim);
            String credentialSubject = claimList.getFirst().getSubjectString();
            graphGaia.addClaims(
                    claimList,
                    credentialSubject.substring(1, credentialSubject.length() - 1));
        }
        GraphQuery queryFull = new GraphQuery(
                "MATCH (n:ServiceOffering) RETURN n.uri LIMIT 25", Map.of());
        List<Map<String, Object>> responseFull = graphGaia.queryData(queryFull).getResults();
        Assertions.assertEquals(resultListFull.size(), responseFull.size());
    }

    /**
     * Given set of credentials connect to graph and upload claims.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form along with literals and upload to graph. Verify if the claim has
     * been uploaded using query service
     */

    @Test
    void testCypherDelta() throws Exception {

        List<RdfClaim> claimFile = loadTestClaims("Claims-Tests/claimsForQuery.nt");
        List<Map<String, String>> resultListDelta = new ArrayList<Map<String, String>>();
        Map<String, String> mapDelta = new HashMap<String, String>();
        mapDelta.put("n.uri", "https://delta-dao.com/.well-known/participant.json");
        resultListDelta.add(mapDelta);
        for (RdfClaim claim : claimFile) {
            List<RdfClaim> claimList = new ArrayList<>();
            claimList.add(claim);
            String credentialSubject = claimList.getFirst().getSubjectString();
            graphGaia.addClaims(claimList, credentialSubject.substring(1, credentialSubject.length() - 1));
        }
        //GraphQuery queryDeltaTest = new GraphQuery("Match(n) RETURN n", null);
        GraphQuery queryDelta = new GraphQuery(
                "MATCH (n:LegalPerson) WHERE n.name = $name RETURN n LIMIT $limit", Map.of("name", "deltaDAO AG", "limit", 25));
        List<Map<String, Object>> responseDelta = graphGaia.queryData(queryDelta).getResults();
        Assertions.assertEquals(1, responseDelta.size());
    }


    /**
     * Given set of credentials connect to graph and upload claims.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form along with literals and upload to graph. Verify if the claim has
     * been uploaded using query service by asking for its signature
     */

    @Test
    void testCypherSignatureQuery() throws Exception {

        String credentialSubject = "https://w3id.org/gaia-x/2511#serviceElasticSearch.json";
        List<RdfClaim> claimList = Arrays.asList(
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<https://w3id.org/gaia-x/2511#name>",
                        "\"Elastic Search DB\""
                ),
                new CredentialClaim(
                        "<http:ex.com/some_service>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<http://ex.com/some_property>",
                        "_:23"
                ),
                new CredentialClaim(
                        "_:23",
                        "<http://ex.com/some_other_property>",
                        "<http:ex.com/some_service>"
                )
        );
        graphGaia.addClaims(claimList, credentialSubject);
        List<Map<String, String>> resultListDelta = new ArrayList<Map<String, String>>();
        Map<String, String> mapDelta = new HashMap<String, String>();
        mapDelta.put("n.uri", "https://w3id.org/gaia-x/2511#serviceElasticSearch.json");
        resultListDelta.add(mapDelta);
        GraphQuery queryDelta = new GraphQuery(
                "MATCH (n:ServiceOffering) WHERE n.name = $name RETURN n.uri LIMIT $limit", Map.of("name", "Elastic Search DB", "limit", 25));
        List<Map<String, Object>> responseDelta = graphGaia.queryData(queryDelta).getResults();
        Assertions.assertEquals(resultListDelta, responseDelta);
        //cleanup
        graphGaia.deleteClaims(credentialSubject);
    }

    /**
     * Given set of credentials connect to graph and upload claims.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form along with literals and upload to graph.
     */
    @Test
    void testAddClaims() throws Exception {
        List<RdfClaim> claimList = new ArrayList<>();
        RdfClaim claim = new CredentialClaim("<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#ServiceOffering>");
        claimList.add(claim);
        RdfClaim claimSecond = new CredentialClaim("<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>", "<https://w3id.org/gaia-x/2511#providedBy>", "<https://delta-dao.com/.well-known/participant.json>");
        claimList.add(claimSecond);
        graphGaia.addClaims(claimList, "https://w3id.org/gaia-x/2511#serviceElasticSearch.json");
        graphGaia.deleteClaims("https://w3id.org/gaia-x/2511#serviceElasticSearch.json");
    }


    /**
     * Given set of credentials connect to graph and upload claims.
     * Instantiate list of claims with subject predicate and object in N-triples
     * form which is invalid and try uploading to graphDB
     */
    @Test
    void testAddClaimsException() throws Exception {
        String credentialSubject = "https://w3id.org/gaia-x/2511#serviceElasticSearch.json";
        //String wrongCredentialSubject = "https://w3id.org/gaia-x/2511#serviceElasticSearch";

        RdfClaim syntacticallyCorrectClaim = new CredentialClaim(
                "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                "<http://w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<https://w3id.org/gaia-x/2511#ServiceOffering>"
        );

        RdfClaim claimWBrokenSubject = new CredentialClaim(
                "<__https://w3id.org/gaia-x/2511#serviceElasticSearch.json__>",
                "<http://w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<https://w3id.org/gaia-x/2511#ServiceOffering>"
        );

        RdfClaim claimWBrokenPredicate = new CredentialClaim(
                "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                "<__http://w3.org/1999/02/22-rdf-syntax-ns#type__>",
                "<https://w3id.org/gaia-x/2511#ServiceOffering>"
        );

        RdfClaim claimWBrokenObjectIRI = new CredentialClaim(
                "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<__https://w3id.org/gaia-x/2511#ServiceOffering__>"
        );

        RdfClaim claimWBrokenLiteral01 = new CredentialClaim(
                "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                "<http://www.w3.org/2000/01/rdf-schema#label>",
                "\"Fourty two\"^^<http://www.w3.org/2001/XMLSchema#int>"
        );

        RdfClaim claimWBrokenLiteral02 = new CredentialClaim(
                "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                "<http://www.w3.org/2000/01/rdf-schema#label>",
                "\"Missing quotes^^<http://www.w3.org/2001/XMLSchema#string>"
        );

        RdfClaim claimWBlankNodeSubject = new CredentialClaim(
                "_:23",
                "<http://ex.com/some_property>",
                "<http://ex.com/resource23>"
        );

        RdfClaim claimWBlankNodeObject = new CredentialClaim(
                "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                "<http://ex.com/some_property>",
                "_:23"
        );

        RdfClaim claimWDIDSubject = new CredentialClaim(
                "<did:example:123456789#v1>",
                "<http://ex.com/some_property>",
                "<http://ex.com/resource23>"
        );

        RdfClaim claimWDIDObject = new CredentialClaim(
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

    @Test
    void deleteValidationResultClaims_removesTargetNode_preservesUnrelatedNode() {
        String resultIri1 = "https://example.org/result/del-1";
        String resultIri2 = "https://example.org/result/del-2";

        // Seed two nodes — n10s sets n.uri from the subject URI of imported N-Triples
        graphGaia.addClaims(List.of(
            new CredentialClaim(
                "<" + resultIri1 + ">",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/ValidationResult>"
            )
        ), resultIri1);
        graphGaia.addClaims(List.of(
            new CredentialClaim(
                "<" + resultIri2 + ">",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/ValidationResult>"
            )
        ), resultIri2);

        // Precondition: both nodes must exist
        GraphQuery findResult1Before = new GraphQuery(
            "MATCH (n {uri: $uri}) RETURN n.uri AS found",
            Map.of("uri", resultIri1), QueryLanguage.OPENCYPHER, GraphQuery.QUERY_TIMEOUT, false);
        GraphQuery findResult2Before = new GraphQuery(
            "MATCH (n {uri: $uri}) RETURN n.uri AS found",
            Map.of("uri", resultIri2), QueryLanguage.OPENCYPHER, GraphQuery.QUERY_TIMEOUT, false);
        Assertions.assertEquals(1, graphGaia.queryData(findResult1Before).getResults().size(),
            "Precondition: result1 node must exist");
        Assertions.assertEquals(1, graphGaia.queryData(findResult2Before).getResults().size(),
            "Precondition: result2 node must exist");

        graphGaia.deleteValidationResultClaims(resultIri1);

        // result1 node must be gone
        GraphQuery findResult1After = new GraphQuery(
            "MATCH (n {uri: $uri}) RETURN n.uri AS found",
            Map.of("uri", resultIri1), QueryLanguage.OPENCYPHER, GraphQuery.QUERY_TIMEOUT, false);
        Assertions.assertTrue(graphGaia.queryData(findResult1After).getResults().isEmpty(),
            "result1 node should be deleted");

        // result2 node must still exist
        GraphQuery findResult2After = new GraphQuery(
            "MATCH (n {uri: $uri}) RETURN n.uri AS found",
            Map.of("uri", resultIri2), QueryLanguage.OPENCYPHER, GraphQuery.QUERY_TIMEOUT, false);
        Assertions.assertEquals(1, graphGaia.queryData(findResult2After).getResults().size(),
            "result2 node should not be affected");

        // cleanup
        graphGaia.deleteValidationResultClaims(resultIri2);
    }

    private List<RdfClaim> loadTestClaims(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream()) {
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String strLine;
            List<RdfClaim> claimList = new ArrayList<>();
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
                RdfClaim claim = new CredentialClaim(subject, predicate, object);
                claimList.add(claim);
            }
            return claimList;
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
        String credentialSubject1 = "https://w3id.org/gaia-x/2511#serviceElasticSearch.json";
        List<RdfClaim> claimList = Arrays.asList(
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<http://ex.com/some_property>",
                        "_:23"
                ),
                new CredentialClaim(
                        "_:23",
                        "<http://ex.com/some_other_property>",
                        "<http:ex.com/some_service>"
                ),
                new CredentialClaim(
                        "<http:ex.com/some_service>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                )
        );

        String credentialSubject2 = "http://ex.com/credentialSubject2";
        List<RdfClaim> claimsWOtherCredSubject = Arrays.asList(
                new CredentialClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://ex.com/some_property>",
                        "<http://ex.com/resource23>"
                )
        );

        try {
        graphGaia.addClaims(claimList, credentialSubject1);
        graphGaia.addClaims(claimsWOtherCredSubject, credentialSubject2);

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
        String credentialSubject1 = "https://w3id.org/gaia-x/2511#serviceElasticSearch.json";
        List<RdfClaim> claimList = Arrays.asList(
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<http://ex.com/some_property>",
                        "_:23"
                ),
                new CredentialClaim(
                        "_:23",
                        "<http://ex.com/some_other_property>",
                        "<http:ex.com/some_service>"
                ),
                new CredentialClaim(
                        "<http:ex.com/some_service>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                )
        );

        String credentialSubject2 = "http://ex.com/credentialSubject2";
        List<RdfClaim> claimsWOtherCredSubject = Arrays.asList(
                new CredentialClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://ex.com/some_property>",
                        "<http://ex.com/resource23>"
                )
        );

        graphGaia.addClaims(claimList, credentialSubject1);
        graphGaia.addClaims(claimsWOtherCredSubject, credentialSubject2);
        GraphQuery queryCypher = new GraphQuery("MATCH (n)-[:some_property]->(m) RETURN n", null);
        List<Map<String, Object>> responseCypher = graphGaia.queryData(queryCypher).getResults();
        List<Map<String, Object>> resultListSomeProperty = List.of(Map.of("n", Map.of("claimsGraphUri", List.of("https://w3id.org/gaia-x/2511#serviceElasticSearch.json"))), Map.of("n", Map.of("claimsGraphUri", List.of("http://ex.com/credentialSubject2"))));
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
        List<RdfClaim> claimList = Arrays.asList(
                new CredentialClaim(
                        "<http://example.org/test-issuer2>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#Provider>"
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer2>",
                        "<https://w3id.org/gaia-x/2511#legalAddress>",
                        "_:b1"
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer2>",
                        "<https://w3id.org/gaia-x/2511#legalName>",
                        "\"deltaDAO AGE\""
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer2>",
                        "<https://w3id.org/gaia-x/2511#name>",
                        "\"deltaDAO AGE\""
                ),
                new CredentialClaim("_:b1",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#Address>"
                ),
                new CredentialClaim("_:b1",
                        "<https://w3id.org/gaia-x/2511#country>",
                        "\"DE\""
                ),
                new CredentialClaim("_:b1",
                        "<https://w3id.org/gaia-x/2511#locality>",
                        "\"Dresden\""
                ),
                new CredentialClaim("_:b1",
                        "<https://w3id.org/gaia-x/2511#postal-code>",
                        "\"01067\""
                ),
                new CredentialClaim("_:b1",
                        "<https://w3id.org/gaia-x/2511#street-address>",
                        "\"Tried str 46b\""
                )
        );

        graphGaia.addClaims(claimList, credentialSubject);
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
        String credentialSubject1 = "https://w3id.org/gaia-x/2511#serviceElasticSearch.json";
        List<RdfClaim> claimList = Arrays.asList(
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<https://w3id.org/gaia-x/2511#serviceElasticSearch.json>",
                        "<http://ex.com/some_property>",
                        "_:23"
                ),
                new CredentialClaim(
                        "_:23",
                        "<http://ex.com/some_other_property>",
                        "<http:ex.com/some_service>"
                ),
                new CredentialClaim(
                        "<http:ex.com/some_service>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                )
        );

        String credentialSubject2 = "http://ex.com/credentialSubject2";
        List<RdfClaim> claimsWOtherCredSubject = Arrays.asList(
                new CredentialClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://ex.com/some_property>",
                        "<http://ex.com/resource23>"
                ),
                new CredentialClaim(
                        "<http://ex.com/credentialSubject2>",
                        "<http://ex.com/some_other_property>",
                        "<http://ex.com/resource24>"
                ),
                new CredentialClaim(
                        "<http://ex.com/resource24>",
                        "<http://www.w3.org/2000/01/rdf-schema#label>",
                        "\"resource24\""
                )
        );

        graphGaia.addClaims(claimList, credentialSubject1);
        graphGaia.addClaims(claimsWOtherCredSubject, credentialSubject2);
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
        List<RdfClaim> claimList = Arrays.asList(
                new CredentialClaim(
                        "<http://example.org/test-issuer>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#Provider>"
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer>",
                        "<https://w3id.org/gaia-x/2511#legalAddress>",
                        "_:23"
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer>",
                        "<https://w3id.org/gaia-x/2511#legalName>",
                        "\"deltaDAO AG\""
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer>",
                        "<https://w3id.org/gaia-x/2511#name>",
                        "\"deltaDAO AG\""
                ),
                new CredentialClaim("_:23",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#Address>"
                ),
                new CredentialClaim("_:23",
                        "<https://w3id.org/gaia-x/2511#country>",
                        "\"DE\""
                ),
                new CredentialClaim("_:23",
                        "<https://w3id.org/gaia-x/2511#locality>",
                        "\"Hamburg\""
                ),
                new CredentialClaim("_:23",
                        "<https://w3id.org/gaia-x/2511#postal-code>",
                        "\"22303\""
                ),
                new CredentialClaim("_:23",
                        "<https://w3id.org/gaia-x/2511#street-address>",
                        "\"GeibelstraГџe 46b\""
                )
        );

        String credentialSubject2 = "http://example.org/test-issuer2";
        List<RdfClaim> claimList2 = Arrays.asList(
                new CredentialClaim(
                        "<http://example.org/test-issuer2>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#Provider>"
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer2>",
                        "<https://w3id.org/gaia-x/2511#legalAddress>",
                        "_:b1"
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer2>",
                        "<https://w3id.org/gaia-x/2511#legalName>",
                        "\"deltaDAO AGE\""
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer2>",
                        "<https://w3id.org/gaia-x/2511#name>",
                        "\"deltaDAO AGE\""
                ),
                new CredentialClaim("_:b1",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#Address>"
                ),
                new CredentialClaim("_:b1",
                        "<https://w3id.org/gaia-x/2511#country>",
                        "\"DE\""
                ),
                new CredentialClaim("_:b1",
                        "<https://w3id.org/gaia-x/2511#locality>",
                        "\"Dresden\""
                ),
                new CredentialClaim("_:b1",
                        "<https://w3id.org/gaia-x/2511#postal-code>",
                        "\"01067\""
                ),
                new CredentialClaim("_:b1",
                        "<https://w3id.org/gaia-x/2511#street-address>",
                        "\"Tried str 46b\""
                )
        );

        String credentialSubject3 = "http://example.org/test-issuer3";
        List<RdfClaim> claimList3 = Arrays.asList(
                new CredentialClaim(
                        "<http://example.org/test-issuer3>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#Provider>"
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer3>",
                        "<https://w3id.org/gaia-x/2511#legalAddress>",
                        "_:b2"
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer3>",
                        "<https://w3id.org/gaia-x/2511#legalName>",
                        "\"deltaDAO AGEF\""
                ),
                new CredentialClaim(
                        "<http://example.org/test-issuer3>",
                        "<https://w3id.org/gaia-x/2511#name>",
                        "\"deltaDAO AGEF\""
                ),
                new CredentialClaim("_:b2",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#Address>"
                ),
                new CredentialClaim("_:b2",
                        "<https://w3id.org/gaia-x/2511#country>",
                        "\"DE\""
                ),
                new CredentialClaim("_:b2",
                        "<https://w3id.org/gaia-x/2511#locality>",
                        "\"Dresden\""
                ),
                new CredentialClaim("_:b2",
                        "<https://w3id.org/gaia-x/2511#postal-code>",
                        "\"01069\""
                ),
                new CredentialClaim("_:b2",
                        "<https://w3id.org/gaia-x/2511#street-address>",
                        "\"Fried str 46b\""
                )
        );

        graphGaia.addClaims(claimList, credentialSubject1);
        graphGaia.addClaims(claimList2, credentialSubject2);
        graphGaia.addClaims(claimList3, credentialSubject3);

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
        List<RdfClaim> claimList = Arrays.asList(new CredentialClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#SoftwareOffering>"),
                new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#accessType>", "\"access type\""),
                new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#formatType>", "\"format type\""),
                new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#requestType>", "\"request type\""),
                new CredentialClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#TermsAndConditions>"),
                new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#content>", "\"http://example.org/tac\""),
                new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#hash>", "\"1234\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#ServiceOffering>"),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_1\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_2\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_3\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_4\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_5\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_6\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_7\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<https://w3id.org/gaia-x/2511#mySoftwareOffering>", "_:b0"),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<https://w3id.org/gaia-x/2511#providedBy>", "<gax-participant:Provider1>"),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<https://w3id.org/gaia-x/2511#serviceTitle>", "\"Software Title\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering>", "<https://w3id.org/gaia-x/2511#termsAndConditions>", "_:b1"));
        graphGaia.addClaims(claimList, credentialSubject);
        GraphQuery queryCypher = new GraphQuery("match(m:ServiceOffering) return m", null);
        List<Map<String, Object>> responseCypher = graphGaia.queryData(queryCypher).getResults();
        Assertions.assertEquals(2, responseCypher.size());
        String credentialSubject2 = "https://www.example.org/mySoftwareOffering2";
        List<RdfClaim> claimList2 = Arrays.asList(new CredentialClaim("_:b0", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#SoftwareOffering2>"),
                new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#accessType>", "\"access type\""),
                new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#formatType>", "\"format type\""),
                new CredentialClaim("_:b0", "<https://w3id.org/gaia-x/2511#requestType>", "\"request type\""),
                new CredentialClaim("_:b1", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#TermsAndConditions>"),
                new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#content>", "\"http://example.org/tac\""),
                new CredentialClaim("_:b1", "<https://w3id.org/gaia-x/2511#hash>", "\"12345\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>", "<https://w3id.org/gaia-x/2511#ServiceOffering2>"),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword2>", "\"Keyword1_1\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword2>", "\"Keyword1_2\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword2>", "\"Keyword1_3\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_4\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_5\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_6\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<http://www.w3.org/ns/dcat#keyword>", "\"Keyword1_7\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<https://w3id.org/gaia-x/2511#mySoftwareOffering>", "_:b0"),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<https://w3id.org/gaia-x/2511#providedBy>", "<gax-participant:Provider1>"),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<https://w3id.org/gaia-x/2511#serviceTitle>", "\"Software Title 2\""),
                new CredentialClaim("<https://www.example.org/mySoftwareOffering2>", "<https://w3id.org/gaia-x/2511#termsAndConditions>", "_:b1"));

        graphGaia.addClaims(claimList2, credentialSubject2);
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
    void isHealthy_embeddedNeo4j_returnsTrue() {
        Assertions.assertTrue(graphGaia.isHealthy(),
            "isHealthy() should return true for embedded Neo4j");
    }

    @Test
    void getClaimCount_emptyGraph_returnsZeroOrPositive() {
        // On a fresh graph, claim count should be 0 or greater (other tests may have added data)
        long count = graphGaia.getClaimCount();
        Assertions.assertTrue(count >= 0,
            "getClaimCount() should return 0 or positive on embedded Neo4j");
    }

    @Test
    void getClaimCount_afterAddClaims_returnsPositiveValue() {
        String credentialSubject = "http://example.org/healthCheckSubject";
        List<RdfClaim> claimList = List.of(
                new CredentialClaim(
                        "<http://example.org/healthCheckSubject>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                )
        );
        graphGaia.addClaims(claimList, credentialSubject);

        long count = graphGaia.getClaimCount();
        Assertions.assertTrue(count > 0,
            "getClaimCount() should return positive value after addClaims");

        // cleanup
        graphGaia.deleteClaims(credentialSubject);
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

    @Test
    void addClaims_withKnownCredentialSubject_setsClaimsGraphUriToCredentialSubject() {
        String credentialSubject = "http://example.org/credSubject1";
        List<RdfClaim> claimList = List.of(
                new CredentialClaim(
                        "<http://example.org/node1>",
                        "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                        "<https://w3id.org/gaia-x/2511#ServiceOffering>"
                ),
                new CredentialClaim(
                        "<http://example.org/node1>",
                        "<http://example.org/name>",
                        "\"Test Service\""
                )
        );

        try {
            graphGaia.addClaims(claimList, credentialSubject);

            // Query nodes whose claimsGraphUri array contains the credential subject
            GraphQuery query = new GraphQuery(
                    "MATCH (n) WHERE $uri IN n.claimsGraphUri RETURN n.claimsGraphUri AS uris",
                    Map.of("uri", credentialSubject));
            List<Map<String, Object>> results = graphGaia.queryData(query).getResults();

            Assertions.assertFalse(results.isEmpty(),
                    "At least one node should have the credential subject in claimsGraphUri");
            for (Map<String, Object> row : results) {
                Object uris = row.get("uris");
                Assertions.assertNotNull(uris, "claimsGraphUri should not be null");
                Assertions.assertInstanceOf(List.class, uris, "claimsGraphUri should be a list");
                List<String> uriList = (List<String>) uris;
                Assertions.assertTrue(uriList.contains(credentialSubject),
                        "claimsGraphUri array should contain the credential subject used in addClaims");
            }
        } finally {
            graphGaia.deleteClaims(credentialSubject);
        }
    }

    @Test
    void deleteClaims_multipleCredentialsShareNode_deleteOneLeavesOther() {
        String credC = "http://example.org/credC";
        String credD = "http://example.org/credD";

        // Both credentials share the same subject node
        RdfClaim sharedClaim = new CredentialClaim(
                "<http://example.org/sharedNode>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<https://w3id.org/gaia-x/2511#ServiceOffering>"
        );

        try {
            graphGaia.addClaims(List.of(sharedClaim), credC);
            graphGaia.addClaims(List.of(sharedClaim), credD);

            // Query the shared node by its URI and check claimsGraphUri contains both
            GraphQuery queryBoth = new GraphQuery(
                    "MATCH (n {uri: $uri}) RETURN n.claimsGraphUri AS uris",
                    Map.of("uri", "http://example.org/sharedNode"));
            List<Map<String, Object>> resultsBoth = graphGaia.queryData(queryBoth).getResults();

            Assertions.assertEquals(1, resultsBoth.size(),
                    "There should be exactly one node for the shared URI");
            List<String> urisBoth = (List<String>) resultsBoth.getFirst().get("uris");
            Assertions.assertTrue(urisBoth.contains(credC),
                    "claimsGraphUri should contain credC");
            Assertions.assertTrue(urisBoth.contains(credD),
                    "claimsGraphUri should contain credD");

            // Delete credC -- this exercises queryUpdate (removes URI from shared array)
            graphGaia.deleteClaims(credC);

            // Re-query: claimsGraphUri should contain only credD
            List<Map<String, Object>> resultsAfter = graphGaia.queryData(queryBoth).getResults();
            Assertions.assertEquals(1, resultsAfter.size(),
                    "Shared node should still exist after deleting one credential");
            List<String> urisAfter = (List<String>) resultsAfter.getFirst().get("uris");
            Assertions.assertFalse(urisAfter.contains(credC),
                    "credC should no longer be in claimsGraphUri after deletion");
            Assertions.assertTrue(urisAfter.contains(credD),
                    "credD should still be in claimsGraphUri after deleting credC");
        } finally {
            graphGaia.deleteClaims(credC);
            graphGaia.deleteClaims(credD);
        }
    }
}
