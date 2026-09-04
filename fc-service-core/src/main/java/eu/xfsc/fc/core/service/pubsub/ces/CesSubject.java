package eu.xfsc.fc.core.service.pubsub.ces;

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

import com.fasterxml.jackson.annotation.JsonProperty;

@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.ToString
public class CesSubject {
	
	private String type;
	private String id;
	@JsonProperty("gx:integrity")
	private String gxIntegrity;
	@JsonProperty("gx:integrityNormalization")
	private String gxIntegrityNormalization;
	@JsonProperty("gx:version")
	private String gxVersion;
	@JsonProperty("gx:type")
	private String gxType;

}

