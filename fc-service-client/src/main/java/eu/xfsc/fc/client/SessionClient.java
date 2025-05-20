package eu.xfsc.fc.client;

import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.Session;

public class SessionClient extends ServiceClient {

    public SessionClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public SessionClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public Session getCurrentSession() {
        return doGet("/session", Map.of(), Map.of(), Session.class);
    }

    public void deleteCurrentSession() {
        doDelete("/session", Map.of(), Map.of(), Void.class);
    }
}
