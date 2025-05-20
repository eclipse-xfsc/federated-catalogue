package eu.xfsc.fc.core.service.filestore;

import eu.xfsc.fc.core.config.FileStoreConfig;
import eu.xfsc.fc.core.pojo.ContentAccessor;
import eu.xfsc.fc.core.pojo.ContentAccessorDirect;
import eu.xfsc.fc.core.util.HashUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang3.mutable.MutableInt;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(properties = {"federated-catalogue.file-store.cached=false"})
@ActiveProfiles("test")
@ContextConfiguration(classes = {FileStoreConfig.class}) 
@Slf4j
public class FileStoreTest {

  @Autowired
  @Qualifier("schemaFileStore")
  private FileStore fileStore;

  private static final int TOTAL_FILE_COUNT = 1_000;
  private static final int THREAD_COUNT = 20;

  @AfterEach
  public void storageSelfCleaning() throws IOException {
    fileStore.clearStorage();
  }

  private static ContentAccessor createContent(final int idx) {
    return new ContentAccessorDirect(String.format("%016d", idx));
  }

  private void assertStoredSdFiles(final int expected) {
    final MutableInt count = new MutableInt(0);
    fileStore.getFileIterable().forEach(file -> count.increment());
    final String message = String.format("Storing %d file(s) should result in exactly %d file(s) in the store.",
        expected, expected);
    assertEquals(expected, count.intValue(), message);
  }

  private void testStoreSpeed(final int nameLength, final int treeDepth, final int threadCount, final int totalFileCount) {
    log.info("Pattern Length: {} Depth: {}", nameLength, treeDepth);
    ((FileStoreImpl) fileStore).setDirectoryNameLength(nameLength);
    ((FileStoreImpl) fileStore).setDirectoryTreeDepth(treeDepth);
    final int perThreadCount = totalFileCount / threadCount;

    long startTime = System.currentTimeMillis();
    createFiles(threadCount, perThreadCount);
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;
    log.info("Pattern {}-{} Created {} in {}ms", nameLength, treeDepth, totalFileCount, duration);

    startTime = System.currentTimeMillis();
    readFiles(threadCount, perThreadCount);
    endTime = System.currentTimeMillis();
    duration = endTime - startTime;
    log.info("Pattern {}-{} Read    {} in {}ms", nameLength, treeDepth, totalFileCount, duration);

    startTime = System.currentTimeMillis();
    assertStoredSdFiles(totalFileCount);
    endTime = System.currentTimeMillis();
    duration = endTime - startTime;
    log.info("Pattern {}-{} Counted {} in {}ms", nameLength, treeDepth, totalFileCount, duration);
  }

  @Test
  void test01FileStore() throws Exception {
    log.info("test01FileStore");

    final int nameLength = 2;
    final int treeDepth = 1;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test02FileStore() throws Exception {
    log.info("test02FileStore");
    final int nameLength = 2;
    final int treeDepth = 2;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test03FileStore() throws Exception {
    log.info("test03FileStore");
    final int nameLength = 2;
    final int treeDepth = 3;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test04FileStore() throws Exception {
    log.info("test04FileStore");
    final int nameLength = 1;
    final int treeDepth = 1;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test05FileStore() throws Exception {
    log.info("test05FileStore");
    final int nameLength = 1;
    final int treeDepth = 2;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test06FileStore() throws Exception {
    log.info("test06FileStore");
    final int nameLength = 1;
    final int treeDepth = 3;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test07FileStore() throws Exception {
    log.info("test07FileStore");
    final int nameLength = 1;
    final int treeDepth = 4;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test08FileStore() throws Exception {
    log.info("test08FileStore");
    final int nameLength = 3;
    final int treeDepth = 1;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test09FileStore() throws Exception {
    log.info("test09FileStore");
    final int nameLength = 3;
    final int treeDepth = 2;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  @Test
  void test10FileStore() throws Exception {
    log.info("test10FileStore");
    final int nameLength = 3;
    final int treeDepth = 3;

    testStoreSpeed(nameLength, treeDepth, THREAD_COUNT, TOTAL_FILE_COUNT);
  }

  private void createFiles(final int threadCount, final int perThreadCount) {
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      final int threadNr = i;
      final int indexStart = i * perThreadCount;
      final int indexEnd = i * perThreadCount + perThreadCount;
      final Thread thread = new Thread(() -> {
        log.trace("Thread {} started", threadNr);
        for (int idx = indexStart; idx < indexEnd; idx++) {
          ContentAccessor content = createContent(idx);
          String hash = HashUtils.calculateSha256AsHex(content.getContentAsString());
          try {
            fileStore.storeFile(hash, content);
          } catch (IOException ex) {
            log.warn("Failed to create file.", ex);
          }
        }
        log.trace("Thread {} done", threadNr);
      });
      threads.add(thread);
      thread.start();
    }
    threads.forEach(t -> {
      try {
        t.join();
      } catch (InterruptedException ex) {
        log.error("Failed to join Thread.");
      }
    });
  }

  private void readFiles(final int threadCount, final int perThreadCount) {
    List<Thread> threads = new ArrayList<>();
    for (int i = 0; i < threadCount; i++) {
      final int threadNr = i;
      final int indexStart = i * perThreadCount;
      final int indexEnd = i * perThreadCount + perThreadCount;
      final Thread thread = new Thread(() -> {
        log.trace("Thread {} started", threadNr);
        for (int idx = indexStart; idx < indexEnd; idx++) {
          ContentAccessor content = createContent(idx);
          String hash = HashUtils.calculateSha256AsHex(content.getContentAsString());
          try {
            ContentAccessor readFile = fileStore.readFile(hash);
            assertEquals(content, readFile);
          } catch (IOException ex) {
            log.warn("Failed to create file.", ex);
          }
        }
        log.trace("Thread {} done", threadNr);
      });
      threads.add(thread);
      thread.start();
    }
    threads.forEach(t -> {
      try {
        t.join();
      } catch (InterruptedException ex) {
        log.error("Failed to join Thread.");
      }
    });
  }

}
