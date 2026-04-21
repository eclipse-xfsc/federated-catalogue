package eu.xfsc.fc.core.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import eu.xfsc.fc.core.dao.assets.AssetDao;
import eu.xfsc.fc.core.dao.assets.AssetRepository;
import eu.xfsc.fc.core.service.assetstore.AssetStore;
import eu.xfsc.fc.core.service.assetstore.AssetStoreImpl;
import eu.xfsc.fc.core.service.assetstore.IriGenerator;
import eu.xfsc.fc.core.service.assetstore.PublishingAssetStore;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.graphdb.GraphStore;
import eu.xfsc.fc.core.service.pubsub.AssetPublisher;
import eu.xfsc.fc.core.config.ProtectedNamespaceProperties;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class AssetStoreConfig {

  @Value("${publisher.impl}")
  private String pubImpl;

  @Bean
  public AssetStore assetStorePublisher(AssetDao dao, GraphStore graphDb,
      @Qualifier("assetFileStore") FileStore fileStore,
      IriGenerator iriGenerator, AssetRepository assetRepository,
      ProtectedNamespaceProperties namespaceProperties,
      AssetPublisher assetPublisher) {
    AssetStore assetStore;
    if ("none".equals(pubImpl)) {
      assetStore = new AssetStoreImpl(dao, graphDb, fileStore, iriGenerator, assetRepository, namespaceProperties);
    } else {
      assetStore = new PublishingAssetStore(dao, graphDb, fileStore, iriGenerator, assetRepository,
          namespaceProperties, assetPublisher);
    }
    log.debug("getAssetStore; returning {} for impl {}", assetStore, pubImpl);
    return assetStore;
  }

}
