/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
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
import kamon.metric.MeasurementUnit._
import kamon.testkit.InstrumentInspection
import munit.FunSuite

class HistogramSuite extends FunSuite with InstrumentInspection.Syntax {

  test("record values and reset internal state when a snapshot is taken") {
    val histogram = Kamon.histogram("test", unit = time.nanoseconds).withoutTags()
    histogram.record(100)
    histogram.record(150, 998)
    histogram.record(200)

    val distribution = histogram.distribution()
    assertEquals(distribution.min, 100L)
    assertEquals(distribution.max, 200L)
    assertEquals(distribution.count, 1000L)
    assertEquals(distribution.buckets.length, 3)

    val bucketValues = distribution.buckets.map(b => (b.value, b.frequency)).toSet
    assert(bucketValues.contains((100L, 1L)))
    assert(bucketValues.contains((150L, 998L)))
    assert(bucketValues.contains((200L, 1L)))

    val emptyDistribution = histogram.distribution()
    assertEquals(emptyDistribution.min, 0L)
    assertEquals(emptyDistribution.max, 0L)
    assertEquals(emptyDistribution.count, 0L)
    assertEquals(emptyDistribution.buckets.length, 0)
  }

  test("accept a smallest discernible value configuration") {
    // The lowestDiscernibleValue gets rounded down to the closest power of 2, so, here it will be 64.
    val histogram = Kamon.histogram(
      "test-lowest-discernible-value",
      unit = time.nanoseconds,
      dynamicRange = DynamicRange.Fine.withLowestDiscernibleValue(100)
    ).withoutTags()
    histogram.record(100)
    histogram.record(200)
    histogram.record(300)
    histogram.record(1000)
    histogram.record(2000)
    histogram.record(3000)

    val distribution = histogram.distribution()
    assertEquals(distribution.min, 64L)
    assertEquals(distribution.max, 2944L)
    assertEquals(distribution.count, 6L)
    assertEquals(distribution.buckets.length, 6)

    val bucketValues = distribution.buckets.map(b => (b.value, b.frequency)).toSet
    assert(bucketValues.contains((64L, 1L)))
    assert(bucketValues.contains((192L, 1L)))
    assert(bucketValues.contains((256L, 1L)))
    assert(bucketValues.contains((960L, 1L)))
    assert(bucketValues.contains((1984L, 1L)))
    assert(bucketValues.contains((2944L, 1L)))
  }

  test("return the same percentile value that was requested on a resulting distribution") {
    val histogram = Kamon.histogram("returned-percentile").withoutTags()
    (1L to 10L).foreach(histogram.record)

    val distribution = histogram.distribution()
    assertEquals(distribution.percentile(99).rank, 99.0)
    assertEquals(distribution.percentile(99.9).rank, 99.9)
    assertEquals(distribution.percentile(99.99).rank, 99.99)
  }

  test("[private api] record values and optionally keep the internal state when a snapshot is taken") {
    val histogram = Kamon.histogram("test-keep-state", unit = time.nanoseconds).withoutTags()
    histogram.record(100)
    histogram.record(150, 998)
    histogram.record(200)

    val distribution = {
      histogram.distribution(resetState = false) // first one gets discarded
      histogram.distribution(resetState = false)
    }

    assertEquals(distribution.min, 100L)
    assertEquals(distribution.max, 200L)
    assertEquals(distribution.count, 1000L)
    assertEquals(distribution.buckets.length, 3)

    val bucketValues = distribution.buckets.map(b => (b.value, b.frequency)).toSet
    assert(bucketValues.contains((100L, 1L)))
    assert(bucketValues.contains((150L, 998L)))
    assert(bucketValues.contains((200L, 1L)))
  }
}
