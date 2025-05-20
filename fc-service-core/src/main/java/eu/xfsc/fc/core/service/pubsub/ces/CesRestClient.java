package eu.xfsc.fc.core.service.pubsub.ces;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.client.ServiceClient;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class CesRestClient extends ServiceClient {

	private static final TypeReference<Map<String, Object>> mapTypeRef = new TypeReference<Map<String, Object>>() {};
	private static final TypeReference<List<Map<String, Object>>> listTypeRef = new TypeReference<List<Map<String, Object>>>() {};
	
	private final ObjectMapper jsonMapper;
	
	public CesRestClient(ObjectMapper jsonMapper, String baseUrl) {
		super(baseUrl, (String)null);
		this.jsonMapper = jsonMapper;
	}

	public List<Map<String, Object>> getCredentials(String lastId, int page, int size, String type) {
		log.debug("getCredentials.enter; got page: {}, size: {}, lastId: {}, type: {}", page, size, lastId, type);
        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("page", page);
        queryParams.put("size", size);
        if (lastId != null) {
            queryParams.put("lastReceivedID", lastId);
        }

		String str = this.doGet("/credentials-events", Map.of(), queryParams, String.class);
		List<Map<String, Object>> result = null;
		if (str != null) {
			try {
				result = jsonMapper.readValue(str, listTypeRef);
				log.debug("getCredentials.exit; returning {} creds", result.size());
			} catch (JsonProcessingException ex) {
				log.error("getCredentialsById.error", ex);
				result = List.of();
			}
		}
		return result;
	}
    
	public Map<String, Object> getCredentialsById(String id) { 
		log.debug("getCredentialsById.enter; got id: {}", id);
        Map<String, Object> pathParams = Map.of("id", id);
        String str = this.doGet("/credentials-events/{id}", pathParams, Map.of(), String.class);
        Map<String, Object> result = null;
		if (str != null) {
			try {
				result = jsonMapper.readValue(str, mapTypeRef);
				log.debug("getCredentialsById.exit; returning creds: {}", result.size());
			} catch (JsonProcessingException ex) {
				log.error("getCredentialsById.error", ex);
			}
		}
		return result;
	}
	
	public String postCredentials(Map<String, Object> cesEvent) {
		String id = (String) cesEvent.get("id");
		log.debug("postCredentials.enter; got event: {}", id);
        ResponseEntity<?> response = client
        		.post()
        		.uri("/credentials-events")
        		.bodyValue(cesEvent)
        		.retrieve()
        		.toEntity(Void.class)
        		.flatMap(entity -> Mono.just(entity)) 
        		.block();
		log.debug("postCredentials.exit; got response: {}", response);
		return response.getStatusCode().value() == HttpStatus.CREATED.value() ? response.getHeaders().getFirst(HttpHeaders.LOCATION) : null;
	}
	
}
