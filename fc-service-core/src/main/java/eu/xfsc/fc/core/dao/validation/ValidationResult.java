package eu.xfsc.fc.core.dao.validation;

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
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Persisted record of a single validation run.
 *
 * <p>{@code contentHash} is a SHA-256 hex digest over the canonical JSON of
 * {@code assetIds}, {@code validatorIds}, {@code validatorType}, {@code conforms},
 * {@code validatedAt}, and {@code failureCategory} (when non-null). Allows tamper
 * detection without a public endpoint.</p>
 *
 * <p>{@code graphSyncStatus} tracks whether this result has been written to the
 * graph DB as {@code fcmeta:} triples. Best-effort write: {@code FAILED} rows require
 * manual intervention; no background retry is performed.</p>
 */
@Entity
@Table(name = "validation_result")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class ValidationResult {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "validation_result_seq")
  @SequenceGenerator(name = "validation_result_seq", sequenceName = "validation_result_seq",
      allocationSize = 50)
  private Long id;

  /**
   * Asset subject IRI(s) that were validated. Single entry for single-asset validation;
   * multiple entries for multi-asset SHACL.
   */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "asset_ids", nullable = false)
  private String[] assetIds;

  /**
   * Validator IDs used in this validation run (schema IDs or trust framework IDs).
   * Array supports multi-schema and multi-framework validation.
   */
  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "validator_ids", nullable = false)
  private String[] validatorIds;

  /**
   * Validator type — distinguishes schema validation ({@code SHACL}, {@code JSON_SCHEMA},
   * {@code XML_SCHEMA}) from external trust framework checks ({@code TRUST_FRAMEWORK}).
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "validator_type", length = 64, nullable = false)
  private ValidatorType validatorType;

  /** True if validation passed; false if violations were found. */
  @Column(name = "conforms", nullable = false)
  private boolean conforms;

  /** Timestamp of the validation run (from the caller, not DB insertion time). */
  @Column(name = "validated_at", nullable = false)
  private Instant validatedAt;

  /**
   * Serialised validation report. Null for passing validations.
   * For SHACL: Turtle report. For JSON Schema: violation messages. For XML: error message.
   */
  @Column(name = "report", columnDefinition = "text")
  private String report;

  /**
   * Failure classification for a trust-framework check that could not produce a verdict
   * (e.g. {@code SERVICE_UNREACHABLE}, {@code SERVICE_TIMEOUT}, {@code UNVERIFIABLE_ATTESTATION}).
   * Null for schema validations and for trust-framework checks that did produce a verdict.
   * Stored as plain text, not a JPA enum — the value's vocabulary is owned by the
   * trust-framework compliance service, not by this generic store.
   */
  @Column(name = "failure_category", length = 64)
  private String failureCategory;

  /**
   * SHA-256 hex digest over canonical JSON of: assetIds, validatorIds, validatorType,
   * conforms, validatedAt, and failureCategory (when non-null). Allows offline tamper detection.
   */
  @Column(name = "content_hash", length = 64, nullable = false)
  private String contentHash;

  /**
   * Graph DB sync lifecycle: SYNCED or FAILED (set atomically when store() commits).
   * Best-effort write: FAILED rows require manual intervention; no retry is performed.
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "graph_sync_status", length = 16)
  private GraphSyncStatus graphSyncStatus;

  @Setter(AccessLevel.NONE)
  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  /** True when the asset was updated or revoked after this result was produced. */
  @Column(name = "outdated", nullable = false)
  private boolean outdated;

  /** Why this result was marked outdated; null when {@code outdated} is false. */
  @Enumerated(EnumType.STRING)
  @Column(name = "outdated_reason", length = 50)
  private OutdatedReason outdatedReason;

}
