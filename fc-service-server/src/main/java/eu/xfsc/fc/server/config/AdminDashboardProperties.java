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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the Admin Dashboard service.
 * Bound from the {@code admin.dashboard} prefix in application configuration.
 */
@ConfigurationProperties(prefix = "admin.dashboard")
public record AdminDashboardProperties(
    String keycloakAuthServerUrl,
    String keycloakRealm,
    String keycloakAdminConsoleUrl,
    String fileStorePath) {}
