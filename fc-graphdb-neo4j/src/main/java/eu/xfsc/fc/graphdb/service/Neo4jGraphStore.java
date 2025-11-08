package eu.xfsc.fc.graphdb.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.tuple.Pair;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.internal.InternalNode;
import org.neo4j.driver.internal.InternalRelationship;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import eu.xfsc.fc.api.generated.model.QueryLanguage;
import eu.xfsc.fc.core.exception.ServerException;
import eu.xfsc.fc.core.exception.TimeoutException;
import eu.xfsc.fc.core.pojo.GraphQuery;
import eu.xfsc.fc.core.pojo.PaginatedResults;
import eu.xfsc.fc.core.pojo.SdClaim;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.util.ClaimValidator;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@Transactional // not sure it is correct annotation 
@ConditionalOnProperty(value = "graphstore.impl", havingValue = "neo4j")
public class Neo4jGraphStore implements GraphStore {

    private static final String queryInsert = "CALL n10s.rdf.import.inline($payload, \"N-Triples\");"; 
    private static final String queryDelete = "MATCH (n {claimsGraphUri: [$uri]})\n" +
                                              "DETACH DELETE n;";
    private static final String queryUpdate = "MATCH (n) WHERE $uri IN n.claimsGraphUri\n" +
                                              "SET n.claimsGraphUri = [g IN n.claimsGraphUri WHERE g <> $uri];";
    
    @Autowired
    private Driver driver;
    private final ClaimValidator claimValidator;

    @Value("${graphstore.timeout-marker:timeout}")
    private String timeoutMarker;
    
    /* Any appearances of ORDER BY (each word surrounded by any whitespace)
     * which is not enclosed by quotes
     */
    protected final Pattern orderByRegex = Pattern.compile("ORDER\\sBY(?=(?:[^'\"`]*(['\"`])[^'\"`]*\1)*[^'\"`]*$)", Pattern.CASE_INSENSITIVE);

