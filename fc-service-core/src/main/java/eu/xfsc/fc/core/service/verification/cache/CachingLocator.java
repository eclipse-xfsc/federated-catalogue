package eu.xfsc.fc.core.service.verification.cache;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.service.filestore.FileStore;
import eu.xfsc.fc.core.util.HashUtils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.jena.atlas.web.ContentType;
import org.apache.jena.atlas.web.TypedInputStream;
import org.apache.jena.riot.system.stream.Locator;
import org.apache.jena.riot.system.stream.LocatorHTTP;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A Jena Locator that caches http downloads.
 */
@Slf4j
public class CachingLocator implements Locator {

  private final static ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  
  // TODO: use caching DocumentLoader instead of this fileStore. 
  // see also https://gitlab.eclipse.org/eclipse/xfsc/cat/fc-service/-/issues/98
  private final FileStore fileStore;
  private final LocatorHTTP locatorHttp;

  public CachingLocator(FileStore fileStore) {
    this.fileStore = fileStore;
    this.locatorHttp = new LocatorHTTP();
  }

  @Override
  public TypedInputStream open(String uri) {
    log.debug("open.enter; uri: {}", uri);
    HttpDocument accessor = getAndCache(uri);
    if (accessor == null) {
      return null;
    }
    return new TypedInputStream(
        IOUtils.toInputStream(accessor.getBody(), StandardCharsets.UTF_8),
        ContentType.create(accessor.getType()),
        accessor.getTargetUri());
  }

  @Override
  public String getName() {
    return "LocatorCaching";
  }

  private HttpDocument getAndCache(String uri) {
    String hash = HashUtils.calculateSha256AsHex(uri);

	HttpDocument cached = fetchFromCache(uri, hash);
    if (cached != null) {
      return cached;
    }
    log.debug("getAndCache; Downloading {}", uri);
    TypedInputStream tis = locatorHttp.open(uri);
    if (tis == null) {
      return null;
    }
    try {
      log.debug("getAndCache; Storing content of {} as {}", uri, hash);
      final String remoteBody = IOUtils.toString(tis, StandardCharsets.UTF_8);
      HttpDocument fromRemote = new HttpDocument(tis.getContentType(), tis.getBaseURI(), remoteBody);
      storeInCache(uri, hash, fromRemote);
      return fromRemote;
    } catch (IOException ex) {
      log.error("getAndCache.error; Failed to store downloaded content for {}", uri, ex);
      return null;
    }
  }

  private HttpDocument fetchFromCache(final String uri, final String hash) {
    try {
      final ContentAccessor cachedContent = fileStore.readFile(hash);
      log.debug("fetchFromCache; Read cached version of {}", uri);
      return OBJECT_MAPPER.readValue(cachedContent.getContentAsStream(), HttpDocument.class);
    } catch (FileNotFoundException ex) {
      log.debug("fetchFromCache.error 1; No cached version found of {}", uri);
    } catch (IOException ex) {
      log.debug("fetchFromCache.error 2; Error reading cached version found of {}", uri, ex);
    }
    return null;
  }

  private void storeInCache(final String uri, final String hash, HttpDocument httpDocument) {
    try {
      log.debug("storeInCache; Storing content of {} as {}", uri, hash);
      ContentAccessorDirect content = new ContentAccessorDirect(OBJECT_MAPPER.writeValueAsString(httpDocument));
      fileStore.replaceFile(hash, content);
    } catch (IOException ex) {
      log.error("storeInCache.error; Failed to store downloaded content for {}", uri, ex);
    }
  }

  @AllArgsConstructor
  @NoArgsConstructor
  @Getter
  @Setter
  static final class HttpDocument {

    private String type;
    private String targetUri;
    private String body;
  }  
  
}
