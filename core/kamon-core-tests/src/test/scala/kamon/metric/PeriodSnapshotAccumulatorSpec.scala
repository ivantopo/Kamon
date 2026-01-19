/* =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon.metric

import kamon.Kamon
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, Reconfigure}
import kamon.util.Clock
import munit.FunSuite

import java.time.{Duration, Instant}

class PeriodSnapshotAccumulatorSuite extends FunSuite with Reconfigure with InstrumentInspection.Syntax {

  override def beforeAll(): Unit = {
    super.beforeAll()
    applyConfig("kamon.metric.tick-interval = 10 seconds")
  }

  val alignedZeroTime: Instant = Clock.nextAlignedInstant(Kamon.clock().instant(), Duration.ofSeconds(60)).minusSeconds(60)
  val unAlignedZeroTime: Instant = alignedZeroTime.plusSeconds(3)

  // Aligned snapshots, every 5 seconds from second 00.
  val fiveSecondsOne: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(5), 22)
  val fiveSecondsTwo: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime.plusSeconds(5), alignedZeroTime.plusSeconds(10), 33)
  val fiveSecondsThree: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime.plusSeconds(10), alignedZeroTime.plusSeconds(15), 12)
  val fiveSecondsFour: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime.plusSeconds(15), alignedZeroTime.plusSeconds(20), 37)
  val fiveSecondsFive: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime.plusSeconds(20), alignedZeroTime.plusSeconds(25), 54)
  val fiveSecondsSix: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime.plusSeconds(25), alignedZeroTime.plusSeconds(30), 63)
  val fiveSecondsSeven: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime.plusSeconds(30), alignedZeroTime.plusSeconds(35), 62)

  // Unaligned snapshots, every 10 seconds from second 03
  val tenSecondsOne: PeriodSnapshot = createPeriodSnapshot(unAlignedZeroTime, unAlignedZeroTime.plusSeconds(10), 22)
  val tenSecondsTwo: PeriodSnapshot = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(10), unAlignedZeroTime.plusSeconds(20), 33)
  val tenSecondsThree: PeriodSnapshot = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(20), unAlignedZeroTime.plusSeconds(30), 12)
  val tenSecondsFour: PeriodSnapshot = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(30), unAlignedZeroTime.plusSeconds(40), 37)
  val tenSecondsFive: PeriodSnapshot = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(40), unAlignedZeroTime.plusSeconds(50), 54)
  val tenSecondsSix: PeriodSnapshot = createPeriodSnapshot(unAlignedZeroTime.plusSeconds(50), unAlignedZeroTime.plusSeconds(60), 63)

  val almostThreeSeconds: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(3).minusMillis(1), 22)
  val threeSeconds: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(3), 22)
  val fourSeconds: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(4), 22)
  val nineSeconds: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(9), 22)
  val tenSeconds: PeriodSnapshot = createPeriodSnapshot(alignedZeroTime, alignedZeroTime.plusSeconds(10), 36)

  test("allow to peek on an empty accumulator") {
    val accumulator = newAccumulator(10, 1)
    val periodSnapshot = accumulator.peek()
    assert(periodSnapshot.histograms.isEmpty)
    assert(periodSnapshot.timers.isEmpty)
    assert(periodSnapshot.rangeSamplers.isEmpty)
    assert(periodSnapshot.gauges.isEmpty)
    assert(periodSnapshot.counters.isEmpty)
  }

  test("bypass accumulation if the configured duration is equal to the metric tick-interval, regardless of the snapshot") {
    val accumulator = newAccumulator(10, 1)
    val result1 = accumulator.add(tenSeconds)
    assert(result1.isDefined)
    assert(result1.get eq tenSeconds)

    val result2 = accumulator.add(fiveSecondsOne)
    assert(result2.isDefined)
    assert(result2.get eq fiveSecondsOne)
  }

  test("bypass accumulation if snapshots are beyond the expected next tick") {
    val accumulator = newAccumulator(4, 1)
    assert(accumulator.add(almostThreeSeconds).isEmpty)
    assert(accumulator.add(fourSeconds).isDefined)

    val result = accumulator.add(nineSeconds)
    assert(result.isDefined)
    assert(result.get eq nineSeconds)
  }

  test("remove snapshots once they have been flushed") {
    val accumulator = newAccumulator(15, 0)

    assert(accumulator.add(fiveSecondsOne).isEmpty)
    assert(accumulator.add(fiveSecondsTwo).isEmpty)
    val firstSnapshot = accumulator.add(fiveSecondsThree)
    assert(firstSnapshot.isDefined)

    assertEquals(firstSnapshot.get.counters.size, 1)
    assertEquals(firstSnapshot.get.gauges.size, 1)
    assertEquals(firstSnapshot.get.histograms.size, 1)
    assertEquals(firstSnapshot.get.timers.size, 1)
    assertEquals(firstSnapshot.get.rangeSamplers.size, 1)

    assert(accumulator.add(clear(fiveSecondsFour)).isEmpty)
    assert(accumulator.add(clear(fiveSecondsFive)).isEmpty)
    val secondSnapshot = accumulator.add(clear(fiveSecondsSix))
    assert(secondSnapshot.isDefined)

    assertEquals(secondSnapshot.get.counters.size, 0)
    assertEquals(secondSnapshot.get.gauges.size, 0)
    assertEquals(secondSnapshot.get.histograms.size, 0)
    assertEquals(secondSnapshot.get.timers.size, 0)
    assertEquals(secondSnapshot.get.rangeSamplers.size, 0)
  }

  test("align snapshot production to round boundaries") {
    // If accumulating over 15 seconds, the snapshots should be generated at 00:00:00, 00:00:15, 00:00:30 and so on.
    // The first snapshot will almost always be shorter than 15 seconds as it gets adjusted to the nearest initial period.

    val accumulator = newAccumulator(15, 0)
    assert(accumulator.add(fiveSecondsTwo).isEmpty) // second 0:10
    val s15 = accumulator.add(fiveSecondsThree) // second 0:15
    assert(s15.isDefined)
    assertEquals(s15.get.from, fiveSecondsTwo.from)
    assertEquals(s15.get.to, fiveSecondsThree.to)

    assert(accumulator.add(fiveSecondsFour).isEmpty) // second 0:20
    assert(accumulator.add(fiveSecondsFive).isEmpty) // second 0:25
    val s30 = accumulator.add(fiveSecondsSix) // second 0:30
    assert(s30.isDefined)
    assertEquals(s30.get.from, fiveSecondsFour.from)
    assertEquals(s30.get.to, fiveSecondsSix.to)

    assert(accumulator.add(fiveSecondsSeven).isEmpty) // second 0:35
  }

  test("do best effort to align when snapshots themselves are not aligned") {
    val accumulator = newAccumulator(30, 0)
    assert(accumulator.add(tenSecondsOne).isEmpty) // second 0:13
    assert(accumulator.add(tenSecondsTwo).isEmpty) // second 0:23
    val s23 = accumulator.add(tenSecondsThree) // second 0:33
    assert(s23.isDefined)
    assertEquals(s23.get.from, tenSecondsOne.from)
    assertEquals(s23.get.to, tenSecondsThree.to)

    assert(accumulator.add(tenSecondsFour).isEmpty) // second 0:43
    assert(accumulator.add(tenSecondsFive).isEmpty) // second 0:53
    val s103 = accumulator.add(tenSecondsSix) // second 1:03
    assert(s103.isDefined)
    assertEquals(s103.get.from, tenSecondsFour.from)
    assertEquals(s103.get.to, tenSecondsSix.to)

    assert(accumulator.add(fiveSecondsSeven).isEmpty) // second 1:13
  }

  test("allow to peek into the data that has been accumulated") {
    val accumulator = newAccumulator(20, 1)
    assert(accumulator.add(fiveSecondsOne).isEmpty)
    assert(accumulator.add(fiveSecondsTwo).isEmpty)

    for (_ <- 1 to 10) {
      val peekSnapshot = accumulator.peek()
      val mergedHistogram = peekSnapshot.histograms.find(_.name == "histogram").get.instruments.head.value
      val mergedRangeSampler = peekSnapshot.rangeSamplers.find(_.name == "rangeSampler").get.instruments.head.value
      assertEquals(peekSnapshot.counters.find(_.name == "counter").get.instruments.head.value, 55L)
      assertEquals(peekSnapshot.gauges.find(_.name == "gauge").get.instruments.head.value, 33.0)
      assert(mergedHistogram.buckets.map(_.value).contains(22L))
      assert(mergedHistogram.buckets.map(_.value).contains(33L))
      assert(mergedRangeSampler.buckets.map(_.value).contains(22L))
      assert(mergedRangeSampler.buckets.map(_.value).contains(33L))
    }

    assert(accumulator.add(fiveSecondsThree).isEmpty)

    for (_ <- 1 to 10) {
      val peekSnapshot = accumulator.peek()
      val mergedHistogram = peekSnapshot.histograms.find(_.name == "histogram").get.instruments.head.value
      val mergedRangeSampler = peekSnapshot.rangeSamplers.find(_.name == "rangeSampler").get.instruments.head.value
      assertEquals(peekSnapshot.counters.find(_.name == "counter").get.instruments.head.value, 67L)
      assertEquals(peekSnapshot.gauges.find(_.name == "gauge").get.instruments.head.value, 12.0)
      assert(mergedHistogram.buckets.map(_.value).contains(22L))
      assert(mergedHistogram.buckets.map(_.value).contains(33L))
      assert(mergedHistogram.buckets.map(_.value).contains(12L))
      assert(mergedRangeSampler.buckets.map(_.value).contains(22L))
      assert(mergedRangeSampler.buckets.map(_.value).contains(33L))
      assert(mergedRangeSampler.buckets.map(_.value).contains(12L))
    }
  }

  test("produce a snapshot when enough data has been accumulated") {
    val accumulator = newAccumulator(15, 1)
    assert(accumulator.add(fiveSecondsOne).isEmpty)
    assert(accumulator.add(fiveSecondsTwo).isEmpty)

    val snapshotOne = accumulator.add(fiveSecondsThree)
    assert(snapshotOne.isDefined)
    assertEquals(snapshotOne.get.from, fiveSecondsOne.from)
    assertEquals(snapshotOne.get.to, fiveSecondsThree.to)

    val mergedHistogram = snapshotOne.get.histograms.find(_.name == "histogram").get.instruments.head.value
    val mergedRangeSampler = snapshotOne.get.rangeSamplers.find(_.name == "rangeSampler").get.instruments.head.value
    assertEquals(snapshotOne.get.counters.find(_.name == "counter").get.instruments.head.value, 67L)
    assertEquals(snapshotOne.get.gauges.find(_.name == "gauge").get.instruments.head.value, 12.0)
    assert(mergedHistogram.buckets.map(_.value).contains(22L))
    assert(mergedHistogram.buckets.map(_.value).contains(33L))
    assert(mergedHistogram.buckets.map(_.value).contains(12L))
    assert(mergedRangeSampler.buckets.map(_.value).contains(22L))
    assert(mergedRangeSampler.buckets.map(_.value).contains(33L))
    assert(mergedRangeSampler.buckets.map(_.value).contains(12L))

    val emptySnapshot = accumulator.peek()
    assert(emptySnapshot.histograms.isEmpty)
    assert(emptySnapshot.rangeSamplers.isEmpty)
    assert(emptySnapshot.gauges.isEmpty)
    assert(emptySnapshot.counters.isEmpty)

    assert(accumulator.add(fiveSecondsFour).isEmpty)
  }

  def newAccumulator(duration: Long, margin: Long): PeriodSnapshot.Accumulator =
    PeriodSnapshot.accumulator(Duration.ofSeconds(duration), Duration.ofSeconds(margin))

  /** Creates a period snapshot with one metric of each type with one instrument. All instruments have a single
    * measurement with the provided value.
    */
  def createPeriodSnapshot(from: Instant, to: Instant, value: Long): PeriodSnapshot = {
    val valueSettings = Metric.Settings.ForValueInstrument(MeasurementUnit.none, Duration.ofSeconds(10))
    val distributionSettings =
      Metric.Settings.ForDistributionInstrument(MeasurementUnit.none, Duration.ofSeconds(10), DynamicRange.Default)
    val distribution = Kamon.histogram("temp").withoutTags().record(value).distribution()

    PeriodSnapshot(
      from,
      to,
      counters = Seq(MetricSnapshot.ofValues(
        "counter",
        "",
        valueSettings,
        Seq(Instrument.Snapshot(TagSet.of("metric", "counter"), value))
      )),
      gauges = Seq(MetricSnapshot.ofValues(
        "gauge",
        "",
        valueSettings,
        Seq(Instrument.Snapshot(TagSet.of("metric", "gauge"), value.toDouble))
      )),
      histograms = Seq(MetricSnapshot.ofDistributions(
        "histogram",
        "",
        distributionSettings,
        Seq(Instrument.Snapshot(TagSet.of("metric", "histogram"), distribution))
      )),
      timers = Seq(MetricSnapshot.ofDistributions(
        "timer",
        "",
        distributionSettings,
        Seq(Instrument.Snapshot(TagSet.of("metric", "timer"), distribution))
      )),
      rangeSamplers = Seq(MetricSnapshot.ofDistributions(
        "rangeSampler",
        "",
        distributionSettings,
        Seq(Instrument.Snapshot(TagSet.of("metric", "rangeSampler"), distribution))
      ))
    )
  }

  def clear(periodSnapshot: PeriodSnapshot): PeriodSnapshot =
    periodSnapshot.copy(
      counters = Seq.empty,
      gauges = Seq.empty,
      histograms = Seq.empty,
      timers = Seq.empty,
      rangeSamplers = Seq.empty
    )
}
