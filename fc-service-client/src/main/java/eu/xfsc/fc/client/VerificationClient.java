package eu.xfsc.fc.client;

import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.VerificationResult;

public class VerificationClient extends ServiceClient {

    public VerificationClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public VerificationClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public VerificationResult verify(String selfDescription) {
        return doPost("/verification", selfDescription, Map.of(), Map.of(), VerificationResult.class);
    }
}
