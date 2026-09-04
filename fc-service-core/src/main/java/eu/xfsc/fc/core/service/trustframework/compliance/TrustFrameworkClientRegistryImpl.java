package eu.xfsc.fc.core.service.trustframework.compliance;

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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Spring-managed registry that maps client-type keys to {@link TrustFrameworkClient}
 * implementations. Discovers all beans implementing {@link TrustFrameworkClient} and registers
 * them by their {@link TrustFrameworkClient#clientType()} key.
 */
@Slf4j
@Service
public class TrustFrameworkClientRegistryImpl implements TrustFrameworkClientRegistry {

  private final Map<String, TrustFrameworkClient> clients;

  public TrustFrameworkClientRegistryImpl(List<TrustFrameworkClient> clientBeans) {
    this.clients = clientBeans.stream()
        .collect(Collectors.toMap(
            TrustFrameworkClient::clientType,
            c -> c,
            (a, b) -> {
              throw new IllegalStateException(
                  "Duplicate TrustFrameworkClient for type: " + a.clientType());
            }
        ));
    log.info("Registered {} trust-framework clients: {}", clients.size(), clients.keySet());
  }

  @Override
  public TrustFrameworkClient resolve(String clientType) {
    TrustFrameworkClient client = clients.get(clientType);
    if (client == null) {
      throw new IllegalArgumentException(
          "No client registered for type: " + clientType + ". Available: " + clients.keySet());
    }
    return client;
  }
}
