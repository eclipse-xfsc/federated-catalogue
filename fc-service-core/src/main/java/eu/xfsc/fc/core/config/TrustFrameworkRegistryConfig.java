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

import eu.xfsc.fc.core.service.trustframework.TrustFrameworkBundleLoader;
import eu.xfsc.fc.core.service.trustframework.TrustFrameworkRegistry;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes a {@link TrustFrameworkRegistry} bean populated from classpath bundles at boot,
 * with optional filesystem overrides applied on top.
 */
@Configuration
public class TrustFrameworkRegistryConfig {

  private final String overridePath;

  /**
   * Constructs the configuration with an optional filesystem override path for trust-framework bundles.
   *
   * @param overridePath path injected from {@code federated-catalogue.trust-frameworks.override-path};
   *                     blank means no filesystem override
   */
  public TrustFrameworkRegistryConfig(
      @Value("${federated-catalogue.trust-frameworks.override-path:}") String overridePath) {
    this.overridePath = overridePath;
  }

  @Bean
  public TrustFrameworkRegistry trustFrameworkRegistry() throws IOException {
    return new TrustFrameworkRegistry(new TrustFrameworkBundleLoader(overridePath).load());
  }
}
