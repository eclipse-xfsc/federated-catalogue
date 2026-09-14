package eu.xfsc.fc.server.util;

/*-
 * ---license-start
 * fc-service-server
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

import java.time.Instant;
import java.time.format.DateTimeParseException;

import eu.xfsc.fc.core.exception.ClientException;
import jakarta.validation.constraints.NotNull;

/**
 * Helper class for the Time parsing.
 */
public class AssetHelper {
  /**
   * Helper method for the parsing time range.
   *
   * @param timeRange String time-range.
   * @return Array of String.
   */
  public static String[] parseTimeRange(@NotNull String timeRange) {
    if (timeRange != null && timeRange.contains("/")) {
      String[] timeRanges = timeRange.split("/");
      if (timeRanges.length == 2) {
        try {
          Instant.parse(timeRanges[0]);
          Instant.parse(timeRanges[1]);
        } catch (DateTimeParseException exception) {
          throw new ClientException("Please check the format of the time range parameters specified for asset filter!");
        }
        return timeRanges;
      }
    }
    throw new ClientException("Please check the value of the time range parameter specified for asset filter!");
  }
}
