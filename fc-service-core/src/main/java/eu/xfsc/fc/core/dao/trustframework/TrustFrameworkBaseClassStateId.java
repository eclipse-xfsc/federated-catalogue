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

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Composite primary key for {@link TrustFrameworkBaseClassState}.
 *
 * <p>{@code frameworkId} is the registry bundle profile ID (e.g. {@code gaia-x-2511});
 * {@code baseClassName} is the base-class name as declared in the bundle's {@code framework.yaml}
 * (e.g. {@code Participant}).
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class TrustFrameworkBaseClassStateId implements Serializable {

  @Column(name = "framework_id", length = 255, nullable = false)
  private String frameworkId;

  @Column(name = "base_class_name", length = 255, nullable = false)
  private String baseClassName;
}
