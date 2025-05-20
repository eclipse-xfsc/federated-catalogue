package eu.xfsc.fc.client;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class ExternalServiceException extends RuntimeException {
	
	private static final long serialVersionUID = 1L;
	
	private final HttpStatusCode status;
	private final Map<String, Object> details;

	public ExternalServiceException(HttpStatusCode status, Map<String, Object> details) {
		super((String) null);
		this.status = status;
		this.details = details;
	}
	
	public HttpStatusCode getStatus() {
		return status;
	}
	
	public Map<String, Object> getDetails() {
		return details;
	}
	
	@Override
	public String getMessage() {
		return details == null ? HttpStatus.valueOf(status.value()).toString() : details.toString();
	}

}
