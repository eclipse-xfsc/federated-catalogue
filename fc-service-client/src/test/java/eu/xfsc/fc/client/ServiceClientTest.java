package eu.xfsc.fc.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.util.DefaultUriBuilderFactory;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ServiceClientTest {
    private ServiceClient serviceClient;

    @BeforeEach
    public void setup() {
        serviceClient = new ServiceClient("http://localhost", (String) null) {};
    }

    @Test
    public void testBuildUriWithPathAndQueryParams() {
        String path = "/participants/{participantId}/users";
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("participantId", "123");

        Map<String, Object> queryParams = new TreeMap<>();
        queryParams.put("age", 25);
        queryParams.put("status", "active");
        queryParams.put("role", "admin");

        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI result = serviceClient.buildUri(uriBuilder, path, pathParams, queryParams);

        assertEquals("http://localhost/participants/123/users?age=25&role=admin&status=active", 
                     serviceClient.getUrl() + result.toString());
    }

    @Test
    public void testBuildUriWithEmptyQueryParams() {
        String path = "/participants/{participantId}/users";
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("participantId", "123");

        Map<String, Object> queryParams = new TreeMap<>();
        queryParams.put("age", 25);
        queryParams.put("status", "active");
        queryParams.put("role", "");

        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI result = serviceClient.buildUri(uriBuilder, path, pathParams, queryParams);

        assertEquals("http://localhost/participants/123/users?age=25&role=&status=active", 
                     serviceClient.getUrl() + result.toString());
    }

    @Test
    public void testBuildUriWithNoQueryParams() {
        String path = "/participants/{participantId}/users";
        Map<String, Object> pathParams = new HashMap<>();
        pathParams.put("participantId", "123");

        Map<String, Object> queryParams = new HashMap<>();

        UriBuilder uriBuilder = new DefaultUriBuilderFactory().builder();
        URI result = serviceClient.buildUri(uriBuilder, path, pathParams, queryParams);

        assertEquals("http://localhost/participants/123/users", serviceClient.getUrl() + result.toString());
    }
}
