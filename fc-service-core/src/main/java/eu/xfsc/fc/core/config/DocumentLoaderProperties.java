package eu.xfsc.fc.core.config;

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

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "federated-catalogue.verification.doc-loader", ignoreInvalidFields = true)
public class DocumentLoaderProperties {

    private Map<String, String> additionalContext = new LinkedHashMap<>();
    private int cacheSize;
    private Duration cacheTimeout;
    private boolean enableFile;
    private boolean enableHttp;
    private boolean enableLocalCache;
        
}
