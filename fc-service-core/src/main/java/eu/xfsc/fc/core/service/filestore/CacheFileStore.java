package eu.xfsc.fc.core.service.filestore;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import org.apache.commons.io.FileExistsException;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import eu.xfsc.fc.core.exception.ConflictException;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CacheFileStore implements FileStore {

    private Cache<String, String> dataCache;
	
    public CacheFileStore(int cacheSize) {
        dataCache = Caffeine.newBuilder().initialCapacity(cacheSize).build();
        log.info("<init>. initialized cached store with size: {}", cacheSize);
    }
    
	@Override
	public void storeFile(String hash, ContentAccessor content) throws IOException {
		try {
		  dataCache.asMap().merge(hash, content.getContentAsString(), (k, v) -> {
			throw new ConflictException("A file for the hash " + hash + " already exists.");
		  });
		} catch (ConflictException ex) {
			throw new FileExistsException(ex.getMessage());
		}
	}
	
	@Override
	public void replaceFile(String hash, ContentAccessor content) throws IOException {
		dataCache.put(hash, content.getContentAsString());
	}

	@Override
	public ContentAccessor readFile(String hash) throws IOException {
		String content = dataCache.getIfPresent(hash);
		if (content == null) {
			throw new FileNotFoundException("A file for the hash " + hash + " does not exist.");
		}
		return new ContentAccessorDirect(content);
	}

	@Override
	public void deleteFile(String hash) throws IOException {
		if (dataCache.asMap().remove(hash) == null) {
			throw new FileNotFoundException("A file for the hash " + hash + " does not exist.");
		}
	}

	@Override
	public Iterable<File> getFileIterable() {
		// not used in runtime, so not required
		return null;
	}

	@Override
	public void clearStorage() throws IOException {
		dataCache.invalidateAll();
	}

}
