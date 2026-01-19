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

package kamon.trace

import kamon.Kamon
import kamon.testkit.{Reconfigure, SpanInspection}
import kamon.testkit.munit.{Eventually, InitAndStopKamonAfterAll, TestSpanReporter}
import munit.FunSuite

import scala.concurrent.duration._

class SpanReportingDelaySpec extends FunSuite with SpanInspection.Syntax
    with Eventually with TestSpanReporter with Reconfigure with InitAndStopKamonAfterAll {

  test("delay disabled: keep spans with a positive sampling decision") {
    val span = Kamon.spanBuilder("positive-span-without-delay").start()
    span.trace.keep()
    span.finish()

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.operationName, span.operationName())
    }
  }

  test("delay disabled: not report spans with a negative sampling decision") {
    val span = Kamon.spanBuilder("negative-span-without-delay").start()
    span.trace.drop()
    span.finish()
    span.trace.keep() // Should not have any effect

    (1 to 5).foreach { _ =>
      val allSpans = testSpanReporter().spans()
      assert(allSpans.find(_.operationName == span.operationName()).isEmpty)

      Thread.sleep(100) // Should be enough because Spans are reported every millisecond in tests
    }
  }

  test("delay enabled: keep spans with a positive sampling decision") {
    applyConfig("kamon.trace.span-reporting-delay = 2 seconds")
    val span = Kamon.spanBuilder("overwrite-to-positive-with-delay").start()
    span.trace.drop()
    span.finish()
    span.trace.keep() // Should force the Span to be reported, even though it was dropped before finising

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.operationName, span.operationName())
    }
  }

  test("delay enabled: not report spans with a negative sampling decision") {
    val span = Kamon.spanBuilder("negative-span-without-delay").start()
    span.trace.keep()
    span.finish()
    span.trace.drop() // Should force the Span to be dropped, even though it was sampled before finishing

    (1 to 5).foreach { _ =>
      val allSpans = testSpanReporter().spans()
      assert(allSpans.find(_.operationName == span.operationName()).isEmpty)

      Thread.sleep(100) // Should be enough because Spans are reported every millisecond in tests
    }
  }
}
