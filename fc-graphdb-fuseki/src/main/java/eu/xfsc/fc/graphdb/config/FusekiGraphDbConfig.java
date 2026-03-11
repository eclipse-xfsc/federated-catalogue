package eu.xfsc.fc.graphdb.config;

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
public class FusekiGraphDbConfig {

    @Value("${graphstore.uri}")
    private String uri;
        
    @Bean
    @Scope(value = ConfigurableBeanFactory.SCOPE_SINGLETON)
    public RDFConnection rdfConnection() {
        return RDFConnectionFuseki.create().destination(uri).build();
    }

}
