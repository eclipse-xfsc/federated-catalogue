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

import eu.xfsc.fc.core.pojo.AssetType;

/**
 * Projection returned by {@link AssetStore#findLink}: the IRI of the linked asset
 * and the asset type of the queried asset (not the linked one).
 */
public record LinkedAssetRef(String linkedIri, AssetType ownType) {}
