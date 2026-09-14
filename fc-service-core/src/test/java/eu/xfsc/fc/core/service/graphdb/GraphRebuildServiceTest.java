package eu.xfsc.fc.core.service.graphdb;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import eu.xfsc.fc.core.exception.GraphStoreDisabledException;

/**
 * Tests for {@link GraphRebuildService} and {@link GraphRebuildProgress}.
 * Tests that do not require a full Spring context.
 */
public class GraphRebuildServiceTest {

  @Test
  public void triggerRebuild_disabledStore_throwsException() {
    DummyGraphStore dummyStore = new DummyGraphStore();
    GraphRebuildService service = new GraphRebuildService(null, null, dummyStore);

    assertThrows(GraphStoreDisabledException.class,
        () -> service.triggerRebuild(1, 0, 4, 100));
  }

  @Test
  public void idle_newInstance_isComplete() {
    GraphRebuildProgress idle = GraphRebuildProgress.idle();

    assertTrue(idle.isComplete());
    assertEquals(0, idle.getTotal());
    assertEquals(100, idle.getPercentComplete());
  }

  @Test
  public void incrementProcessed_multipleCalls_tracksProgress() {
    GraphRebuildProgress status = new GraphRebuildProgress(10);
    assertEquals(10, status.getTotal());
    assertEquals(0, status.getProcessedCount());
    assertEquals(0, status.getPercentComplete());
    assertFalse(status.isComplete());

    status.incrementProcessed();
    status.incrementProcessed();
    status.incrementProcessed();
    assertEquals(3, status.getProcessedCount());
    assertEquals(30, status.getPercentComplete());

    status.markComplete();
    assertTrue(status.isComplete());
    assertFalse(status.isFailed());
  }

  @Test
  public void markFailed_withMessage_setsErrorAndComplete() {
    GraphRebuildProgress status = new GraphRebuildProgress(5);
    status.markFailed("test error");

    assertTrue(status.isFailed());
    assertTrue(status.isComplete());
    assertEquals("test error", status.getErrorMessage());
  }

  @Test
  public void incrementErrors_multipleCalls_tracksErrorCount() {
    GraphRebuildProgress status = new GraphRebuildProgress(5);
    assertEquals(0, status.getErrorCount());

    status.incrementErrors();
    status.incrementErrors();
    assertEquals(2, status.getErrorCount());
  }

  @Test
  public void getDurationMs_newInstance_returnsNonNegative() {
    GraphRebuildProgress status = new GraphRebuildProgress(1);

    assertTrue(status.getDurationMs() >= 0);
  }
}
