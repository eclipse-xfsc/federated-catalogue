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

import org.apache.jena.rdf.model.Model;

/**
 * Result of filtering a Jena RDF model against the protected RDF namespace.
 * {@code warning} is {@code null} when nothing was filtered.
 *
 * @see eu.xfsc.fc.core.service.verification.ProtectedNamespaceFilter
 */
public record FilteredModel(Model model, String warning) {

  public boolean hasWarning() {
    return warning != null;
  }

}
