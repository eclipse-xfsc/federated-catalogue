package eu.xfsc.fc.core.util;

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

import com.google.common.hash.Hashing;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Utilities for hashing strings.
 */
public class HashUtils {

  public static final String HASH_REGEX = "^[0-9a-h]{64}$";
  public static final Pattern HASH_PATTERN = Pattern.compile(HASH_REGEX);

  private HashUtils() {
    // Utility class.
  }

  /**
   * Calculates the Sha256 hash of the given data String and returns it as a
   * Hex-String.
   *
   * Example: f60409e0271867824617a5cea2893787d3030be27b01cd172e8fa03a366b1aeb
   *
   *
   * @param data The data to hash.
   * @return The hash of the data as Hex-String: ^[0-9a-f]{64}$ .)
   */
  public static String calculateSha256AsHex(String data) {
    return Hashing.sha256().hashString(data, StandardCharsets.UTF_8).toString();
  }

  /**
   * Calculates the Sha256 hash of the given data bytes and returns it as a
   * Hex-String.
   *
   * @param data The data to hash.
   * @return The hash of the data as Hex-String: ^[0-9a-f]{64}$
   */
  public static String calculateSha256AsHex(byte[] data) {
    return Hashing.sha256().hashBytes(data).toString();
  }

}
