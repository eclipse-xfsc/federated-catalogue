package eu.xfsc.fc.client;

import java.util.List;
import java.util.Map;

import org.springframework.web.reactive.function.client.WebClient;

import eu.xfsc.fc.api.generated.model.User;
import eu.xfsc.fc.api.generated.model.UserProfile;
import eu.xfsc.fc.api.generated.model.UserProfiles;

public class UserClient extends ServiceClient {

    public UserClient(String baseUrl, String jwt) {
        super(baseUrl, jwt);
    }

    public UserClient(String baseUrl, WebClient client) {
        super(baseUrl, client);
    }

    public UserProfile getUser(String userId) {
        Map<String, Object> pathParams = Map.of("userId", userId);
        return doGet("/users/{userId}", pathParams, Map.of(), UserProfile.class);
    }

    public UserProfiles getUsers(int offset, int limit) {
        Map<String, Object> queryParams = buildPagingParams(offset, limit);
        return doGet("/users", Map.of(), queryParams, UserProfiles.class);
    }

    public List<String> getUserRoles(String userId) {
        Map<String, Object> pathParams = Map.of("userId", userId);
        return doGet("/users/{userId}/roles", pathParams, Map.of(), List.class);
    }

    public UserProfile addUser(User user) {
        return doPost("/users", user, Map.of(), Map.of(), UserProfile.class);
    }

    public UserProfile deleteUser(String userId) {
        Map<String, Object> pathParams = Map.of("userId", userId);
        return doDelete("/users/{userId}", pathParams, Map.of(), UserProfile.class);
    }

    public UserProfile updateUser(String userId, User user) {
        Map<String, Object> pathParams = Map.of("userId", userId);
        return doPut("/users/{userId}", user, pathParams, Map.of(), UserProfile.class);
    }

    public List<String> updateUserRoles(String userId, List<String> roles) {
        Map<String, Object> pathParams = Map.of("userId", userId);
        return doPut("/users/{userId}/roles", roles, pathParams, Map.of(), List.class);
    }
}
