package eu.xfsc.fc.server.config;

import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties("federated-catalogue.assets")
public class AssetProperties {

  private Set<String> humanReadableContentTypes = new LinkedHashSet<>(Set.of(
      "application/pdf",
      "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
      "text/html",
      "text/plain"
  ));

}
