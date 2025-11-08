package eu.xfsc.fc.graphdb.config;

import java.util.List;

import apoc.util.Utils;
import org.neo4j.configuration.GraphDatabaseSettings;
import org.neo4j.configuration.GraphDatabaseSettings.LogQueryLevel;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.configuration.helpers.SocketAddress;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.gds.catalog.GraphExistsProc;
import org.neo4j.gds.catalog.GraphListProc;
import org.neo4j.gds.catalog.GraphProjectProc;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;
//import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import lombok.extern.slf4j.Slf4j;
import n10s.graphconfig.GraphConfigProcedures;
import n10s.rdf.load.RDFLoadProcedures;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "federated-catalogue.scope", havingValue = "test")
//@EnableAutoConfiguration
public class EmbeddedNeo4JConfig {
    
	@Bean
    public Neo4j embeddedDatabaseServer() {
        log.info("starting Embedded Neo4J DB");
        Neo4j embeddedDatabaseServer = Neo4jBuilders.newInProcessBuilder()
                .withDisabledServer()
                .withConfig(BoltConnector.listen_address, new SocketAddress(BoltConnector.DEFAULT_PORT))
                .withConfig(GraphDatabaseSettings.procedure_allowlist, List.of("gds.*", "n10s.*", "apoc.*, semantics.*"))
                .withConfig(GraphDatabaseSettings.procedure_unrestricted, List.of("gds.*", "n10s.*", "apoc.*, semantics.*"))
                //.withConfig(GraphDatabaseSettings.log_queries_transaction_id, true)
                .withConfig(GraphDatabaseSettings.log_queries_transactions_level, LogQueryLevel.VERBOSE)
                // will be user for gds procedure
                // Commented out to prevent the org.neo4j.kernel.lifecycle.LifecycleException: Component 'org.neo4j.harness.internal.AbstractInProcessNeo4jBuilder$Neo4jHarnessExtensions$1@732f3b2f' 
                // was successfully initialized, but failed to start. Please see the attached cause exception "Unable to set up injection for procedure `GraphExistsProc`, the field `facade` has type 
                // `class org.neo4j.gds.procedures.GraphDataScience` which is not a known injectable component."
         //       .withProcedure(GraphExistsProc.class) // gds.graph.exists procedure
         //       .withProcedure(GraphListProc.class)
         //       .withProcedure(GraphProjectProc.class)
                // will be used for neo-semantics
                .withProcedure(GraphConfigProcedures.class) // n10s.graphconfig.*
                .withProcedure(RDFLoadProcedures.class)
                // will be used for apoc
                .withProcedure(Utils.class) // apoc.utils.*
                .build();
        log.info("started Embedded Neo4J DB: {}", embeddedDatabaseServer);
        return embeddedDatabaseServer;
    }

    @Bean(destroyMethod = "close")
    public Driver driver(Neo4j embeddedDatabaseServer) {
        Config config = Config.builder().withLogging(Logging.slf4j()).build();
        Driver driver = GraphDatabase.driver(embeddedDatabaseServer.boltURI(), config);
        Session session = driver.session();
     //   Result result = session.run("CALL gds.graph.exists('neo4j');");
     //   if (result.hasNext()) {
     //       org.neo4j.driver.Record record = result.next();
     //       org.neo4j.driver.Value value = record.get("exists");
     //       if (value != null && value.asBoolean()) {
     //           log.info("Graph already loaded");
     //           return driver;
     //       }
     //   }
        session.run("CALL n10s.graphconfig.init({handleVocabUris:'MAP',handleMultival:\"ARRAY\",multivalPropList:[\"http://w3id.org/gaia-x/service#claimsGraphUri\"] });"); /// run only when creating a new graph
        session.run("CREATE CONSTRAINT n10s_unique_uri IF NOT EXISTS FOR (r:Resource) REQUIRE r.uri IS UNIQUE");
        log.info("n10 procedure and Constraints are loaded successfully");
        return driver;
    }
}
