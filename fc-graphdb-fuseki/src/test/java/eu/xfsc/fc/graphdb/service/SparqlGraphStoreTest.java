package eu.xfsc.fc.graphdb.service;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.QueryException;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.graphdb.config.EmbeddedFusekiConfig;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.system.Txn;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(classes = {SparqlGraphStore.class})
@Import(EmbeddedFusekiConfig.class)
public class SparqlGraphStoreTest {

    private static final String RDF_TYPE = "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>";
    private static final String CRED_SUBJECT_URI = "https://www.w3.org/2018/credentials#credentialSubject";

    @Autowired
    private GraphStore graphStore;

    @Autowired
    private RDFConnection rdfConnection;

    @BeforeEach
    void clearDataset() {
        Txn.executeWrite(rdfConnection, () -> rdfConnection.update("CLEAR ALL"));
    }

    @Test
    void graphStoreBeanIsSparqlImplementation() {
        assertInstanceOf(SparqlGraphStore.class, graphStore);
    }

    @Test
    void addClaims_queriedWithSparqlStar_returnsUploadedTripleData() {
        List<SdClaim> claims = List.of(
            typeClaim("http://example.org/subject1", "http://example.org/ServiceOffering"),
            literalClaim("http://example.org/subject1", "http://example.org/name", "Test Service")
        );
        graphStore.addClaims(claims, "http://example.org/credential1");

        List<Map<String, Object>> rows = queryAllClaimsByCredentialSubject().getResults();

        assertEquals(2, rows.size(), "Should return 2 results for 2 claims");
        boolean foundType = rows.stream().anyMatch(r ->
            "http://example.org/subject1".equals(r.get("s")) &&
            "http://www.w3.org/1999/02/22-rdf-syntax-ns#type".equals(r.get("p")) &&
            "http://example.org/ServiceOffering".equals(r.get("o")));
        boolean foundName = rows.stream().anyMatch(r ->
            "http://example.org/subject1".equals(r.get("s")) &&
            "http://example.org/name".equals(r.get("p")) &&
            "Test Service".equals(r.get("o")));
        assertTrue(foundType, "Should contain the rdf:type triple with ServiceOffering URI");
        assertTrue(foundName, "Should contain the name triple with literal value 'Test Service'");
    }

    @Test
    void addClaims_wrapsEachClaimWithCredentialSubjectMetaProperty() {
        List<SdClaim> claims = List.of(
            typeClaim("http://example.org/subject2", "http://example.org/Resource"),
            literalClaim("http://example.org/subject2", "http://example.org/label", "My Resource")
        );
        String credentialSubject = "http://example.org/credential2";
        graphStore.addClaims(claims, credentialSubject);

        List<Map<String, Object>> rows = querySparql(
            "SELECT ?s ?p ?o ?mp ?mo WHERE { <<?s ?p ?o>> ?mp ?mo }").getResults();

        assertEquals(2, rows.size(), "Should have 2 RDF-star wrapped statements");
        for (Map<String, Object> row : rows) {
            assertEquals(CRED_SUBJECT_URI, row.get("mp"),
                "Meta-property should be credentialSubject URI");
            assertEquals(credentialSubject, row.get("mo"),
                "Meta-object should match the credential subject passed to addClaims");
            assertEquals("http://example.org/subject2", row.get("s"),
                "Inner triple subject should be the uploaded subject URI");
        }
    }

    @Test
    void deleteClaims_removesOnlyTargetCredentialSubject_leavesOthersIntact() {
        String credSubA = "http://example.org/credentialA";
        String credSubB = "http://example.org/credentialB";
        graphStore.addClaims(List.of(
            typeClaim("http://example.org/subjectA", "http://example.org/TypeA"),
            literalClaim("http://example.org/subjectA", "http://example.org/name", "Subject A")
        ), credSubA);
        graphStore.addClaims(List.of(
            typeClaim("http://example.org/subjectB", "http://example.org/TypeB"),
            literalClaim("http://example.org/subjectB", "http://example.org/name", "Subject B")
        ), credSubB);

        graphStore.deleteClaims(credSubA);

        assertTrue(queryBySpecificCredentialSubject(credSubA).getResults().isEmpty(),
            "Deleted credential subject should have 0 results");

        List<Map<String, Object>> rows = queryBySpecificCredentialSubject(credSubB).getResults();
        assertEquals(2, rows.size(), "Surviving subject should have 2 claims");
        boolean foundTypeB = rows.stream().anyMatch(r ->
            "http://example.org/subjectB".equals(r.get("s")) &&
            "http://example.org/TypeB".equals(r.get("o")));
        boolean foundNameB = rows.stream().anyMatch(r ->
            "http://example.org/subjectB".equals(r.get("s")) &&
            "Subject B".equals(r.get("o")));
        assertTrue(foundTypeB, "Surviving results should contain Subject B's type triple");
        assertTrue(foundNameB, "Surviving results should contain Subject B's name triple");
    }

