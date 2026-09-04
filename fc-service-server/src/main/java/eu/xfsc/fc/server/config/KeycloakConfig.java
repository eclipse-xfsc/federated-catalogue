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

import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Defines Keycloak client configurations.
 */
@Configuration
public class KeycloakConfig {
  @Value("${keycloak.auth-server-url}")
  private String serverUrl;
  @Value("${keycloak.realm}")
  private String realm;
  @Value("${keycloak.resource}")
  private String clientId;
  @Value("${keycloak.credentials.secret}")
  private String clientSecret;

  /**
   * The Keycloak Client bean config.
   */
  @Bean
  public Keycloak getKeycloakClient() {
    return KeycloakBuilder.builder()
      .serverUrl(serverUrl).realm(realm).grantType(OAuth2Constants.CLIENT_CREDENTIALS)
      .clientId(clientId).clientSecret(clientSecret)
      .resteasyClient(new ResteasyClientBuilderImpl().connectionPoolSize(10).build())
      .build();
  }
}
