package eu.xfsc.fc.core.service.provenance;

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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import eu.xfsc.fc.core.service.assetstore.AssetDeletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvenanceCleanupListenerTest {

  @Mock
  private ProvenanceService provenanceService;

  @InjectMocks
  private ProvenanceCleanupListener listener;

  @Test
  void onAssetDeleted_validEvent_callsDeleteByAssetId() {
    AssetDeletedEvent event = new AssetDeletedEvent("https://example.org/asset/deleted-1");

    listener.onAssetDeleted(event);

    verify(provenanceService).deleteByAssetId("https://example.org/asset/deleted-1");
  }

  @Test
  void onAssetDeleted_serviceThrows_propagatesException() {
    AssetDeletedEvent event = new AssetDeletedEvent("https://example.org/asset/deleted-2");
    doThrow(new RuntimeException("db unavailable"))
        .when(provenanceService).deleteByAssetId("https://example.org/asset/deleted-2");

    assertThrows(RuntimeException.class, () -> listener.onAssetDeleted(event));
  }
}
