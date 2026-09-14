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

/**
 * Registry that maps client-type keys to {@link TrustFrameworkClient} implementations.
 *
 * <p>Callers resolve the correct client by passing the {@code clientType} from a
 * {@link TrustFrameworkProfileConfig} to {@link #resolve(String)}.
 */
public interface TrustFrameworkClientRegistry {

  /**
   * Returns the {@link TrustFrameworkClient} registered for the given client-type key.
   *
   * @param clientType the client-type key to look up
   * @return the matching client implementation; never {@code null}
   * @throws IllegalArgumentException when no client is registered for the given type
   */
  TrustFrameworkClient resolve(String clientType);
}
