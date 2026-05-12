package eu.xfsc.fc.core.service.pubsub;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import eu.xfsc.fc.api.generated.model.AssetResult;
import eu.xfsc.fc.api.generated.model.AssetStatus;
import eu.xfsc.fc.client.AssetClient;
import eu.xfsc.fc.core.pojo.AssetMetadata;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.pojo.CredentialVerificationResult;
import eu.xfsc.fc.core.service.pubsub.AssetPublisher.AssetEvent;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.verification.VerificationService;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class BaseAssetSubscriber implements AssetSubscriber {

	protected static final TypeReference<HashMap<String, Object>> mapTypeRef = new TypeReference<HashMap<String, Object>>() {};
		
    @Value("${subscriber.instance}")
    protected String instance;
    @Autowired
	protected AssetStore assetStore;
    @Autowired 
	protected VerificationService verificationService;
    @Autowired 
	protected ObjectMapper jsonMapper;

	protected Map<String, AssetClient> assetClients = new HashMap<>();
    
    @PostConstruct
    public void init() throws Exception {
    	subscribe();
    }
	
	@Override
	public void onMessage(Map<String, Object> params) {
		log.debug("onMessage.enter; got params: {}", params);
		AssetEvent event = AssetEvent.valueOf((String) params.get("event"));
		String hash = (String) params.get("hash");
		switch (event) {
			case ADD:
				try {
					CredentialVerificationResult vr;
					AssetMetadata assetMeta;
					String dataStr = (String) params.get("data");
					if (dataStr == null) {
						// get it by hash from other instance, then register locally.. may be do it in separate working queue?
						String source = (String) params.get("source");
						AssetClient assetClient = assetClients.computeIfAbsent(source, src -> new AssetClient(src, (String) null));
						AssetResult assetResult = assetClient.getAssetByHash(hash, false, true);
						ContentAccessor content = new ContentAccessorDirect(assetResult.getContent());
						// how to get proper VR class?
                      vr = verificationService.verifyCredential(content);
					    assetMeta = new AssetMetadata(vr.getId(), vr.getIssuer(), vr.getValidators(), content);
					} else {
						Map<String, Object> data = jsonMapper.readValue((String) params.get("data"), mapTypeRef);
					    String vrs = (String) data.get("verificationResult");
					    vr = jsonMapper.readValue(vrs, CredentialVerificationResult.class);
					    String content = (String) data.get("content");
					    assetMeta = new AssetMetadata(new ContentAccessorDirect(content), vr);
					}
					assetStore.storeCredential(assetMeta, vr);
			    } catch (JsonProcessingException ex) {
			    	log.warn("onMessage.error", ex);
			    }
				break;
			case UPDATE:
				assetStore.changeLifeCycleStatus(hash, AssetStatus.valueOf((String) params.get("status")));
				break;
			case DELETE:
				assetStore.deleteAsset(hash);
				break;
		}
	}
	
    protected abstract void subscribe() throws Exception;
	
}
