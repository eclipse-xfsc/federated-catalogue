package eu.xfsc.fc.server.config;

import java.util.Collections;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties("federated-catalogue.query")
public class QueryProperties {

	private List<String> partners;
	private String self;
	
	public List<String> getPartners() {
		if (partners == null) {
			return Collections.emptyList();
		}
		return partners;
	}

	@Override
	public String toString() {
		return "QueryProperties [partners=" + partners + ", self=" + self + "]";
	}
	
}

