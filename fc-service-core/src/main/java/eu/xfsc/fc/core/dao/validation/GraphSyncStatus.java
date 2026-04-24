package eu.xfsc.fc.core.dao.validation;

/**
 * Graph DB synchronisation lifecycle for a {@link ValidationResult}.
 *
 * <p>The status is set atomically when {@code ValidationResultStoreImpl.store()}
 * commits. PENDING is not a valid committed state — the graph write either
 * succeeds ({@code SYNCED}) or fails ({@code FAILED}) within the same transaction.</p>
 */
public enum GraphSyncStatus {

  /** The result was successfully written to the graph DB as {@code fcmeta:} triples. */
  SYNCED,

  /**
   * The graph DB write failed. The PostgreSQL row is the source of truth.
   * FAILED rows require manual intervention; no automatic retry is performed.
   */
  FAILED
}
