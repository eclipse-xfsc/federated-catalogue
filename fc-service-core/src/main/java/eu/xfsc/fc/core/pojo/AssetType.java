package eu.xfsc.fc.core.pojo;

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
 * Intrinsic type of asset in a linked MR–HR pair.
 */
public enum AssetType {
  /** Machine-readable asset: the primary RDF or structured representation. */
  MACHINE_READABLE,
  /** Human-readable asset: a document (PDF, HTML, DOCX, plain text) linked from a machine-readable asset. */
  HUMAN_READABLE
}
