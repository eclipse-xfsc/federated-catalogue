package eu.xfsc.fc.core.service.pubsub.ces;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CesTracking {
	
	private String cesId;
    private String event;
    private Instant createdAt;
    private int credProcessed;
    private String credId;
    private String error;
    
    public boolean isSuccess() {
    	return credId == null && error == null; 
    }

}
