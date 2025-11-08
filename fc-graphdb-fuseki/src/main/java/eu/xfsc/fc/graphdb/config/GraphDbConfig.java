package eu.xfsc.fc.graphdb.config;

import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Config;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Logging;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnExpression("'${federated-catalogue.scope}'.equals('runtime') && '${graphstore.impl}'.equals('fuseki')")
public class GraphDbConfig {

    @Value("${graphstore.uri}")
    private String uri;
        
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public RDFConnection rdfConnection() {
        return RDFConnectionFuseki.create().destination(uri).build();
    }

}
