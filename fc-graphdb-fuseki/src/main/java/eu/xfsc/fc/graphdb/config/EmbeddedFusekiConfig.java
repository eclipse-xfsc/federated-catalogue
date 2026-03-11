package eu.xfsc.fc.graphdb.config;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionFuseki;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@ConditionalOnExpression("'${federated-catalogue.scope}'.equals('test') && '${graphstore.impl}'.equals('fuseki')")
public class EmbeddedFusekiConfig {

    @Bean(destroyMethod = "stop")
    public FusekiServer fusekiServer() {
        log.info("starting Embedded Fuseki Server");
        FusekiServer server = FusekiServer.create()
            .add("/ds", DatasetFactory.createTxnMem())
            .port(0)
            .build();
        server.start();
        log.info("started Embedded Fuseki Server at {}", server.serverURL());
        return server;
    }

    @Bean(destroyMethod = "close")
    public RDFConnection rdfConnection(FusekiServer server) {
        return RDFConnectionFuseki.create()
            .destination(server.serverURL() + "ds")
            .build();
    }
}