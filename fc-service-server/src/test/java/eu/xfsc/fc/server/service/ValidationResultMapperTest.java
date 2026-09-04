package eu.xfsc.fc.server.service;

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

import eu.xfsc.fc.api.generated.model.StoredValidationResult;
import eu.xfsc.fc.core.dao.validation.GraphSyncStatus;
import eu.xfsc.fc.core.dao.validation.OutdatedReason;
import eu.xfsc.fc.core.dao.validation.ValidationResult;
import eu.xfsc.fc.core.dao.validation.ValidatorType;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies {@link ValidationResultMapper#toDto} maps every entity field to the API model,
 * including the graph-sync and outdated-tracking fields.
 */
class ValidationResultMapperTest {

  @Test
  void toDto_entityWithAllFieldsSet_mapsEveryField() {
    ValidationResult entity = new ValidationResult();
    entity.setId(1L);
    entity.setAssetIds(new String[] {"urn:asset:1"});
    entity.setValidatorIds(new String[] {"validator:1"});
    entity.setValidatorType(ValidatorType.TRUST_FRAMEWORK);
    entity.setConforms(true);
    entity.setValidatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    entity.setReport("{}");
    entity.setContentHash("abc123");
    entity.setGraphSyncStatus(GraphSyncStatus.FAILED);
    entity.setOutdated(true);
    entity.setOutdatedReason(OutdatedReason.ASSET_REVOKED);

    StoredValidationResult dto = ValidationResultMapper.toDto(entity);

    assertThat(dto.getGraphSyncStatus()).isEqualTo(StoredValidationResult.GraphSyncStatusEnum.FAILED);
    assertThat(dto.getOutdated()).isTrue();
    assertThat(dto.getOutdatedReason()).isEqualTo(StoredValidationResult.OutdatedReasonEnum.ASSET_REVOKED);
  }

  @Test
  void toDto_entityWithNullGraphSyncAndOutdatedReason_mapsToNull() {
    ValidationResult entity = new ValidationResult();
    entity.setId(1L);
    entity.setAssetIds(new String[] {"urn:asset:1"});
    entity.setValidatorIds(new String[] {"validator:1"});
    entity.setValidatorType(ValidatorType.SHACL);
    entity.setConforms(true);
    entity.setValidatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    entity.setOutdated(false);
    // graphSyncStatus and outdatedReason left null, as for a mid-write or non-outdated row.

    StoredValidationResult dto = ValidationResultMapper.toDto(entity);

    assertThat(dto.getGraphSyncStatus()).isNull();
    assertThat(dto.getOutdated()).isFalse();
    assertThat(dto.getOutdatedReason()).isNull();
  }
}