    public Neo4jGraphStore() {
        super();
        this.claimValidator = new ClaimValidator();
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public void addClaims(List<SdClaim> sdClaimList, String credentialSubject) {
        log.debug("addClaims.enter; got claims: {}, subject: {}", sdClaimList, credentialSubject);
        if (!sdClaimList.isEmpty()) {
            try (Session session = driver.session()) { 
                Pair<String, Set<String>> props = claimValidator.resolveClaims(sdClaimList, credentialSubject);
                if (!props.getRight().isEmpty()) {
                    updateGraphConfig(session, props.getRight());
                }
                Result rs = session.run(queryInsert, Map.of("payload", props.getLeft()));
                log.debug("addClaims; inserted: {}", rs.consume());
            }
        }
        log.debug("addClaims.exit");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deleteClaims(String credentialSubject) {
        log.debug("deleteClaims.enter; got subject: {}", credentialSubject);
        Map<String, Object> params = Map.of("uri", credentialSubject);
        try (Session session = driver.session()) {
            Result rsDelelte = session.run(queryDelete, params);
            log.debug("deleteClaims; deleted: {}", rsDelelte.consume());
            Result rsUpdate = session.run(queryUpdate, params);
            log.debug("deleteClaims; updated: {}", rsUpdate.consume());
        }
        log.debug("deleteClaims.exit");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PaginatedResults<Map<String, Object>> queryData(GraphQuery sdQuery) {
        log.debug("queryData.enter; got query: {}", sdQuery);

        if (sdQuery.getQueryLanguage() != QueryLanguage.OPENCYPHER) {
            throw new UnsupportedOperationException(sdQuery.getQueryLanguage() + " query language is not supported yet");
        }

        TransactionConfig transactionConfig = TransactionConfig.builder()
                .withTimeout(Duration.ofSeconds(sdQuery.getTimeout()))
                .build();

        long stamp = System.currentTimeMillis();
        try (Session session = driver.session()) {
            //In this method we use read transaction to avoid any Cypher query that modifies data
            return session.executeRead(tx -> doQuery(tx, sdQuery), transactionConfig);
        } catch (Exception ex) {
            stamp = System.currentTimeMillis() - stamp;
            log.error("queryData.error: {}", ex.getMessage());
            if (ex.getMessage() != null && ex.getMessage().contains(timeoutMarker)) {
                if (stamp > sdQuery.getTimeout() * 1000) {
                    throw new TimeoutException("query timeout (" + sdQuery.getTimeout() + " sec) exceeded)");
                }
            }
            throw new ServerException("error querying data " + ex.getMessage());
        }
    }
    
    private PaginatedResults<Map<String, Object>> doQuery(TransactionContext tx, GraphQuery query) {
        List<Map<String, Object>> resultList = new ArrayList<>();
        String finalString = getDynamicallyAddedCountClauseQuery(query);
        Result result = tx.run(finalString, query.getParams());
        log.debug("doQuery; got result: {}", result.keys());
        Long totalCount = 0L;
        while (result.hasNext()) {
            org.neo4j.driver.Record record = result.next();
            Map<String, Object> map = record.asMap();
            log.debug("doQuery; record: {}", map);
            Map<String, Object> outputMap = new HashMap<>();
            totalCount = (Long) map.getOrDefault("totalCount", Long.valueOf(resultList.size()));
            for (var entry : map.entrySet()) {
                if (entry.getKey().equals("totalCount"))
                    continue;
                if (entry.getValue() == null) {
                    outputMap.put(entry.getKey(), null);
                } else if (entry.getValue() instanceof InternalNode) {
                    Map<String, Object> nodeMap = ((InternalNode) entry.getValue()).asMap();
                    Map<String, Object> modifiableNodeMap = new HashMap<>(nodeMap);
                    modifiableNodeMap.remove("uri");
                    outputMap.put(entry.getKey(), modifiableNodeMap);
                } else if (entry.getValue() instanceof InternalRelationship) {
                    outputMap.put(entry.getKey(), ((InternalRelationship) entry.getValue()).type());
                } else {
                    outputMap.put(entry.getKey(), entry.getValue());
                }
            }
            resultList.add(outputMap);
        }

        // Shuffle list to guarantee results won't appear in a deterministic order thus giving certain results
        // an advantage over others as they would always be in the top n result entries.
        // However, the shuffling should only be performed if the query does not, by itself, return an ordered result.
        Matcher matcher = orderByRegex.matcher(query.getQuery());
        boolean queryProvidesOrderedResult = matcher.find();
        if (!queryProvidesOrderedResult) {
            Collections.shuffle(resultList);
        }

        log.debug("doQuery.exit; returning: {}", resultList);
        return new PaginatedResults<>(totalCount, resultList);
    }

    @SuppressWarnings("unchecked")
	private void updateGraphConfig(Session session, Set<String> properties) {
        Result config = session.run("CALL n10s.graphconfig.show");
        while (config.hasNext()) {
            org.neo4j.driver.Record record = config.next();
            Map<String,Object> propMap = record.asMap();
            if (propMap.get("param").equals("multivalPropList")) {
                Collection<String> propList = new HashSet<>((Collection<String>) propMap.get("value"));
                log.debug("updateGraphConfig; got multivalPropList {}", propList);
                int size = propList.size();
                propList.addAll(properties);
                if (propList.size() > size) {
                    log.debug("updateGraphConfig; Adding new properties to graphconfig {}", propList);
                    try {
                        Map<String, Object> params = Map.of("propList", propList, "force", true);
                        session.run("CALL n10s.graphconfig.set({multivalPropList: $propList, force: $force})", params);
                    } catch (Exception e) {
                        log.error("updateGraphConfig.error; Failed to add new properties due to Exception", e);
                    }
                }
                break;
            }
        }
    }

    private String getDynamicallyAddedCountClauseQuery(GraphQuery sdQuery) {
        if (sdQuery.isWithTotalCount()) {
            log.debug("getDynamicallyAddedCountClauseQuery.enter; actual query: {}", sdQuery.getQuery());
            /*get string before statements and append count clause*/
            String statement = "return";

            String queryStatementLowerCase = sdQuery.getQuery().toLowerCase();
            int indexOf = queryStatementLowerCase.lastIndexOf(statement);

            if (indexOf == -1) {
                // no need for count if no return
                return sdQuery.getQuery();
            }

            /*add totalCount to query to get count*/
            StringBuffer subStringOfCount = new StringBuffer(sdQuery.getQuery().substring(0, indexOf));
            subStringOfCount.append("WITH count(*) as totalCount ");

            /*append totalCount to return statements*/
            StringBuffer actualQuery = new StringBuffer(sdQuery.getQuery());
            int indexOfAfter = actualQuery.toString().toLowerCase().lastIndexOf(statement) + statement.length();

            if (queryStatementLowerCase.lastIndexOf("return *") == -1) {
                actualQuery.insert(indexOfAfter + 1, "totalCount, ");
            }
            /*finally combine both string */
            String finalString = subStringOfCount.append(actualQuery).toString();
            log.debug("getDynamicallyAddedCountClauseQuery.exit; count query appended : {}", finalString);
            return finalString;
        }
        return sdQuery.getQuery();
    }

}
