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
 * ParserException is an exception that can be thrown to customize the asset parse error of the Federated
 * Catalogue server application.
 * Implementation of the {@link ServiceException} exception.
 */
public class ParserException extends ServiceException {

  public ParserException(String message) {
    super(message);
  }

   /**
   * Constructs a new Parser exception with the specified detail message and cause.
   *
   * @param message Detailed message about the thrown exception.
   * @param cause Case of the thrown exception. (A null value is permitted, and indicates that the cause is unknown.)
   */
  public ParserException(String message, Throwable cause) {
    super(message, cause);
  }
}
