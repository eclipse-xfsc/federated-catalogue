package eu.xfsc.fc.core.service.filestore;

import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorFile;
import eu.xfsc.fc.core.util.HashUtils;

import static eu.xfsc.fc.core.util.HashUtils.HASH_PATTERN;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.FileNotFoundException;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileExistsException;

/**
 * Stores and retrieves files identified by a hash.
 */
@Slf4j
public class FileStoreImpl implements FileStore {

  @Value("${federated-catalogue.scope}")
  private String scope;

  @Value("${datastore.file-path}")
  private String basePathName;

  /**
   * The depth of the file tree.
   */
  @Value("${federated-catalogue.file-store.directory-tree-depth:1}")
  private int directoryTreeDepth;
  /**
   * The length of each directory in the file tree. The number of directories in
   * each level will be 16^x.
   */
  @Value("${federated-catalogue.file-store.directory-name-length:2}")
  private int directoryNameLength;

  private final String storeName;

  public FileStoreImpl(String storeName) {
    this.storeName = storeName;
    log.info("<init>; initialized file store with name: {}", storeName);
  }

  private final Map<String, Path> storePaths = new HashMap<>();

  private Path getPathForStore(String store) {
    Path path;  
    if (scope.equals("test")) {
      path = FileSystems.getDefault().getPath(store);
    } else {
      path = storePaths.computeIfAbsent(store, n -> FileSystems.getDefault().getPath(basePathName, n));
    }
    log.trace("getPathForStore.exit; returning {} for store {}", path, store);
    return path;
  }

  private File getFileForStoreHash(String hash) {
    Path storePath = getPathForStore(storeName);
    Path storeSubPath = storePath;
    int start = 0;
    for (int i = 0; i < directoryTreeDepth; i++) {
      int end = start + directoryNameLength;
      storeSubPath = storeSubPath.resolve(hash.substring(start, end));
      start = end;
    }
    Path filePath = storeSubPath.resolve(hash);
    return filePath.toFile();
  }

  private String validateFileName(String filename) {
    if (HASH_PATTERN.matcher(filename).matches()) {
      return filename;
    }
    log.debug("validateFileName; Filename is not a hash: {}", filename);
    return HashUtils.calculateSha256AsHex(filename);
  }

  @Override
  public void storeFile(String hash, ContentAccessor content) throws IOException {
    saveFile(hash, content, false);
  }

  @Override
  public void replaceFile(String hash, ContentAccessor content) throws IOException {
    saveFile(hash, content, true);
  }

  private void saveFile(String hash, ContentAccessor content, boolean overwrite) throws IOException {
    File file = getFileForStoreHash(validateFileName(hash));
    if (file.exists() && !overwrite) {
      throw new FileExistsException("A file for the hash " + hash + " already exists.");
    }
    try ( FileOutputStream os = FileUtils.openOutputStream(file)) {
      IOUtils.copy(content.getContentAsStream(), os);
    }
  }

  @Override
  public ContentAccessor readFile(String hash) throws IOException {
    File file = getFileForStoreHash(validateFileName(hash));
    if (!file.exists()) {
      throw new FileNotFoundException("A file for the hash " + hash + " does not exist.");
    }
    return new ContentAccessorFile(file);
  }

  @Override
  public void deleteFile(String hash) throws IOException {
    File file = getFileForStoreHash(validateFileName(hash));
    if (!file.exists()) {
      throw new FileNotFoundException("A file for the hash " + hash + " does not exist.");
    }
    file.delete();
  }

  @Override
  public Iterable<File> getFileIterable() {
    return () -> {
      return new HashFileIterator(this, storeName);
    };
  }

  @Override
  public void clearStorage() throws IOException {
    Path storePath = getPathForStore(storeName);
    FileUtils.deleteDirectory(storePath.toFile());
  }

  public static class HashFileIterator implements Iterator<File> {

    private final Deque<Iterator<File>> fileIterators = new ArrayDeque<>();
    private File next;

    public HashFileIterator(FileStoreImpl parent, String storeName) {
      Path storePath = parent.getPathForStore(storeName);
      File[] subFileArray = storePath.toFile().listFiles();
      if (subFileArray != null) {
        fileIterators.push(Arrays.asList(subFileArray).iterator());
        next = traverseIterator();
      }
    }

    /**
     * Traverses the fileIterators, descending into directories until it finds a
     * regular file.
     *
     * @return The first file found while traversing the directories, or null if
     * no more files are available.
     */
    private File traverseIterator() {
      while (!fileIterators.isEmpty()) {
        Iterator<File> head = fileIterators.peek();
        while (head.hasNext()) {
          File nextFile = head.next();
          if (nextFile.isDirectory()) {
            head = Arrays.asList(nextFile.listFiles()).iterator();
            fileIterators.push(head);
          } else {
            return nextFile;
          }
        }
        fileIterators.pop();
      }
      return null;
    }

    @Override
    public boolean hasNext() {
      return next != null;
    }

    @Override
    public File next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      File retval = next;
      next = traverseIterator();
      return retval;
    }

  }

  /**
   * The depth of the file tree.
   *
   * @return the directoryTreeDepth
   */
  public int getDirectoryTreeDepth() {
    return directoryTreeDepth;
  }

  /**
   * The depth of the file tree.
   *
   * @param directoryTreeDepth the directoryTreeDepth to set
   */
  public void setDirectoryTreeDepth(int directoryTreeDepth) {
    this.directoryTreeDepth = directoryTreeDepth;
  }

  /**
   * The length of each directory in the file tree. The number of directories in
   * each level will be 16^x.
   *
   * @return the directoryNameLength
   */
  public int getDirectoryNameLength() {
    return directoryNameLength;
  }

  /**
   * The length of each directory in the file tree. The number of directories in
   * each level will be 16^x.
   *
   * @param directoryNameLength the directoryNameLength to set
   */
  public void setDirectoryNameLength(int directoryNameLength) {
    this.directoryNameLength = directoryNameLength;
  }
}
