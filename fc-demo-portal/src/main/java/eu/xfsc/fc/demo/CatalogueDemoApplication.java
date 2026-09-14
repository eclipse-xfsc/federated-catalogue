package eu.xfsc.fc.demo;

/*-
 * ---license-start
 * fc-demo-portal
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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The Application class.
 */
@SpringBootApplication
public class CatalogueDemoApplication {
  /**
   * The main Method.
   *
   * @param args the args for the main method
   */
  public static void main(String[] args) {
    SpringApplication.run(CatalogueDemoApplication.class, args);
  }
}
