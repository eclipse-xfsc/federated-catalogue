package eu.xfsc.fc.server.model;

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


import jakarta.validation.constraints.Min;

@lombok.AllArgsConstructor
@lombok.Getter
@lombok.ToString
public class GraphRebuildRequest {
    
    
    @Min(1)   
    private int chunkCount;
    @Min(0)   
    private int chunkId;
    @Min(1)   
    private int threads;
    @Min(1)   
    private int batchSize;

}
