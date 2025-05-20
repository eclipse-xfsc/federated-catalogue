package eu.xfsc.fc.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import eu.xfsc.fc.core.config.CoreConfig;

/**
 * Federated Catalogue core service configuration.
 */
@Configuration
@Import(value = {CoreConfig.class})
public class ServiceConfig {

}
