package eu.xfsc.fc.graphdb.config;

import apoc.util.Utils;
import lombok.extern.slf4j.Slf4j;
import n10s.graphconfig.GraphConfigProcedures;
import n10s.rdf.load.RDFLoadProcedures;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.GraphDatabaseSettings.LogQueryLevel;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.Session;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnExpression("'${federated-catalogue.scope}'.equals('test')")
//@EnableAutoConfiguration
public class EmbeddedNeo4JConfig {

    /** Port 0 tells the OS to assign a random available port, avoiding conflicts. */
    private static final int RANDOM_AVAILABLE_PORT = 0;

    /**
     * Starts an in-process Neo4j server for tests.
     *
     * <p>Procedures are made available exclusively through {@code withProcedure(..)}. That path
     * registers with full access, so {@code dbms.security.procedures.allowlist} and
     * {@code .unrestricted} have no effect here and are deliberately not configured — nothing is
     * loaded from a plugin directory in an in-process server.
     *
     * <p>The registered set therefore does not mirror a deployed server, which installs and
     * allowlists {@code n10s} only. {@code apoc.util} is registered purely to give tests a
     * deterministic slow query ({@code apoc.util.sleep}) for the query-timeout assertions; the
     * behaviour under test is the transaction timeout of the Bolt driver, which is identical
     * without APOC installed.
     */
	@Bean
    public Neo4j embeddedDatabaseServer() {
        log.info("starting Embedded Neo4J DB");
        Neo4j embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withConfig(BoltConnector.listen_address, new SocketAddress("localhost", RANDOM_AVAILABLE_PORT))
                //.withConfig(GraphDatabaseSettings.log_queries_transaction_id, true)
                .withConfig(GraphDatabaseSettings.log_queries_transactions_level, LogQueryLevel.VERBOSE)
                // will be used for neo-semantics
                .withProcedure(GraphConfigProcedures.class) // n10s.graphconfig.*
                .withProcedure(RDFLoadProcedures.class)
                // test-only stopwatch for the query-timeout tests
                .withProcedure(Utils.class) // apoc.util.*
                .build();
        log.info("started Embedded Neo4J DB: {}", embeddedDatabaseServer);
        return embeddedDatabaseServer;
    }

    @Bean(destroyMethod = "close")
    public Driver driver(Neo4j embeddedDatabaseServer) {
        Config config = Config.builder().withLogging(Logging.slf4j()).build();
        Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI(), config);
        Session session = driver.session();
        session.run("CALL n10s.graphconfig.init({handleVocabUris:'MAP',handleMultival:\"ARRAY\",multivalPropList:[\"https://w3id.org/gaia-x/2511#claimsGraphUri\"] });"); /// run only when creating a new graph
        session.run("CREATE CONSTRAINT n10s_unique_uri IF NOT EXISTS FOR (r:Resource) REQUIRE r.uri IS UNIQUE");
        log.info("n10 procedure and Constraints are loaded successfully");
        return driver;
    }
}
