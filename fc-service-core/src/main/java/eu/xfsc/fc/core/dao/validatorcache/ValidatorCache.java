package eu.xfsc.fc.core.dao.validatorcache;

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

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "validatorcache")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ValidatorCache {

  @Id
  @Column(name = "diduri", columnDefinition = "TEXT")
  private String didUri;

  @Column(name = "publickey", nullable = false, columnDefinition = "TEXT")
  private String publicKey;

  @Column(name = "expirationtime")
  private Instant expirationTime;
}
