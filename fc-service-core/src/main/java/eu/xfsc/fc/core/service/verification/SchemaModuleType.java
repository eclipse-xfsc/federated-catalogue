package eu.xfsc.fc.core.service.verification;

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

/**
 * Constants for schema validation module type identifiers.
 *
 * <p>These values are used as keys in admin config (schema.module.{type}.enabled)
 * and as the module type field in the schema validation API.</p>
 */
public final class SchemaModuleType {

  public static final String SHACL = "SHACL";
  public static final String JSON_SCHEMA = "JSON_SCHEMA";
  public static final String XML_SCHEMA = "XML_SCHEMA";
  public static final String OWL = "OWL";

  private SchemaModuleType() {
  }
}
