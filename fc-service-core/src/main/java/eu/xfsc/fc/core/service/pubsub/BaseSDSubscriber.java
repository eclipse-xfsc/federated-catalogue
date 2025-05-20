package eu.xfsc.fc.core.service.pubsub;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.SelfDescriptionResult;
import eu.xfsc.fc.api.generated.model.SelfDescriptionStatus;
import eu.xfsc.fc.client.SelfDescriptionClient;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.SelfDescriptionMetadata;
import eu.xfsc.fc.core.pojo.VerificationResult;
import eu.xfsc.fc.core.service.pubsub.SDPublisher.SDEvent;
import eu.xfsc.fc.core.service.sdstore.SelfDescriptionStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseSDSubscriber implements SDSubscriber {

	protected static final TypeReference<HashMap<String, Object>> mapTypeRef = new TypeReference<HashMap<String, Object>>() {};
		
    @Value("${subscriber.instance}")
    protected String instance;
    @Autowired 
	protected SelfDescriptionStore sdStore;
    @Autowired 
	protected VerificationService verificationService;
    @Autowired 
	protected ObjectMapper jsonMapper;

	protected Map<String, SelfDescriptionClient> sdClients = new HashMap<>();
    
    @PostConstruct
    public void init() throws Exception {
    	subscribe();
    }
	
	@Override
	public void onMessage(Map<String, Object> params) {
		log.debug("onMessage.enter; got params: {}", params);
		SDEvent event = SDEvent.valueOf((String) params.get("event"));
		String hash = (String) params.get("hash");
		switch (event) {
			case ADD:
				try {
					VerificationResult vr;
					SelfDescriptionMetadata sdMeta;
					String dataStr = (String) params.get("data");
					if (dataStr == null) {
						// get it by hash from other instance, then register locally.. may be do it in separate working queue?
						String source = (String) params.get("source");
						SelfDescriptionClient sdClient = sdClients.computeIfAbsent(source, src -> new SelfDescriptionClient(src, (String) null));
						SelfDescriptionResult sd = sdClient.getSelfDescriptionByHash(hash, false, true);
						ContentAccessor content = new ContentAccessorDirect(sd.getContent());
						// how to get proper VR class?
				        vr = verificationService.verifyOfferingSelfDescription(content);
					    sdMeta = new SelfDescriptionMetadata(vr.getId(), vr.getIssuer(), vr.getValidators(), content);
					} else {
						Map<String, Object> data = jsonMapper.readValue((String) params.get("data"), mapTypeRef);
					    String vrs = (String) data.get("verificationResult");
					    vr = jsonMapper.readValue(vrs, VerificationResult.class);
					    String content = (String) data.get("content");
					    sdMeta = new SelfDescriptionMetadata(new ContentAccessorDirect(content), vr); 
					}
					sdStore.storeSelfDescription(sdMeta, vr);
			    } catch (JsonProcessingException ex) {
			    	log.warn("onMessage.error", ex);
			    }
				break;
			case UPDATE:
				sdStore.changeLifeCycleStatus(hash, SelfDescriptionStatus.valueOf((String) params.get("status")));
				break;
			case DELETE:
				sdStore.deleteSelfDescription(hash);
				break;
		}
	}
	
    protected abstract void subscribe() throws Exception;
	
}
