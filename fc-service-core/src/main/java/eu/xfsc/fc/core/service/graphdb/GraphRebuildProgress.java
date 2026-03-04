package eu.xfsc.fc.core.service.graphdb;

import java.util.concurrent.atomic.AtomicLong;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * Tracks the progress of a graph rebuild operation.
 */
@Getter
public class GraphRebuildProgress {
  private volatile long total;
  @Getter(AccessLevel.NONE)
  private final AtomicLong processed = new AtomicLong(0);
  @Getter(AccessLevel.NONE)
  private final AtomicLong errors = new AtomicLong(0);
  private final long startTimeMs;
  private volatile boolean complete;
  private volatile boolean failed;
  private volatile String errorMessage;

  /**
   * Creates a new progress tracker for a rebuild with the given total SD count.
   *
   * @param total the total number of SDs to process
   */
  public GraphRebuildProgress(long total) {
    this.total = total;
    this.startTimeMs = System.currentTimeMillis();
  }

  /**
   * Creates an idle progress indicating no rebuild is in progress.
   *
   * @return an idle GraphRebuildProgress
   */
  public static GraphRebuildProgress idle() {
    GraphRebuildProgress idle = new GraphRebuildProgress(0);
    idle.complete = true;
    return idle;
  }

  /**
   * Sets the total SD count. Used when the count is determined asynchronously.
   *
   * @param total the total number of SDs to process
   */
  void setTotal(long total) {
    this.total = total;
  }

  /**
   * Returns the number of SDs processed so far.
   *
   * @return the processed count
   */
  public long getProcessedCount() {
    return processed.get();
  }

  /**
   * Returns the number of SDs that failed during processing.
   *
   * @return the error count
   */
  public long getErrorCount() {
    return errors.get();
  }

  /**
   * Increments the processed count by one.
   */
  public void incrementProcessed() {
    processed.incrementAndGet();
  }

  /**
   * Increments the error count by one.
   */
  public void incrementErrors() {
    errors.incrementAndGet();
  }

  /**
   * Marks the rebuild as complete.
   */
  public void markComplete() {
    this.complete = true;
  }

  /**
   * Marks the rebuild as failed with the given error message.
   *
   * @param message the error message
   */
  public void markFailed(String message) {
    this.failed = true;
    this.errorMessage = message;
    this.complete = true;
  }

  /**
   * Returns the completion percentage (0-100).
   *
   * @return the percentage complete
   */
  public int getPercentComplete() {
    if (total == 0) {
      return complete ? 100 : 0;
    }
    return (int) (processed.get() * 100 / total);
  }

  /**
   * Returns the elapsed duration in milliseconds since the rebuild started.
   *
   * @return duration in milliseconds
   */
  public long getDurationMs() {
    return System.currentTimeMillis() - startTimeMs;
  }
}