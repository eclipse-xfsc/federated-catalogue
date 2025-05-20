package eu.xfsc.fc.client;

import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

public class RoleClient extends ServiceClient {

    public RoleClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public RoleClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public List<String> getAllRoles(int offset, int limit) {
        Map<String, Object> queryParams = buildPagingParams(offset, limit);
        return doGet("/roles", Map.of(), queryParams, List.class);
    }
}
