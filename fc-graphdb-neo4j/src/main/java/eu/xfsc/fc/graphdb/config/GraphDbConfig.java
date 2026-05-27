package eu.xfsc.fc.graphdb.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnExpression("'${federated-catalogue.scope}'.equals('runtime')")
public class GraphDbConfig {

    @Value("${graphstore.neo4j.uri:${graphstore.uri}}")
    private String uri;
    @Value("${graphstore.user}")
    private String user;
    @Value("${graphstore.password}")
    private String password;

    // @Lazy + no eager session.run() — the Neo4j driver does not open a TCP connection at
    // construction; it only connects on first session(). n10s schema bootstrap was moved
    // out of this factory into Neo4jGraphStore.ensureInitialized() so that an unreachable
    // Neo4j container at boot does not crash the JVM in routing mode.
    @Bean(destroyMethod = "close")
    @Lazy
    public Driver driver() {
        Config config = Config.builder().withLogging(Logging.slf4j()).build();
        return GraphDatabase.driver(uri, AuthTokens.basic(user, password), config);
    }

}
