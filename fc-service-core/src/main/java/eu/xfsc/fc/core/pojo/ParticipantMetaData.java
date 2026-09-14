package eu.xfsc.fc.core.pojo;

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

import com.fasterxml.jackson.annotation.JsonIgnore;
import eu.xfsc.fc.api.generated.model.Participant;
import eu.xfsc.fc.core.util.HashUtils;

@lombok.AllArgsConstructor
@lombok.NoArgsConstructor
@lombok.EqualsAndHashCode(callSuper = true)
@lombok.Getter
@lombok.Setter
public class ParticipantMetaData extends Participant {

  @JsonIgnore
  private String assetHash;

  public ParticipantMetaData(String id, String participantName, String participantPublicKey, String asset) {
    super(id, participantName, participantPublicKey, asset);
    this.assetHash = HashUtils.calculateSha256AsHex(asset);
  }

  public ParticipantMetaData(String id, String participantName, String participantPublicKey, String asset, String assetHash) {
    super(id, participantName, participantPublicKey, asset);
    this.assetHash = assetHash;
  }
}
