package org.folio.marc.migrations.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DateUtilsTest {

  @Test
  void fromTimestamp_NullValue_ReturnsNull() {
    // Act
    OffsetDateTime result = DateUtils.fromTimestamp(null);

    // Assert
    assertNull(result);
  }

  @Test
  void fromTimestamp_NonNullValue_ReturnsOffsetDateTime() {
    // Arrange
    Timestamp timestamp = Timestamp.valueOf("2023-01-01 12:10:23");

    // Act
    OffsetDateTime result = DateUtils.fromTimestamp(timestamp);

    // Assert
    assertNotNull(result);
    assertEquals(2023, result.getYear());
    assertEquals(Month.JANUARY, result.getMonth());
    assertEquals(1, result.getDayOfMonth());
    assertEquals(12, result.getHour());
    assertEquals(10, result.getMinute());
    assertEquals(23, result.getSecond());
  }

  @Test
  void toTimestamp_NullValue_ReturnsNull() {
    // Act
    Timestamp result = DateUtils.toTimestamp(null);

    // Assert
    assertNull(result);
  }

  @Test
  void toTimestamp_NonNullValue_ReturnsTimestamp() {
    // Arrange
    OffsetDateTime offsetDateTime = OffsetDateTime.of(2023, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    // Act
    Timestamp result = DateUtils.toTimestamp(offsetDateTime);

    // Assert
    assertNotNull(result);
    assertEquals("2023-01-01 12:00:00.0", result.toString());
  }

  @Test
  void currentTsInString_NotNull() {
    // Act
    String result = DateUtils.currentTsInString();

    // Assert
    assertNotNull(result);
  }

  @Test
  void currentTs_NotNull() throws InterruptedException {
    // Arrange
    Instant before = Instant.now();
    Thread.sleep(1L);

    // Act
    Timestamp result = DateUtils.currentTs();

    // Assert
    assertNotNull(result);
    assertThat(result)
        .isAfter(before)
        .isBefore(Instant.now());
  }
}
