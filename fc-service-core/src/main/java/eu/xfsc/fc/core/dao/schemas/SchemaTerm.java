package eu.xfsc.fc.core.dao.schemas;

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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.envers.Audited;

@Entity
@Table(name = "schematerms")
@Audited
@Getter
@Setter
@NoArgsConstructor
public class SchemaTerm {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "schematerms_seq")
  @SequenceGenerator(name = "schematerms_seq", sequenceName = "schematerms_id_seq")
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "term", length = 256, nullable = false)
  private String term;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "schema_file_id", nullable = false)
  private SchemaFile schemaFile;
}
