package eu.xfsc.fc.core.config;

import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Scope;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "federated-catalogue.scope", havingValue = "runtime")
public class GraphDbConfig {

    @Value("${graphstore.uri}")
    private String uri;

    @Bean(destroyMethod = "close")
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public RDFConnection rdfConnection() {
        return RDFConnectionFuseki.create().destination(uri).build();
    }
}
