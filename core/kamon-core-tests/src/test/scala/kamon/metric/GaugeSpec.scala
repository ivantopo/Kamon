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

class GaugeSuite extends FunSuite with InstrumentInspection.Syntax {

  test("have a starting value of zero") {
    val gauge = Kamon.gauge("default-value").withoutTags()
    assertEquals(gauge.value(), 0d)
  }

  test("retain the last value recorded on it") {
    val gauge = Kamon.gauge("retain-value").withoutTags().update(42d)
    assertEquals(gauge.value(), 42d)
    assertEquals(gauge.value(), 42d)

    gauge.update(17d)
    assertEquals(gauge.value(), 17d)
    assertEquals(gauge.value(), 17d)
  }

  test("ignore updates with negative values") {
    val gauge = Kamon.gauge("non-negative-value").withoutTags().update(30)
    assertEquals(gauge.value(), 30d)
    gauge.update(-20d)
    assertEquals(gauge.value(), 30d)

    gauge.decrement(100)
    assertEquals(gauge.value(), 30d)

    gauge.increment(-100)
    assertEquals(gauge.value(), 30d)
  }

  test("increment and decrement the current value of the gauge") {
    val gauge = Kamon.gauge("increment-decrement").withoutTags().update(30)
    assertEquals(gauge.value(), 30d)
    gauge.increment(10d)
    gauge.increment(10d)
    assertEquals(gauge.value(), 50d)

    gauge.decrement(15)
    gauge.decrement(15d)
    assertEquals(gauge.value(), 20d)
  }

  test("increment and decrement the current value of the gauge with non whole values") {
    val gauge = Kamon.gauge("increment-decrement-non-whole").withoutTags().update(30)
    assertEquals(gauge.value(), 30d)
    gauge.increment(10.5d)
    gauge.increment(10.5d)
    assertEquals(gauge.value(), 51d)

    gauge.decrement(10.5d)
    gauge.decrement(10.5d)
    assertEquals(gauge.value(), 30d)
  }
}
