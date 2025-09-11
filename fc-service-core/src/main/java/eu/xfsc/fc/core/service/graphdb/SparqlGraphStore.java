package eu.xfsc.fc.core.service.graphdb;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.util.ClaimValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.sparql.core.ResultBinding;
import org.apache.jena.system.Txn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.http.HttpConnectTimeoutException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

@Slf4j
@Component
@Transactional
public class SparqlGraphStore implements GraphStore {

    private static final String PROP_CREDENTIAL_SUBJECT = "https://www.w3.org/2018/credentials#credentialSubject";

    /* Any appearances of ORDER BY (each word surrounded by any whitespace)
     * which is not enclosed by quotes
     */
    protected final Pattern orderByRegex = Pattern.compile("ORDER\\sBY(?=(?:[^'\"`]*(['\"`])[^'\"`]*\1)*[^'\"`]*$)", Pattern.CASE_INSENSITIVE);


    private final ClaimValidator claimValidator;

    @Autowired
    private RDFConnection rdfConnection;

    public SparqlGraphStore() {
        super();
        this.claimValidator = new ClaimValidator();
    }

    @Override
    public void addClaims(List<SdClaim> sdClaimList, String credentialSubject) {
        log.debug("addClaims.enter; got claims: {}, subject: {}", sdClaimList, credentialSubject);
        if(!sdClaimList.isEmpty()) {
            final Model starmodel = ModelFactory.createDefaultModel();
            final Model model = claimValidator.validateClaims(sdClaimList);
            model.listStatements().forEachRemaining(stmt -> {
                final Triple triple = stmt.asTriple();
                final Node qTripleNode = NodeFactory.createTripleNode(triple);

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
        final String deleteQuery = String.format("DELETE WHERE { ?s <%s> <%s> .}", PROP_CREDENTIAL_SUBJECT, credentialSubject);
        Txn.executeWrite(rdfConnection, () -> rdfConnection.update(deleteQuery));
    }

    @Override
    public PaginatedResults<Map<String, String>> queryData(GraphQuery sdQuery) {
        log.debug("queryData.enter; got query: {}", sdQuery);

        if (sdQuery.getQueryLanguage() != QueryLanguage.SPARQL) {
            throw new UnsupportedOperationException(sdQuery.getQueryLanguage() + " query language is not supported");
        }
        final QueryExecutionBuilder queryExecutionBuilder = rdfConnection.newQuery()
                .query(sdQuery.getQuery())
                .timeout(sdQuery.getTimeout(), TimeUnit.SECONDS);  // Fuseki timeout is in milliseconds per default
        try(final QueryExecution queryResults = queryExecutionBuilder.build()) {
            final List<Map<String, String>> parsedResults = new ArrayList<>(ResultSetFormatter.toList(queryResults.execSelect()).stream()
                    .map(qs -> (ResultBinding) qs)
                    .map(rb -> {
                        final Map<String, String> resultMap = new HashMap<>();
                        rb.varNames().forEachRemaining(varName -> resultMap.put(varName, rb.get(varName).toString()));
                        return resultMap;
                    }).toList());
            // Shuffle list to guarantee results won't appear in a deterministic order thus giving certain results
            // an advantage over others as they would always be in the top n result entries.
            // However, the shuffling should only be performed if the query does not, by itself, return an ordered result.
            if(! orderByRegex.matcher(sdQuery.getQuery()).find()) {
                Collections.shuffle(parsedResults);
            }
            return new PaginatedResults<>(parsedResults);
        } catch (Exception e) {
            if(e.getCause() instanceof HttpConnectTimeoutException) {
                log.error("Timeout while executing query: {}", sdQuery.getQuery(), e);
                throw new TimeoutException("Timeout while executing query");
            } else {
                log.error("Error while executing query: {}", sdQuery.getQuery(), e);
                throw new RuntimeException("Error while executing query", e);
            }
        }
    }
}
