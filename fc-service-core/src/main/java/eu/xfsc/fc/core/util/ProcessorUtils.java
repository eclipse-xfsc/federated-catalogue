package eu.xfsc.fc.core.util;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;

/**
 * Utility class for creating processor services.
 */
@Slf4j
public class ProcessorUtils {

  /**
   * Creates a new executorService with the given number of threads that all watch the given queue for jobs. Each job is
   * passed to the given consumer. Threads a named using the given name.
   *
   * @param <T> The type of input for the processors.
   * @param threadCount The number of threads to use.
   * @param queue The queue used to pass jobs to the processors.
   * @param consumer The consumer that handles the jobs.
   * @param name The name prefix for the threads.
   * @return A working ExecutorService.
   */
  public static <T> ExecutorService createProcessors(int threadCount, BlockingQueue<T> queue, Consumer<T> consumer, String name) {
    ThreadFactory factory = new BasicThreadFactory.Builder().namingPattern(name + "-%d").build();
    ExecutorService result = Executors.newFixedThreadPool(threadCount, factory);
    for (int i = 0; i < threadCount; i++) {
      result.submit(new Processor(queue, consumer, name));
    }
    return result;
  }

  /**
   * Shutdown the given processor. Any jobs on the queue are cleared. Before calling this any processes that add jobs to
   * the queue must be stopped.
   *
   * @param executorService the ExecutorService to stop.
   * @param queue The queue the ExecutorService works on.
   * @param timeout The maximum time to wait for proper shutdown.
   * @param timeUnit The unit of the timeout argument.
   */
  public static void shutdownProcessors(ExecutorService executorService, BlockingQueue<?> queue, long timeout, TimeUnit timeUnit) {
    if (executorService != null) {
      executorService.shutdown();
      queue.clear();
      executorService.shutdownNow();
      try {
        if (!executorService.awaitTermination(timeout, timeUnit)) {
          log.debug("executoreService did not terminate in time");
        }
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
      }
    }
  }

  /**
   * A processor thread that blocks on queue.take, waiting for tasks to process. Stops working when it is interrupted.
   *
   * @param <T> The type of the tasks the processor processes.
   */
  public static class Processor<T> implements Runnable {

    private final BlockingQueue<T> queue;
    private final Consumer<T> consumer;
    private final String name;

    private Processor(BlockingQueue<T> queue, Consumer<T> consumer, String name) {
      if (queue == null) {
        throw new IllegalArgumentException("queue must be non-null");
      }
      if (consumer == null) {
        throw new IllegalArgumentException("handler must be non-null");
      }
      if (name == null || name.isEmpty()) {
        this.name = getClass().getName();
      } else {
        this.name = name;
      }
      this.queue = queue;
      this.consumer = consumer;
    }

    @Override
    public void run() {
      log.debug("starting {}-Thread", name);
      while (!Thread.currentThread().isInterrupted()) {
        T event;
        try {
          event = queue.take();
          consumer.accept(event);
        } catch (InterruptedException ex) {
          log.trace("{} interrupted", name, ex);
          Thread.currentThread().interrupt();
          break;
        } catch (Exception ex) {
          log.warn("Exception while executing {}", name, ex);
        }
      }
      log.debug("exiting {}-Thread", name);
    }
  }

}
