package eu.xfsc.fc.server.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import eu.xfsc.fc.core.exception.ClientException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(SpringExtension.class)
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.ZONKY)
public class SelfDescriptionHelperTest {
  @Test
  public void testTimeRangeParserReturnsSuccessResult() {
    String[] timeRanges = SelfDescriptionHelper.parseTimeRange("2022-03-01T13:00:00Z/2022-05-11T15:30:00Z");
    assertEquals(timeRanges.length, 2);
    assertEquals(timeRanges[0], "2022-03-01T13:00:00Z");
    assertEquals(timeRanges[1], "2022-05-11T15:30:00Z");
  }

  @Test
  public void testParserWithoutSeparatorThrowClientException() {
    assertThrows(ClientException.class, () ->
        SelfDescriptionHelper.parseTimeRange("2022-03-01T13:00:00Z2022-05-11T15:30:00Z"));
  }

  @Test
  public void testParserWithNullThenThrowClientException() {
    assertThrows(ClientException.class, () -> SelfDescriptionHelper.parseTimeRange(null));
  }

  @Test
  public void testParserWithUncorrectedValueThenThrowClientException() {
    assertThrows(ClientException.class, () -> {
      String[] timeRanges = SelfDescriptionHelper.parseTimeRange("2022-03-01/2022-05-1115:30:00");
      Instant.parse(timeRanges[0]);
    });
  }
}
