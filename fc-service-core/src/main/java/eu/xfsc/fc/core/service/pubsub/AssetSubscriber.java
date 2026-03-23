package eu.xfsc.fc.core.service.pubsub;

import java.util.Map;

public interface AssetSubscriber {
	
	void onMessage(Map<String, Object> payload);

}
