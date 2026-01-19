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

import java.time.Duration
import java.util.function.Supplier
import kamon.Kamon
import kamon.metric.Counter.delta
import kamon.testkit.InstrumentInspection
import kamon.testkit.munit.{Eventually, InitAndStopKamonAfterAll}
import munit.FunSuite

class CounterSuite extends FunSuite with InstrumentInspection.Syntax with Eventually with InitAndStopKamonAfterAll {

  test("allow unit and bundled increments") {
    val counter = Kamon.counter("unit-increments").withoutTags()
    counter.increment()
    counter.increment()
    counter.increment(40)

    assertEquals(counter.value(), 42L)
  }

  test("warn the user and ignore attempts to decrement the counter") {
    val counter = Kamon.counter("attempt-to-decrement").withoutTags()
    counter.increment(100)
    counter.increment(100)
    counter.increment(100)

    assertEquals(counter.value(), 300L)
  }

  test("reset the internal state to zero after taking snapshots as a default behavior") {
    val counter = Kamon.counter("reset-after-snapshot").withoutTags()
    counter.increment()
    counter.increment(10)

    assertEquals(counter.value(), 11L)
    assertEquals(counter.value(), 0L)
  }

  test("optionally leave the internal state unchanged") {
    val counter = Kamon.counter("leave-state-unchanged").withoutTags()
    counter.increment()
    counter.increment(10)

    assertEquals(counter.value(resetState = false), 11L)
    assertEquals(counter.value(resetState = false), 11L)
  }

  test("have an easy to setup delta auto-update that stores difference between the last two observations of a supplier") {
    val autoUpdateCounter = Kamon.counter("auto-update-delta").withoutTags()
      .autoUpdate(delta(supplierOf(0, 0, 1, 1, 2, 3, 4, 6, 8, 10, 12, 16, 18)), Duration.ofMillis(1))

    eventually() {
      assertEquals(autoUpdateCounter.value(resetState = false), 18L)
    }
  }

  test("ignore decrements in observations") {
    val autoUpdateCounter = Kamon.counter("auto-update-delta-with-decrement").withoutTags()
      .autoUpdate(delta(supplierOf(0, 0, 1, 1, 2, 3, 4, 6, 5, 4, 10, 16, 18)), Duration.ofMillis(1))

    eventually() {
      assertEquals(autoUpdateCounter.value(resetState = false), 20L)
    }
  }

  /** Creates a supplier that gives out the sequence of numbers provided */
  def supplierOf(numbers: Long*): Supplier[Long] = new Supplier[Long] {
    var remaining = numbers.toList
    var last = numbers.head

    override def get(): Long = synchronized {
      if (remaining.isEmpty) last
      else {
        val head = remaining.head
        remaining = remaining.tail
        last = head
        head
      }
    }
  }
}
