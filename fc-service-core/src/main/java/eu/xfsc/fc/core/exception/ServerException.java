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
 * ServerException is an exception that can be thrown to customize the Internal server error of the Federated
 * Catalogue server application.
 * Implementation of the {@link ServiceException} exception.
 */
public class ServerException extends ServiceException {
  /**
   * Constructs a new Server Exception with the specified detail message.
   *
   * @param message Detailed message about the thrown exception.
   */
  public ServerException(String message) {
    super(message);
  }

  public ServerException(Throwable cause) {
    super(cause);
  }

  /**
   * Constructs a new Server exception with the specified detail message and cause.
   *
   * @param message Detailed message about the thrown exception.
   * @param cause Case of the thrown exception. (A null value is permitted, and indicates that the cause is unknown.)
   */
  public ServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
