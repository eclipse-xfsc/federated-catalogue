package eu.xfsc.fc.core.config;

import java.io.File;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.google.common.io.Files;

import eu.xfsc.fc.core.service.filestore.CacheFileStore;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.service.filestore.FileStoreImpl;

@Configuration
public class FileStoreConfig {

  @Value("${federated-catalogue.scope}")
  private String scope;

  @Value("${federated-catalogue.file-store.schema.location}")
  private String schemaFilesLocation;

  /**
   * Location for non-RDF asset storage. Set {@code federated-catalogue.file-store.asset.location}
   * in production ({@code scope=runtime}). When null or in test scope, a temporary directory is used.
   */
  @Value("${federated-catalogue.file-store.asset.location:#{null}}")
  private String assetFilesLocation;

  @Value("${federated-catalogue.file-store.context-cache.location}")
  private String contextCacheFilesLocation;

  @Value("${federated-catalogue.file-store.cached}")
  private boolean cached;
  
  @Value("${federated-catalogue.file-store.cache-size:128}")
  private int cacheSize;

  private final File TEMPORARY_FOLDER_FILE = Files.createTempDir();  

  @Bean
  public FileStore schemaFileStore() {
    return createFileStore(schemaFilesLocation, "testSchemaFiles");
  }

  /**
   * FileStore for non-RDF asset content. Uses {@code federated-catalogue.file-store.asset.location}
   * in production. Falls back to a temporary directory when the location is null or in test scope.
   */
  @Bean
  public FileStore assetFileStore() {
    return createFileStore(assetFilesLocation, "testAssetFiles");
  }

  @Bean
  public FileStore contextCacheFileStore() {
    return createFileStore(contextCacheFilesLocation, "testContextCache");
  }

  private FileStore createFileStore(String location, String tempFolderName) {
    if (cached) {
      return new CacheFileStore(cacheSize);
    }
    if (scope.equals("runtime")) {
      if (location == null) {
        throw new IllegalStateException("File store location must be configured for scope=runtime (missing property for '" + tempFolderName + "')");
      }
      return new FileStoreImpl(location);
    }
    String tmpPath = TEMPORARY_FOLDER_FILE.getAbsolutePath() + File.separator + tempFolderName;
    return new FileStoreImpl(tmpPath);
  }
}
