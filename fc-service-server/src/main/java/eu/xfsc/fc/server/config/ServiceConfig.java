package eu.xfsc.fc.server.config;

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

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import eu.xfsc.fc.core.config.CoreConfig;
//import eu.xfsc.fc.graphdb.config.GraphDbConfig;

/**
 * Federated Catalogue core service configuration.
 */
@Configuration
@Import(value = {CoreConfig.class})
@ComponentScan(basePackages = {"eu.xfsc.fc.graphdb.service", "eu.xfsc.fc.graphdb.config"})
public class ServiceConfig {

}
