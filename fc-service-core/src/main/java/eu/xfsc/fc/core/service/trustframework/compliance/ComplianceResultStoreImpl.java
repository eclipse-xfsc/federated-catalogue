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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import eu.xfsc.fc.core.service.validation.ValidationResultRecord;
import eu.xfsc.fc.core.service.validation.ValidationResultStore;

import java.time.Instant;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Thin persistence wrapper that maps {@link ComplianceCheckOutcome} variants to
 * {@link ValidationResultRecord} entries with {@code validatorType=TRUST_FRAMEWORK}.
 *
 * <p>Variant-specific fields are serialised to JSON in the {@code report} column.
 * For issued attestations the raw credential JWT is stored; the issuing service is
 * identified by the JWT's standard {@code iss} claim and need not be extracted separately.
 * The report is always written; for issued attestations it carries positive evidence,
 * not just error detail. The {@link FailureCategory}, when present, is additionally passed as
 * {@code ValidationResultRecord.failureCategory()} so it lands on the entity's own sealed
 * column instead of only inside the unsealed {@code report} blob.</p>
 *
 * <p>{@link #storeFailedAttempt} covers a separate case that never produces a
 * {@link ComplianceCheckOutcome}: a check attempt where the trust service could not be reached at
 * all. It is always written with {@code conforms=false} and a {@link FailureCategory} that is
 * never {@link FailureCategory#UNVERIFIABLE_ATTESTATION}.</p>
 */
@Service
@RequiredArgsConstructor
public class ComplianceResultStoreImpl implements ComplianceResultStore {

  private static final String FIELD_FAILURE_CATEGORY = "failureCategory";
  private static final String FIELD_ATTESTATION_CREDENTIAL = "attestationCredential";
  private static final String FIELD_VERIFICATION_ERROR = "verificationError";
  private static final String FIELD_RAW_ATTESTATION = "rawAttestation";
  private static final String FIELD_FAILURE_DETAIL = "failureDetail";

  private static final int MAX_RAW_ATTESTATION_SIZE = 65_536;
  private static final String TRUNCATION_MARKER = "...[TRUNCATED]";

  private final ValidationResultStore validationResultStore;
  private final ObjectMapper objectMapper;

  @Override
  public Long store(String assetId, String frameworkProfileId, String familyId,
                    ComplianceCheckOutcome outcome) {
    String report = buildReport(outcome);
    var record = new ValidationResultRecord(
        List.of(assetId),
        List.of(frameworkProfileId, familyId),
        ValidatorType.TRUST_FRAMEWORK,
        outcome.compliant(),
        Instant.now(),
        report,
        extractFailureCategory(outcome)
    );
    return validationResultStore.store(record);
  }

  @Override
  public Page<ValidationResult> findByAssetId(String assetId, Pageable pageable) {
    return validationResultStore.getByAssetId(assetId, pageable);
  }

  @Override
  public Long storeFailedAttempt(String assetId, String frameworkProfileId, String familyId,
                                 FailureCategory category, String failureDetail) {
    String report = buildFailedAttemptReport(category, failureDetail);
    var record = new ValidationResultRecord(
        List.of(assetId),
        List.of(frameworkProfileId, familyId),
        ValidatorType.TRUST_FRAMEWORK,
        false,
        Instant.now(),
        report,
        category.name()
    );
    // Deliberately storeWithoutGraphSync, not store: "the trust service was unreachable" is not
    // a claim about the asset, so it must never appear as a triple on the federated query surface,
    // where it would be indistinguishable from a genuine non-compliant verdict.
    return validationResultStore.storeWithoutGraphSync(record);
  }

  private static String truncate(String value) {
    if (value.length() <= MAX_RAW_ATTESTATION_SIZE) {
      return value;
    }
    return value.substring(0, MAX_RAW_ATTESTATION_SIZE) + TRUNCATION_MARKER;
  }

  private static String extractFailureCategory(ComplianceCheckOutcome outcome) {
    return switch (outcome) {
      case IssuedAttestation ia -> null;
      case UnverifiableAttestation ua -> ua.failureCategory().name();
    };
  }

  private String buildReport(ComplianceCheckOutcome outcome) {
    ObjectNode node = objectMapper.createObjectNode();
    switch (outcome) {
      case IssuedAttestation ia -> {
        if (ia.attestationCredential() != null) {
          node.put(FIELD_ATTESTATION_CREDENTIAL, ia.attestationCredential());
        }
      }
      case UnverifiableAttestation ua -> {
        node.put(FIELD_FAILURE_CATEGORY, ua.failureCategory().name());
        if (ua.rawAttestation() != null) {
          node.put(FIELD_RAW_ATTESTATION, truncate(ua.rawAttestation()));
        }
        if (ua.verificationError() != null) {
          node.put(FIELD_VERIFICATION_ERROR, ua.verificationError());
        }
      }
    }
    return writeReport(node);
  }

  private String buildFailedAttemptReport(FailureCategory category, String failureDetail) {
    ObjectNode node = objectMapper.createObjectNode();
    node.put(FIELD_FAILURE_CATEGORY, category.name());
    if (failureDetail != null) {
      node.put(FIELD_FAILURE_DETAIL, failureDetail);
    }
    return writeReport(node);
  }

  private String writeReport(ObjectNode node) {
    try {
      return objectMapper.writeValueAsString(node);
    } catch (JsonProcessingException e) {
      // ObjectNode serialisation never fails in practice
      throw new IllegalStateException("Failed to serialise compliance report", e);
    }
  }
}
