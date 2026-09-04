package eu.xfsc.fc.core.dao.trustframework;

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

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for per-bundle external-identifier overrides. Absence of a row means
 * "no override — fall back to the bundle's {@code framework.yaml} values". A present
 * row with a NULL field also falls back for that single field.
 */
public interface TrustFrameworkBundleConfigRepository
    extends JpaRepository<TrustFrameworkBundleConfig, String> {
}
