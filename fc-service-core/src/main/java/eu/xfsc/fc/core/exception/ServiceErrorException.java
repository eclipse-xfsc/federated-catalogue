package eu.xfsc.fc.core.exception;

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
 * Thrown when an external service required to fulfil a request was reached but responded with
 * an error, as opposed to {@link ServiceUnavailableException}'s connection-level meaning of
 * "could not be reached at all". A subtype of {@link ServiceUnavailableException} so it still
 * maps to HTTP 503 Service Unavailable without a new exception handler; callers that need the
 * finer distinction (e.g. audit-trail categorisation) can match on this type specifically.
 */
public class ServiceErrorException extends ServiceUnavailableException {

  public ServiceErrorException(String message, Throwable cause) {
    super(message, cause);
  }
}
