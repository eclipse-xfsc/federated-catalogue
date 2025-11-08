package eu.xfsc.fc.server.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import eu.xfsc.fc.core.config.CoreConfig;
//import eu.xfsc.fc.graphdb.config.GraphDbConfig;

/**
 * Federated Catalogue core service configuration.
 */
@Configuration
@Import(value = {CoreConfig.class})
@ComponentScan(basePackages = {"eu.xfsc.fc.graphdb.service"})
public class ServiceConfig {

}
