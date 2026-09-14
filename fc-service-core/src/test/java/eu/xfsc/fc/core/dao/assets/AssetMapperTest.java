package eu.xfsc.fc.core.dao.assets;

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

import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for AssetMapper null-safety.
 */
class AssetMapperTest {

  @Test
  void toEntity_nullRecord_returnsNull() {
    assertNull(AssetMapper.toEntity(null));
  }

  @Test
  void toRecord_nullEntity_returnsNull() {
    assertNull(AssetMapper.toRecord(null));
  }
}
