package eu.xfsc.fc.core.service.trustframework;

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
 * Carries the resolved trust-framework base class for a credential subject type.
 * Use {@link #UNKNOWN} when no framework claims the type.
 */
public record ResolvedBaseClass(String frameworkProfileId, String baseClass) {

  public static final ResolvedBaseClass UNKNOWN = new ResolvedBaseClass("", "");

  /**
   * Returns {@code true} when this instance represents a known resolved base class.
   */
  public boolean isResolved() {
    return !this.equals(UNKNOWN);
  }
}