    @Test
    void queryData_withOrderByClause_returnsResultsInSortedOrder() {
        graphStore.addClaims(List.of(
            literalClaim("http://example.org/item1", "http://example.org/name", "Charlie"),
            literalClaim("http://example.org/item2", "http://example.org/name", "Alice"),
            literalClaim("http://example.org/item3", "http://example.org/name", "Bob")
        ), "http://example.org/credentialOrder");

        List<Map<String, Object>> rows = querySparql(
            "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> <" + CRED_SUBJECT_URI + "> ?cs } ORDER BY ?o"
        ).getResults();

        assertEquals(3, rows.size(), "Should return 3 results");
        assertEquals("Alice", rows.get(0).get("o"));
        assertEquals("Bob", rows.get(1).get("o"));
        assertEquals("Charlie", rows.get(2).get("o"));
    }

    @Test
    void addClaims_withValidClaim_persistsInStore() {
        graphStore.addClaims(
            List.of(typeClaim("http://example.org/subject", "http://example.org/Type")),
            "http://example.org/credentialValidation");

        assertEquals(1, queryAllClaimsByCredentialSubject().getResults().size(),
            "Valid claim should be persisted in the store");
    }

    @ParameterizedTest
    @MethodSource("malformedUriClaims")
    void addClaims_withMalformedUri_throwsQueryExceptionIdentifyingBrokenPart(
            SdClaim brokenClaim, String expectedMessageFragment) {
        Exception exception = assertThrows(QueryException.class,
            () -> graphStore.addClaims(List.of(brokenClaim), "http://example.org/credential"));

        assertTrue(exception.getMessage().contains(expectedMessageFragment),
            "Error message should contain '" + expectedMessageFragment + "'");
    }

    static Stream<Arguments> malformedUriClaims() {
        return Stream.of(
            Arguments.of(
                new SdClaim("<__http://example.org/broken__>", RDF_TYPE,
                    "<http://example.org/Type>"),
                "Subject in triple"),
            Arguments.of(
                new SdClaim("<http://example.org/subject>", "<__http://example.org/broken__>",
                    "<http://example.org/Type>"),
                "Predicate in triple"),
            Arguments.of(
                new SdClaim("<http://example.org/subject>", RDF_TYPE,
                    "<__http://example.org/broken__>"),
                "Object in triple")
        );
    }

    @ParameterizedTest
    @EnumSource(value = QueryLanguage.class, names = {"OPENCYPHER", "GRAPHQL"})
    void queryData_withUnsupportedLanguage_throwsUnsupportedOperationException(QueryLanguage language) {
        GraphQuery query = new GraphQuery("SELECT * WHERE { ?s ?p ?o }", Map.of(),
            language, GraphQuery.QUERY_TIMEOUT, false);

        UnsupportedOperationException exception = assertThrows(
            UnsupportedOperationException.class,
            () -> graphStore.queryData(query));

        assertTrue(exception.getMessage().contains(language.name()),
            "Exception message should contain the rejected language name: " + language.name());
    }

    @Test
    void addClaims_withEmptyList_storesNothing() {
        String credentialSubject = "http://example.org/emptySubject";

        assertDoesNotThrow(
            () -> graphStore.addClaims(List.of(), credentialSubject));

        assertTrue(queryBySpecificCredentialSubject(credentialSubject).getResults().isEmpty(),
            "No results should be stored for empty claim list");
    }

    // --- Helpers ---

    private PaginatedResults<Map<String, Object>> querySparql(String sparql) {
        return graphStore.queryData(new GraphQuery(
            sparql, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false));
    }

    private PaginatedResults<Map<String, Object>> queryAllClaimsByCredentialSubject() {
        return querySparql(
            "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> <" + CRED_SUBJECT_URI + "> ?cs }");
    }

    private PaginatedResults<Map<String, Object>> queryBySpecificCredentialSubject(String credentialSubject) {
        return querySparql(
            "SELECT ?s ?p ?o WHERE { <<?s ?p ?o>> <" + CRED_SUBJECT_URI + "> <" + credentialSubject + "> }");
    }

    private static SdClaim typeClaim(String subject, String type) {
        return new SdClaim("<" + subject + ">", RDF_TYPE, "<" + type + ">");
    }

    private static SdClaim literalClaim(String subject, String predicate, String value) {
        return new SdClaim("<" + subject + ">", "<" + predicate + ">", "\"" + value + "\"");
    }
}