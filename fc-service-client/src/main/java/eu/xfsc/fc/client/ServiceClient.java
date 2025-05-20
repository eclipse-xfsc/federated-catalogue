package eu.xfsc.fc.client;

import static org.springframework.security.oauth2.client.web.reactive.function.client.ServerOAuth2AuthorizedClientExchangeFilterFunction.oauth2AuthorizedClient;

import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.json.Jackson2JsonDecoder;
import org.springframework.http.codec.json.Jackson2JsonEncoder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import java.net.URI;

@Slf4j
public abstract class ServiceClient {

    protected final String baseUrl;
    protected final ObjectMapper mapper;
    protected final WebClient client;

    public ServiceClient(String baseUrl, String jwt) {
        this.baseUrl = baseUrl;
        mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        WebClient.Builder builder = WebClient.builder()
            .baseUrl(baseUrl)
            .codecs(configurer -> {
                configurer.defaultCodecs().jackson2JsonEncoder(new Jackson2JsonEncoder(mapper, MediaType.APPLICATION_JSON));
                configurer.defaultCodecs().jackson2JsonDecoder(new Jackson2JsonDecoder(mapper, MediaType.APPLICATION_JSON));
            })
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .filter(ExchangeFilterFunction.ofResponseProcessor(response -> {
            	if (response.statusCode().isError()) {
            		return response.bodyToMono(Map.class)
            				.flatMap(map -> Mono.error(new ExternalServiceException(response.statusCode(), map)));
            	}
            	return Mono.just(response);
            }));
        if (jwt != null) {
            builder = builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jwt);
        }
        this.client = builder.build();
    }

    public ServiceClient(String baseUrl, WebClient client) {
        this.baseUrl = baseUrl;
        this.client = client;
        this.mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public String getUrl() {
        return this.baseUrl;
    }

    protected URI buildUri(UriBuilder uriBuilder, String path, Map<String, Object> pathParams, Map<String, Object> queryParams) {
        UriBuilder builder = uriBuilder.path(path);
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(builder::queryParam);
        }
        return builder.build(pathParams);
    }

    protected Map<String, Object> buildPagingParams(int offset, int limit) {
        if (limit == 0) {
            limit = 50;
        }
        return Map.of("offset", offset, "limit", limit);
    }

    protected <T> T doGet(String path, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType) {
        return client
            .get()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .retrieve()
            .bodyToMono(reType)
            .block();
    }

    protected <T> T doGet(String path, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType, OAuth2AuthorizedClient authorizedClient) {
        return client
            .get()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .attributes(oauth2AuthorizedClient(authorizedClient))
            .retrieve()
            .bodyToMono(reType)
            .block();
    }

    protected <T> T doPost(String path, Object body, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType) {
        return client
            .post()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(reType)
            .block();
    }

    protected <T> T doPost(String path, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType) {
        return client
            .post()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .retrieve()
            .bodyToMono(reType)
            .block();
    }

    protected <T> T doPost(String path, Object body, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType, OAuth2AuthorizedClient authorizedClient) {
        return client
            .post()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .bodyValue(body)
            .attributes(oauth2AuthorizedClient(authorizedClient))
            .retrieve()
            .bodyToMono(reType)
            .block();
    }

    protected <T> Mono<T> doPostAsync(String path, Object body, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType) {
        return client
            .post()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(reType);
    }

    protected <T> Mono<T> doPostAsync(String path, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType) {
        return client
            .post()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .retrieve()
            .bodyToMono(reType);
    }

    protected <T> Mono<T> doPostAsync(String path, Object body, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType, OAuth2AuthorizedClient authorizedClient) {
        return client
            .post()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .bodyValue(body)
            .attributes(oauth2AuthorizedClient(authorizedClient))
            .retrieve()
            .bodyToMono(reType);
    }

    protected <T> T doPut(String path, Object body, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType) {
        return client
            .put()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .bodyValue(body)
            .retrieve()
            .bodyToMono(reType)
            .block();
    }

    protected <T> T doPut(String path, Object body, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType, OAuth2AuthorizedClient authorizedClient) {
        return client
            .put()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .bodyValue(body)
            .attributes(oauth2AuthorizedClient(authorizedClient))
            .retrieve()
            .bodyToMono(reType)
            .block();
    }

    protected <T> T doDelete(String path, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType) {
        return client
            .delete()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .retrieve()
            .bodyToMono(reType)
            .block();
    }

    protected <T> T doDelete(String path, Map<String, Object> pathParams, Map<String, Object> queryParams, Class<T> reType, OAuth2AuthorizedClient authorizedClient) {
        return client
            .delete()
            .uri(uriBuilder -> buildUri(uriBuilder, path, pathParams, queryParams))
            .attributes(oauth2AuthorizedClient(authorizedClient))
            .retrieve()
            .bodyToMono(reType)
            .block();
    }
}
