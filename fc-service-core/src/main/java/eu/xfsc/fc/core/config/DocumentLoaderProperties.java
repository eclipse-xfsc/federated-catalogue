package eu.xfsc.fc.core.config;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "federated-catalogue.verification.doc-loader", ignoreInvalidFields = true)
public class DocumentLoaderProperties {

    private Map<String, String> additionalContext = new LinkedHashMap<>();
    private int cacheSize;
    private Duration cacheTimeout;
    private boolean enableFile;
    private boolean enableHttp;
    private boolean enableLocalCache;
        
}
