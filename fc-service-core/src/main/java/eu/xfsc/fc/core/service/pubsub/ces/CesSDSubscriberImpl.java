package eu.xfsc.fc.core.service.pubsub.ces;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;

import com.fasterxml.jackson.core.JsonProcessingException;

import eu.xfsc.fc.core.dao.CesTrackerDao;
import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.exception.VerificationException;
import eu.xfsc.fc.core.service.pubsub.BaseSDSubscriber;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CesSDSubscriberImpl extends BaseSDSubscriber {

    @Value("${subscriber.url}")
    private String subUrl;
    @Value("${subscriber.page-size:20}")
    private int pageSize;    
    @Value("${subscriber.event-type:#{null}}")
    private String eventType;
    @Value("${subscriber.fixed-rate:30000}")
    private Duration fixedRate;
    
	@Autowired
	private CesTrackerDao ctDao;
    @Autowired
    private TaskScheduler scheduler;
    @Autowired
    private CesSDProcessor ceProcessor;
    private CesRestClient cesClient;
    private ExecutorService ceExecutor;
    
    private int total = 0;
    private String lastId = null;
    
	@Override
	protected void subscribe() throws Exception {
		log.debug("subscribe");
		cesClient = new CesRestClient(this.jsonMapper, subUrl);
		ceExecutor = Executors.newFixedThreadPool(pageSize); // .newVirtualThreadPerTaskExecutor();
		scheduler.scheduleAtFixedRate(() -> {
			if (lastId == null) {
				CesTracking latest = ctDao.selectLatest();
				lastId = latest == null ? null : latest.getCesId();
			}
			List<Map<String, Object>> events = cesClient.getCredentials(lastId, 0, pageSize, eventType);
			log.debug("scheduled-run; got events: {}, lastId: {}", events.size(), lastId);
			String id;
			int cnt = 0;
			for (Map<String, Object> event: events) {
				id = (String) event.get("id");
				log.debug("scheduled-run; processing event with id: {}", id);
				onMessage(event);
				total++;
				cnt++;
				lastId = id;
			}			
			log.debug("scheduled-run; processed events: {}, lastId: {}, total: {}", cnt, lastId, total);
		}, Instant.now().plus(fixedRate), fixedRate);
	}
	
	@Override
	public void onMessage(Map<String, Object> event) {
		CesTracking ctr = new CesTracking((String) event.get("id"), null, Instant.now(), 0, null, null);
		String source = (String) event.get("source");
		if (!instance.equals(source)) {
			ceExecutor.execute(() -> {
				Thread th = Thread.currentThread();
				if (th.getName() == null) {
					th.setName("ceProcessor-" + th.getId()); // .threadId());
				}
				log.debug("onMessage; processing event: {}", ctr.getCesId());
				
				try {
					ctr.setEvent(jsonMapper.writeValueAsString(event));
				} catch (JsonProcessingException ex) {
			    	log.info("processCesEvent.error: {}", ex.getMessage());
			    	ctr.setError(ex.getMessage());
					ctDao.insert(ctr);
				}
				
				int cnt = 0;
				if (ctr.getError() == null) {
					try {
					    Map<String, Object> data = (Map<String, Object>) event.get("data");
						cnt = ceProcessor.processCesEvent(ctr, data);
				    } catch (VerificationException | ConflictException ex) {
				    	log.info("processCesEvent.error: {}", ex.getMessage());
				    	ctr.setError(ex.getMessage());
						ctDao.insert(ctr);
				    }
				}
				log.debug("onMessage; processed event: {}, creds: {}", ctr.getCesId(), cnt);
			});
		}
	}
	
}

