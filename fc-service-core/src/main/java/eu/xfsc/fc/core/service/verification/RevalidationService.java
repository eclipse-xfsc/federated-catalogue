package eu.xfsc.fc.core.service.verification;

/**
 * Interface for a revalidation service.
 */
public interface RevalidationService {

  /**
   * Sets up the RevalidationService so it is ready for work. This does not
   * actually start the revalidation process yet.
   */
  void setup();

  /**
   * Starts the revalidation process when it is not started yet, restarts the
   * process when it is already running.
   */
  void startValidating();

  /**
   * Check if the revalidator is active.
   *
   * @return true if the Revalidator is actively revalidating SDs
   */
  boolean isWorking();

  /**
   * Clean up the revalidationService. If there are running tasks they will
   * complete, but any queued tasks will not.
   */
  void cleanup();

}
