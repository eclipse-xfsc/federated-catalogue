package eu.xfsc.fc.graphdb.service;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.RdfClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.util.ClaimValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionBuilder;
import org.apache.jena.query.ResultSet;
import org.apache.jena.query.ResultSetFormatter;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.core.ResultBinding;
import org.apache.jena.system.Txn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpConnectTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@Transactional
@ConditionalOnProperty(value = "graphstore.impl", havingValue = "fuseki")
public class SparqlGraphStore implements GraphStore {

    private static final String PROP_CREDENTIAL_SUBJECT = "https://www.w3.org/2018/credentials#credentialSubject";

    /* Any appearances of ORDER BY (each word surrounded by any whitespace)
     * which is not enclosed by quotes
     */
    protected final Pattern orderByRegex = Pattern.compile("ORDER\\sBY(?=(?:[^'\"`]*(['\"`])[^'\"`]*\1)*[^'\"`]*$)", Pattern.CASE_INSENSITIVE);

    // Rejects characters that break the <iri> token in a SPARQL string context.
    private static final Pattern SAFE_IRI_PATTERN = Pattern.compile("^[^<>\"\\\\\\s{}|^`\\[\\]]+$");


    private final ClaimValidator claimValidator;

    @Autowired
    private RDFConnection rdfConnection;

    public SparqlGraphStore() {
        super();
        this.claimValidator = new ClaimValidator();
    }

    /** {@inheritDoc} */
    @Override
    public Optional<QueryLanguage> getSupportedQueryLanguage() {
        return Optional.of(QueryLanguage.SPARQL);
    }

    /** {@inheritDoc} */
    @Override
    public GraphBackendType getBackendType() {
        return GraphBackendType.FUSEKI;
    }

    /** {@inheritDoc} */
    @Override
    public boolean isHealthy() {
        try {
            Txn.calculateRead(rdfConnection, () -> {
                try (QueryExecution qe = rdfConnection.newQuery()
                        .query("ASK { ?s ?p ?o }").build()) {
                    return qe.execAsk();
                }
            });
            return true;
        } catch (Exception e) {
            log.warn("Fuseki health check failed", e);
            return false;
        }
    }

    /** {@inheritDoc} */
    @Override
    public long getClaimCount() {
        try {
            return Txn.calculateRead(rdfConnection, () -> {
                String query = "SELECT (COUNT(*) AS ?cnt) WHERE { "
                    + "<<(?s ?p ?o)>> <" + PROP_CREDENTIAL_SUBJECT + "> ?cs }";
                try (QueryExecution qe = rdfConnection.newQuery().query(query).build()) {
                    ResultSet rs = qe.execSelect();
                    if (rs.hasNext()) {
                        return rs.next().getLiteral("cnt").getLong();
                    }
                }
                return 0L;
            });
        } catch (Exception e) {
            log.warn("Failed to get Fuseki claim count: {}", e.getMessage());
            return -1;
        }
    }

    /** {@inheritDoc} */
    @Override
    public long getRDFAssetCountInGraph() {
        try {
            return Txn.calculateRead(rdfConnection, () -> {
                String query = "SELECT (COUNT(DISTINCT ?cs) AS ?cnt) WHERE { "
                    + "<<(?s ?p ?o)>> <" + PROP_CREDENTIAL_SUBJECT + "> ?cs }";
                try (QueryExecution qe = rdfConnection.newQuery().query(query).build()) {
                    ResultSet rs = qe.execSelect();
                    if (rs.hasNext()) {
                        return rs.next().getLiteral("cnt").getLong();
                    }
                }
                return 0L;
            });
        } catch (Exception e) {
            log.warn("Failed to get Fuseki asset count: {}", e.getMessage());
            return -1;
        }
    }

    @Override
    public void addClaims(List<RdfClaim> claimList, String credentialSubject) {
        log.debug("addClaims.enter; got claims: {}, subject: {}", claimList, credentialSubject);
        requireSafeIri(credentialSubject);
        if (!claimList.isEmpty()) {
            final Model starmodel = ModelFactory.createDefaultModel();
            final Model model = claimValidator.validateClaims(claimList);
            model.listStatements().forEachRemaining(stmt -> {
                final Triple triple = stmt.asTriple();
                final Node qTripleNode = NodeFactory.createTripleTerm(triple);

                final Property credSubProp = starmodel.createProperty(PROP_CREDENTIAL_SUBJECT);
                final Resource credSubValue = starmodel.createResource(credentialSubject);
                starmodel.add(starmodel.asRDFNode(qTripleNode).asResource(), credSubProp, credSubValue);
            });
            Txn.executeWrite(rdfConnection, () -> rdfConnection.load(starmodel));
        }
    }

