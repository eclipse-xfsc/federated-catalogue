package eu.xfsc.fc.core.dao.validation;

/*-
 * ---license-start
 * fc-service-core
 * ---
 * Copyright (c) 2022 - 2026 Contributors to the Eclipse Foundation
 * ---
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Apache License, Version 2.0 which is available at
 * https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: Apache-2.0
 * ---license-end
 */

/**
 * Graph DB synchronisation lifecycle for a {@link ValidationResult}.
 *
 * <p>The status is set atomically when {@code ValidationResultStoreImpl.store()} or
 * {@code ValidationResultStoreImpl.storeWithoutGraphSync()} commits. PENDING is not a valid
 * committed state — a row leaves the write path as exactly one of {@code SYNCED}, {@code FAILED},
 * or {@code EXCLUDED}.</p>
 */
public enum GraphSyncStatus {

  /** The result was successfully written to the graph DB as {@code fcmeta:} triples. */
  SYNCED,

  /**
   * The graph write failed. The stored validation result row is the source of truth.
   * FAILED rows require manual intervention; no automatic retry is performed.
   */
  FAILED,

  /**
   * The result was deliberately never projected to the graph, because the record is not itself
   * a claim about an asset (e.g. an audit entry for a compliance-check attempt that could not
   * reach the trust service). Distinct from {@code FAILED}: this is an intentional, permanent
   * terminal state, not an error to retry. Graph rebuild must skip these rows — resurrecting
   * them as triples would recreate the exact ambiguity this state exists to avoid.
   */
  EXCLUDED
}
