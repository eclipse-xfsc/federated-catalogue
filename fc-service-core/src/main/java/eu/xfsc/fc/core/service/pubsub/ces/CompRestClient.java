package eu.xfsc.fc.core.service.pubsub.ces;

import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.client.ServiceClient;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CompRestClient extends ServiceClient {

	private static final TypeReference<Map<String, Object>> mapTypeRef = new TypeReference<Map<String, Object>>() {};
	
	private final ObjectMapper jsonMapper;
	
	public CompRestClient(ObjectMapper mapper, String baseUrl) {
		super(baseUrl, (String)null);
		this.jsonMapper = mapper;
	}

	public Map<String, Object> postCredentials(String selfDescription) {
		log.debug("postCredentials.enter; got SD: {}", selfDescription.length());
		// can add optional vcid query param..
        Map<String, Object> queryParams = Map.of();
        String str = this.doPost("/credential-offers", selfDescription, Map.of(), queryParams, String.class);
        Map<String, Object> result;
		try {
			result = jsonMapper.readValue(str, mapTypeRef);
			log.debug("postCredentials.exit; returning creds: {}", result.size());
		} catch (JsonProcessingException ex) {
			log.error("postCredentials.error", ex);
			result = null;
		}
		return result;
	}
	
}
