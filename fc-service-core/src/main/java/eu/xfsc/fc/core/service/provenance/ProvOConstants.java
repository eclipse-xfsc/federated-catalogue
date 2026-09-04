package eu.xfsc.fc.core.service.provenance;

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

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class ProvOConstants {

  static final String NAMESPACE = "http://www.w3.org/ns/prov#";
  static final String RDF_NAMESPACE = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
  static final String DCS_NAMESPACE = "https://w3id.org/facis/dcs/1#";
  static final String XSD_DATETIME = "http://www.w3.org/2001/XMLSchema#dateTime";

  static final String RDF_TYPE = RDF_NAMESPACE + "type";
  static final String DCS_ACTION = DCS_NAMESPACE + "action";
}
