package eu.xfsc.fc.server.util;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import eu.xfsc.fc.core.exception.ClientException;
import jakarta.validation.constraints.NotNull;

/**
 * Helper class for the Time parsing.
 */
public class SelfDescriptionHelper {
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
          throw new ClientException("Please check the format of the time range parameters specified for SD filter!");
        }
        return timeRanges;
      }
    }
    throw new ClientException("Please check the value of the time range parameter specified for SD filter!");
  }
}
