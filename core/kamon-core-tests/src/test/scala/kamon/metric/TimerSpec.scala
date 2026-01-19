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
import kamon.testkit.InstrumentInspection
import munit.FunSuite

class TimerSuite extends FunSuite with InstrumentInspection.Syntax {

  test("record the duration between calls to .start() and .stop() in the StartedTimer") {
    val timer = Kamon.timer("timer-spec").withoutTags()
    timer.start().stop()
    timer.start().stop()
    timer.start().stop()

    assertEquals(timer.distribution().count, 3L)
  }

  test("ensure that a started timer can only be stopped once") {
    val timer = Kamon.timer("timer-stop-once").withoutTags()
    val startedTimer = timer.start()
    startedTimer.stop()
    startedTimer.stop()
    startedTimer.stop()

    assertEquals(timer.distribution().count, 1L)
  }

  test("allow to record values and produce distributions as Histograms do") {
    val timer = Kamon.timer("test-timer").withoutTags()
    timer.record(100)
    timer.record(200)

    val distribution = timer.distribution()
    assertEquals(distribution.min, 100L)
    assertEquals(distribution.max, 200L)
    assertEquals(distribution.count, 2L)
    assertEquals(distribution.buckets.length, 2)

    val bucketValues = distribution.buckets.map(b => (b.value, b.frequency)).toSet
    assert(bucketValues.contains((100L, 1L)))
    assert(bucketValues.contains((200L, 1L)))

    val emptyDistribution = timer.distribution()
    assertEquals(emptyDistribution.min, 0L)
    assertEquals(emptyDistribution.max, 0L)
    assertEquals(emptyDistribution.count, 0L)
    assertEquals(emptyDistribution.buckets.length, 0)
  }
}