    @Override
    public void deleteClaims(String credentialSubject) {
        log.debug("deleteClaims.enter; got subject: {}", credentialSubject);
        requireSafeIri(credentialSubject);
        final String deleteQuery = String.format("DELETE WHERE { ?s <%s> <%s> .}", PROP_CREDENTIAL_SUBJECT, credentialSubject);
        Txn.executeWrite(rdfConnection, () -> rdfConnection.update(deleteQuery));
    }

    @Override
    public void deleteValidationResultClaims(String resultIri) {
        log.debug("deleteValidationResultClaims.enter; resultIri={}", resultIri);
        requireSafeIri(resultIri);
        // Delete all RDF-star annotations where the embedded triple has resultIri as subject or object.
        // Two operations: (1) result property triples <<(resultIri ?p ?o)>>, (2) hasValidationResult links <<(?s ?p resultIri)>>.
        // Note: <<(?s ?p ?o)>> is the annotation-pattern syntax required by Jena SPARQL-star in WHERE/DELETE clauses.
        final String query = String.format(
            "DELETE WHERE { <<(<%1$s> ?p ?o)>> <%2$s> ?cs . } ;" +
            "DELETE WHERE { <<(?s ?p <%1$s>)>> <%2$s> ?cs . }",
            resultIri, PROP_CREDENTIAL_SUBJECT);
        Txn.executeWrite(rdfConnection, () -> rdfConnection.update(query));
        log.debug("deleteValidationResultClaims.exit");
    }

    private static void requireSafeIri(String iri) {
        if (iri == null || !SAFE_IRI_PATTERN.matcher(iri).matches()) {
            throw new ServerException("IRI contains characters unsafe for SPARQL interpolation: " + iri);
        }
    }

    @Override
    public PaginatedResults<Map<String, Object>> queryData(GraphQuery query) {
        log.debug("queryData.enter; got query: {}", query);

        if (query.getQueryLanguage() != QueryLanguage.SPARQL) {
            throw new UnsupportedOperationException(query.getQueryLanguage() + " query language is not supported");
        }
        return Txn.calculateRead(rdfConnection, () -> {
            final QueryExecutionBuilder queryExecutionBuilder = rdfConnection.newQuery()
                    .query(query.getQuery())
                    .timeout(query.getTimeout(), TimeUnit.SECONDS);  // Fuseki timeout is in milliseconds per default
            try(final QueryExecution queryResults = queryExecutionBuilder.build()) {
                final List<Map<String, Object>> parsedResults = new ArrayList<>(ResultSetFormatter.toList(queryResults.execSelect()).stream()
                        .map(qs -> (ResultBinding) qs)
                        .map(rb -> {
                            final Map<String, Object> resultMap = new HashMap<>();
                            rb.varNames().forEachRemaining(varName -> resultMap.put(varName, convertRdfNode(rb.get(varName))));
                            return resultMap;
                        }).toList());
                // Shuffle list to guarantee results won't appear in a deterministic order thus giving certain results
                // an advantage over others as they would always be in the top n result entries.
                // However, the shuffling should only be performed if the query does not, by itself, return an ordered result.
                if (!orderByRegex.matcher(query.getQuery()).find()) {
                    Collections.shuffle(parsedResults);
                }
                return new PaginatedResults<>(parsedResults);
            } catch (Exception e) {
                if (e.getCause() instanceof HttpConnectTimeoutException) {
                    log.error("Timeout while executing query: {}", query.getQuery(), e);
                    throw new TimeoutException("Timeout while executing query");
                } else {
                    log.error("Error while executing query: {}", query.getQuery(), e);
                    throw new ServerException("error querying data " + e.getMessage(), e);
                }
            }
        });
    }

    /**
     * Converts an {@link RDFNode} to a JSON-serializable Java object.
     */
    private Object convertRdfNode(RDFNode node) {
        if (node == null) {
            return null;
        }
        if (node.isLiteral()) {
            Literal lit = node.asLiteral();
            try {
                Object value = lit.getValue();
                if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                    return value;
                }
                // Jena-internal types (e.g. XSDDateTime) are not JSON-serializable
                return lit.getLexicalForm();
            } catch (Exception e) {
                log.warn("Could not extract typed value for literal '{}': {}", lit.getLexicalForm(), e.getMessage());
                return lit.getLexicalForm();
            }
        }
        if (node.isURIResource()) {
            return node.asResource().getURI();
        }
        return node.toString();
    }
}
