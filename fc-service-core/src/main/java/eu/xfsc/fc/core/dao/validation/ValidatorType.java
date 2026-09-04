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

/**
 * Validator type discriminator values for {@link ValidationResult#getValidatorType()}.
 */
public enum ValidatorType {

  /** On-demand SHACL validation of RDF assets. */
  SHACL,

  /** On-demand JSON Schema validation of non-RDF JSON assets. */
  JSON_SCHEMA,

  /** On-demand XML Schema validation of non-RDF XML assets. */
  XML_SCHEMA,

  /**
   * External trust framework compliance check.
   *
   * <p>No {@code ValidationStrategy} implementation exists yet; compliance evaluation is
   * currently performed by the trust-framework orchestrator outside the on-demand validation
   * pipeline.</p>
   */
  TRUST_FRAMEWORK
}
