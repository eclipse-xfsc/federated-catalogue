package eu.xfsc.fc.core.service.graphdb;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.pojo.GraphBackendType;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.CredentialClaim;

/**
 * Defines the required functions to add, query, update and delete active claims extracted from credentials
 * @author nacharya
 */
public interface GraphStore {

    /**
     * Pushes set of claims to the Graph DB. The set of claims are list of claim
     * objects containing subject, predicate and object similar to the form of n-triples
     * format stored in individual strings.
     *
     * @param claimList List of claims to be added to the Graph DB.
     * @param credentialSubject contains an asset unique identifier
     */
    void addClaims(List<CredentialClaim> claimList, String credentialSubject);

    /**
     * Deletes all claims in the Graph DB of a given asset
     * @param credentialSubject contains an asset unique identifier
     */
    void deleteClaims(String credentialSubject);

    /**
     * Query the graph when Cypher query is passed in query object and this
     * returns list of Maps with key value pairs as a result.
     *
     * @param query is the query to be executed
     * @return List of Maps
     */
    PaginatedResults<Map<String, Object>> queryData(GraphQuery query);

    /**
     * Returns the query language supported by this graph store implementation.
     *
     * @return the supported {@link QueryLanguage}, or empty if the store is disabled
     */
    Optional<QueryLanguage> getSupportedQueryLanguage();

    /**
     * Returns the type of graph database backend.
     *
     * @return the {@link GraphBackendType} of this store
     */
    GraphBackendType getBackendType();

    /**
     * Checks whether the graph store backend is reachable and operational.
     *
     * @return {@code true} if the backend is healthy, {@code false} otherwise
     */
    boolean isHealthy();

    /**
     * Returns the number of claim entries currently stored in the graph database.
     * Counts only claim-related data, not internal/structural nodes or triples.
     *
     * @return the claim count (&ge; 0), or {@code -1} if the count could not be
     *         determined (e.g. connectivity failure). The default implementation
     *         returns {@code 0}, suitable for disabled or dummy stores.
     */
    default long getClaimCount() {
        return 0;
    }

    /**
     * Returns the number of distinct RDF-assets whose claims are stored
     * in the graph database. Unlike {@link #getClaimCount()}, which counts
     * individual claim triples/nodes, this counts unique credential subjects.
     *
     * @return the asset count (&ge; 0), or {@code -1} if the count could not be
     *         determined (e.g. connectivity failure). The default implementation
     *         returns {@code 0}, suitable for disabled or dummy stores.
     */
    default long getRDFAssetCountInGraph() {
        return 0;
    }

}

