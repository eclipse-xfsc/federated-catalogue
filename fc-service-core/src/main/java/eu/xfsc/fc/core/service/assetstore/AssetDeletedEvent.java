package eu.xfsc.fc.core.service.assetstore;

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
 * Published by {@link AssetStoreImpl} when an asset row is successfully deleted.
 * Listeners should use {@code @TransactionalEventListener(phase = BEFORE_COMMIT)}
 * so cleanup runs inside the same transaction as the asset deletion.
 *
 * @param assetId the subject IRI of the deleted asset
 */
public record AssetDeletedEvent(String assetId) {
}
