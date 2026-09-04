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

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration holder for Admin Dashboard service properties.
 * Groups Keycloak, file store, and WebClient config to reduce constructor parameters.
 */
@Getter
@Component
@RequiredArgsConstructor
public class AdminDashboardConfig {

  private final WebClient webClient;
  private final AdminDashboardProperties props;

  private String keycloakIssuerUrl;
  private String keycloakAdminConsoleUrl;
  private String fileStorePath;

  @PostConstruct
  private void init() {
    keycloakIssuerUrl = props.keycloakAuthServerUrl() + "/realms/" + props.keycloakRealm();
    String override = props.keycloakAdminConsoleUrl();
    keycloakAdminConsoleUrl = (override == null || override.isBlank())
        ? props.keycloakAuthServerUrl() + "/admin/master/console/#/" + props.keycloakRealm()
        : override;
    fileStorePath = props.fileStorePath();
  }
}
