package eu.xfsc.fc.server.service;

/*-
 * ---license-start
 * fc-service-server
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

import eu.xfsc.fc.api.generated.model.AssetEnrichmentResponse;
import eu.xfsc.fc.core.pojo.AssetMetadata;

/**
 * Return type for {@link AssetUploadService#processUpload}. Discriminates between a new asset
 * creation and an enrichment of an existing non-RDF asset with RDF metadata.
 */
public sealed interface UploadResult permits UploadResult.AssetCreated, UploadResult.AssetEnriched {

    record AssetCreated(AssetMetadata metadata) implements UploadResult {}

    record AssetEnriched(AssetEnrichmentResponse response) implements UploadResult {}
}
