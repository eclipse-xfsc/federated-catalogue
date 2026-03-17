package eu.xfsc.fc.core.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "federated-catalogue.rdf")
public class RdfContentTypeProperties {

    private Set<String> contentTypes = new LinkedHashSet<>();

}