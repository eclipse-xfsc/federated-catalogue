package eu.xfsc.fc.server;

//import javax.transaction.Transactional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//import org.springframework.transaction.PlatformTransactionManager;
//import org.springframework.transaction.TransactionStatus;
//import org.springframework.transaction.support.DefaultTransactionDefinition;

import eu.xfsc.fc.core.service.schemastore.SchemaStore;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class CatalogueServerScheduler {
    
    @Autowired
    private AssetStore assetStorePublisher;
    
    @Autowired
    private SchemaStore smStore;
    
    /**
     * Scheduler for invalidating expired assets in store.
     */
    @Scheduled(cron = "${scheduler.asset.cron.expression}")
    public void scheduleAssetInvalidationTask() {
      log.debug("scheduleAssetInvalidationTask.enter; Launched scheduler to invalidate expired assets in store.");
      int numberOfExpiredAssets = assetStorePublisher.invalidateExpiredAssets();
      log.debug("scheduleAssetInvalidationTask.exit; {} expired assets were found and invalidated.", numberOfExpiredAssets);
    }
    
    @Scheduled(initialDelayString = "${scheduler.schema.init-delay}", fixedDelay = Long.MAX_VALUE) 
    public void scheduleSchemaInitialization() {
      log.debug("scheduleSchemaInitialization.enter; Launching default schemas initialization.");
      int numberOfSchemas = smStore.initializeDefaultSchemas();
      log.debug("scheduleSchemaInitialization.exit; {} default schemas initialized.", numberOfSchemas);
    }    

}
