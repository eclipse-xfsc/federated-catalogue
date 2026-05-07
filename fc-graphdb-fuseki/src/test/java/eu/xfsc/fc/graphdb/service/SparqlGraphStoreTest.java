package eu.xfsc.fc.graphdb.service;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.QueryException;
import eu.xfsc.fc.core.pojo.CredentialClaim;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.RdfClaim;
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
        List<RdfClaim> claims = List.of(
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
        List<RdfClaim> claims = List.of(
            typeClaim("http://example.org/subject2", "http://example.org/Resource"),
            literalClaim("http://example.org/subject2", "http://example.org/label", "My Resource")
        );
        String credentialSubject = "http://example.org/credential2";
        graphStore.addClaims(claims, credentialSubject);

        List<Map<String, Object>> rows = querySparql(
            "SELECT ?s ?p ?o ?mp ?mo WHERE { <<(?s ?p ?o)>> ?mp ?mo }").getResults();

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
            "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> <" + CRED_SUBJECT_URI + "> ?cs } ORDER BY ?o"
        ).getResults();

        assertEquals(3, rows.size(), "Should return 3 results");
        assertEquals("Alice", rows.getFirst().get("o"));
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
            RdfClaim brokenClaim, String expectedMessageFragment) {
        Exception exception = assertThrows(QueryException.class,
            () -> graphStore.addClaims(List.of(brokenClaim), "http://example.org/credential"));

        assertTrue(exception.getMessage().contains(expectedMessageFragment),
            "Error message should contain '" + expectedMessageFragment + "'");
    }

    static Stream<Arguments> malformedUriClaims() {
        return Stream.of(
            Arguments.of(
                new CredentialClaim("<__http://example.org/broken__>", RDF_TYPE,
                    "<http://example.org/Type>"),
                "Subject in triple"),
            Arguments.of(
                new CredentialClaim("<http://example.org/subject>", "<__http://example.org/broken__>",
                    "<http://example.org/Type>"),
                "Predicate in triple"),
            Arguments.of(
                new CredentialClaim("<http://example.org/subject>", RDF_TYPE,
                    "<__http://example.org/broken__>"),
                "Object in triple")
        );
    }

    @ParameterizedTest
    @EnumSource(value = QueryLanguage.class, names = {"OPENCYPHER"})
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
    void isHealthy_embeddedFuseki_returnsTrue() {
        assertTrue(graphStore.isHealthy(),
            "isHealthy() should return true for embedded Fuseki");
    }

    @Test
    void getClaimCount_emptyDataset_returnsZero() {
        long count = graphStore.getClaimCount();
        assertEquals(0, count,
            "getClaimCount() should return 0 on empty dataset");
    }

    @Test
    void getClaimCount_afterAddClaims_returnsCorrectCount() {
        List<RdfClaim> claims = List.of(
            new CredentialClaim(
                "<http://example.org/healthSubject>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/ServiceOffering>"
            ),
            new CredentialClaim(
                "<http://example.org/healthSubject>",
                "<http://example.org/name>",
                "\"Health Check Service\""
            )
        );
        String credentialSubject = "http://example.org/healthCredential";
        graphStore.addClaims(claims, credentialSubject);

        long count = graphStore.getClaimCount();
        assertEquals(2, count,
            "getClaimCount() should return 2 for 2 claim triples");
    }

    @Test
    void getRDFAssetCountInGraph_emptyDataset_returnsZero() {
        long count = graphStore.getRDFAssetCountInGraph();
        assertEquals(0, count,
            "getRDFAssetCountInGraph() should return 0 on empty dataset");
    }

    @Test
    void getRDFAssetCountInGraph_afterAddClaims_countsDistinctCredentialSubjects() {
        graphStore.addClaims(List.of(
            new CredentialClaim(
                "<http://example.org/subject1>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/ServiceOffering>"
            ),
            new CredentialClaim(
                "<http://example.org/subject1>",
                "<http://example.org/name>",
                "\"Service One\""
            )
        ), "http://example.org/credential1");

        graphStore.addClaims(List.of(
            new CredentialClaim(
                "<http://example.org/subject2>",
                "<http://www.w3.org/1999/02/22-rdf-syntax-ns#type>",
                "<http://example.org/Resource>"
            )
        ), "http://example.org/credential2");

        assertEquals(3, graphStore.getClaimCount(),
            "getClaimCount() should return 3 claim triples total");
        assertEquals(2, graphStore.getRDFAssetCountInGraph(),
            "getRDFAssetCountInGraph() should return 2 distinct credential subjects");
    }

    @Test
    void addClaims_withEmptyList_storesNothing() {
        String credentialSubject = "http://example.org/emptySubject";

        assertDoesNotThrow(
            () -> graphStore.addClaims(List.of(), credentialSubject));

        assertTrue(queryBySpecificCredentialSubject(credentialSubject).getResults().isEmpty(),
            "No results should be stored for empty claim list");
    }

    @Test
    void addClaims_withMultipleTriples_wrapsEachIndividually() {
        String credentialSubject = "http://example.org/credentialMulti";
        List<RdfClaim> claims = List.of(
            typeClaim("http://example.org/multiSubject", "http://example.org/TypeA"),
            typeClaim("http://example.org/multiSubject", "http://example.org/TypeB"),
            literalClaim("http://example.org/multiSubject", "http://example.org/label", "Multi")
        );
        graphStore.addClaims(claims, credentialSubject);

        List<Map<String, Object>> rows = querySparql(
            "SELECT ?s ?p ?o ?mo WHERE { <<(?s ?p ?o)>> <" + CRED_SUBJECT_URI + "> ?mo } ORDER BY ?p"
        ).getResults();

        assertEquals(3, rows.size(), "Should return 3 rows for 3 individually wrapped claims");
        for (Map<String, Object> row : rows) {
            assertEquals(credentialSubject, row.get("mo"),
                "Each wrapped statement's meta-object should be the credential subject");
        }
    }

    @Test
    void addClaims_sameTripleFromTwoCredentials_createsSeparateWrappedStatements() {
        String credA = "http://example.org/credentialSharedA";
        String credB = "http://example.org/credentialSharedB";
        RdfClaim sharedClaim = typeClaim("http://example.org/shared", "http://example.org/SharedType");

        graphStore.addClaims(List.of(sharedClaim), credA);
        graphStore.addClaims(List.of(sharedClaim), credB);

        String sharedTripleQuery = "SELECT ?mo WHERE { <<(" +
            "<http://example.org/shared> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/SharedType>" +
            ")>> <" + CRED_SUBJECT_URI + "> ?mo } ORDER BY ?mo";

        List<Map<String, Object>> rows = querySparql(sharedTripleQuery).getResults();

        assertEquals(2, rows.size(), "Same triple from two credentials should create 2 wrapped statements");
        assertEquals(credA, rows.getFirst().get("mo"));
        assertEquals(credB, rows.get(1).get("mo"));

        // Delete one credential, the other should survive
        graphStore.deleteClaims(credA);

        List<Map<String, Object>> afterDelete = querySparql(sharedTripleQuery).getResults();

        assertEquals(1, afterDelete.size(), "After deleting credA, only credB's wrapping should remain");
        assertEquals(credB, afterDelete.getFirst().get("mo"));
    }

    @Test
    void deleteClaims_removesAllWrappedStatements_zeroResultsForCredential() {
        String credentialSubject = "http://example.org/credentialToDelete";
        List<RdfClaim> claims = List.of(
            typeClaim("http://example.org/delSubject", "http://example.org/DelType"),
            literalClaim("http://example.org/delSubject", "http://example.org/name", "ToDelete"),
            literalClaim("http://example.org/delSubject", "http://example.org/desc", "Will be removed")
        );
        graphStore.addClaims(claims, credentialSubject);

        assertEquals(3, queryBySpecificCredentialSubject(credentialSubject).getResults().size(),
            "Precondition: 3 claims should be stored before deletion");

        graphStore.deleteClaims(credentialSubject);

        assertTrue(queryBySpecificCredentialSubject(credentialSubject).getResults().isEmpty(),
            "After deletion, zero results should remain for the credential subject");
    }

    @Test
    void queryData_regularSparqlWithoutStarSyntax_returnsWrappedStatements() {
        String credentialSubject = "http://example.org/credentialRegular";
        graphStore.addClaims(List.of(
            typeClaim("http://example.org/regSubject", "http://example.org/RegType"),
            literalClaim("http://example.org/regSubject", "http://example.org/label", "Regular")
        ), credentialSubject);

        // Regular SPARQL (no <<>> star syntax) matches the RDF-star reified statements.
        // In Jena's RDF-star model, the only triples in the default graph are of the form:
        //   <<inner-triple>> cred:credentialSubject <credentialSubject>
        // So ?s binds to a triple-term, ?p to the wrapping predicate, ?o to the credential subject.
        List<Map<String, Object>> rows = querySparql(
            "SELECT ?s ?p ?o WHERE { ?s ?p ?o } ORDER BY ?s ?p"
        ).getResults();

        assertEquals(2, rows.size(),
            "Regular SPARQL should return 2 rows (one per wrapped claim)");

        // Each row's predicate is the credentialSubject wrapping predicate
        for (Map<String, Object> row : rows) {
            assertEquals(CRED_SUBJECT_URI, row.get("p"),
                "Each result's predicate should be the RDF-star wrapping predicate");
            assertEquals(credentialSubject, row.get("o"),
                "Each result's object should be the credential subject");
        }

        // The triple subjects are triple-term string representations containing the original triples.
        // This confirms the data is accessible (non-empty) and the wrapping structure is consistent.
        for (Map<String, Object> row : rows) {
            String subject = String.valueOf(row.get("s"));
            assertTrue(subject.contains("http://example.org/regSubject"),
                "Triple-term subject should contain the original inner triple's subject URI");
        }
    }

    @Test
    void deleteValidationResultClaims_removesMatchingTriples_preservesUnrelated() {
        String assetId1 = "http://example.org/asset/1";
        String assetId2 = "http://example.org/asset/2";
        String resultIri1 = "http://example.org/result/1";
        String resultIri2 = "http://example.org/result/2";
        String conformsPredicate = "http://example.org/conforms";

        // Seed result1: property triple + link triple (assetId1 -> resultIri1)
        graphStore.addClaims(List.of(
            typeClaim(resultIri1, "http://example.org/ValidationResult"),
            literalClaim(resultIri1, conformsPredicate, "true")
        ), assetId1);
        graphStore.addClaims(List.of(
            new CredentialClaim("<" + assetId1 + ">", "<http://example.org/hasValidationResult>", "<" + resultIri1 + ">")
        ), assetId1);

        // Seed result2: same structure but different IRI — must survive the deletion
        graphStore.addClaims(List.of(
            typeClaim(resultIri2, "http://example.org/ValidationResult"),
            literalClaim(resultIri2, conformsPredicate, "true")
        ), assetId2);
        graphStore.addClaims(List.of(
            new CredentialClaim("<" + assetId2 + ">", "<http://example.org/hasValidationResult>", "<" + resultIri2 + ">")
        ), assetId2);

        long countBefore = queryAllClaimsByCredentialSubject().getResults().size();
        assertTrue(countBefore > 0, "Precondition: claims must be present before deletion");

        graphStore.deleteValidationResultClaims(resultIri1);

        List<Map<String, Object>> remaining = queryAllClaimsByCredentialSubject().getResults();
        boolean result1Gone = remaining.stream().noneMatch(r ->
            resultIri1.equals(r.get("s")) || resultIri1.equals(r.get("o")));
        assertTrue(result1Gone, "All triples referencing result1 IRI should be deleted");

        boolean result2Intact = remaining.stream().anyMatch(r ->
            resultIri2.equals(r.get("s")) || resultIri2.equals(r.get("o")));
        assertTrue(result2Intact, "Triples for result2 should not be affected");
    }

    @Test
    void queryData_sparqlStarFilterBySpecificCredential_returnsOnlyThatCredential() {
        String credA = "http://example.org/credFilterA";
        String credB = "http://example.org/credFilterB";
        graphStore.addClaims(List.of(
            typeClaim("http://example.org/filterSubjectA", "http://example.org/TypeFA")
        ), credA);
        graphStore.addClaims(List.of(
            typeClaim("http://example.org/filterSubjectB", "http://example.org/TypeFB")
        ), credB);

        List<Map<String, Object>> rows = queryBySpecificCredentialSubject(credA).getResults();

        assertEquals(1, rows.size(), "Filter by credA should return only credA's claim");
        assertEquals("http://example.org/filterSubjectA", rows.getFirst().get("s"),
            "Returned triple subject should belong to credA");
        assertTrue(rows.stream().noneMatch(r ->
                "http://example.org/filterSubjectB".equals(r.get("s"))),
            "Filter by credA should not return credB's claims");
    }


    private PaginatedResults<Map<String, Object>> querySparql(String sparql) {
        return graphStore.queryData(new GraphQuery(
            sparql, Map.of(), QueryLanguage.SPARQL, GraphQuery.QUERY_TIMEOUT, false));
    }

    private PaginatedResults<Map<String, Object>> queryAllClaimsByCredentialSubject() {
        return querySparql(
            "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> <" + CRED_SUBJECT_URI + "> ?cs }");
    }

    private PaginatedResults<Map<String, Object>> queryBySpecificCredentialSubject(String credentialSubject) {
        return querySparql(
            "SELECT ?s ?p ?o WHERE { <<(?s ?p ?o)>> <" + CRED_SUBJECT_URI + "> <" + credentialSubject + "> }");
    }

    private static RdfClaim typeClaim(String subject, String type) {
        return new CredentialClaim("<" + subject + ">", RDF_TYPE, "<" + type + ">");
    }

    private static RdfClaim literalClaim(String subject, String predicate, String value) {
        return new CredentialClaim("<" + subject + ">", "<" + predicate + ">", "\"" + value + "\"");
    }
}