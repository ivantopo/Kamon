package kamon.util

import kamon.util
import munit.FunSuite

import java.time.{Duration, Instant}

class ClockSuite extends FunSuite {
  private val MicrosInSecond = 1000000

  test("generate nanosecond precision Instants") {
    val nanoRemainder = newClock().instant().getNano % MicrosInSecond
    assertNotEquals(nanoRemainder, 0)
  }

  test("turn Instants into micros") {
    assertEquals(Clock.toEpochMicros(Instant.parse("2017-12-18T08:39:59.000000000Z")), 1513586399000000L)
    assertEquals(Clock.toEpochMicros(Instant.parse("2017-12-18T08:39:59.000000010Z")), 1513586399000000L)
    assertEquals(Clock.toEpochMicros(Instant.parse("2017-12-18T08:39:59.987654321Z")), 1513586399987654L)
    assertEquals(Clock.toEpochMicros(Instant.parse("2017-12-18T08:39:59.987000000Z")), 1513586399987000L)
  }

  test("calculate nanos between two Instants") {
    assertEquals(
      Clock.nanosBetween(
        Instant.parse("2017-12-18T08:39:59.987654321Z"),
        Instant.parse("2017-12-18T08:39:59.987654322Z")
      ),
      1L
    )
    assertEquals(
      Clock.nanosBetween(
        Instant.parse("2017-12-18T08:39:59.987654322Z"),
        Instant.parse("2017-12-18T08:39:59.987654321Z")
      ),
      -1L
    )
    assertEquals(
      Clock.nanosBetween(
        Instant.parse("2017-12-18T08:39:59.987Z"),
        Instant.parse("2017-12-18T08:39:59.988Z")
      ),
      1000000L
    )
    assertEquals(
      Clock.nanosBetween(
        Instant.parse("2017-12-18T08:39:59.987654Z"),
        Instant.parse("2017-12-18T08:39:59.987Z")
      ),
      -654000L
    )
  }

  test("calculate ticks aligned to rounded boundaries") {
    assertEquals(
      Clock
        .nextAlignedInstant(
          Instant.parse("2017-12-18T08:39:59.999Z"),
          Duration.ofSeconds(10)
        )
        .toString,
      "2017-12-18T08:40:00Z"
    )
    assertEquals(
      Clock
        .nextAlignedInstant(
          Instant.parse("2017-12-18T08:40:00.000Z"),
          Duration.ofSeconds(10)
        )
        .toString,
      "2017-12-18T08:40:10Z"
    )
    assertEquals(
      Clock
        .nextAlignedInstant(
          Instant.parse("2017-12-18T08:39:14.906Z"),
          Duration.ofSeconds(10)
        )
        .toString,
      "2017-12-18T08:39:20Z"
    )
  }

  private def newClock(): Clock =
    new util.Clock.Anchored()
}
