package eu.xfsc.fc.core.service.verification;

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
   * @return true if the Revalidator is actively revalidating assets
   */
  boolean isWorking();

  /**
   * Clean up the revalidationService. If there are running tasks they will
   * complete, but any queued tasks will not.
   */
  void cleanup();

}
