/* =========================================================================================
 * Copyright © 2013-2021 the kamon project <http://kamon.io/>
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

import java.time.Instant
import scala.concurrent.duration._

class LocalTailSamplerSpec extends FunSuite with SpanInspection.Syntax
    with Eventually with TestSpanReporter with Reconfigure with InitAndStopKamonAfterAll {

  test("keep traces that match the error count threshold") {
    applyConfig(
      """
        |kamon.trace {
        |  sampler = never
        |  span-reporting-delay = 1 second
        |
        |  local-tail-sampler {
        |    enabled = yes
        |    error-count-threshold = 3
        |  }
        |}
        |""".stripMargin
    )

    val parentSpan = Kamon.spanBuilder("parent-with-errors").start()

    (1 to 5).foreach { _ =>
      Kamon.spanBuilder("child")
        .asChildOf(parentSpan)
        .start()
        .fail("failing for tests")
        .finish()
    }

    parentSpan.finish()
    var spansFromParentTrace = 0

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.trace.id, parentSpan.trace.id)
      spansFromParentTrace += 1
      assertEquals(spansFromParentTrace, 6) // The parent Span plus five child Spans
    }
  }

  test("keep traces that match the latency threshold") {
    applyConfig(
      """
        |kamon.trace {
        |  sampler = never
        |  span-reporting-delay = 1 second
        |
        |  local-tail-sampler {
        |    enabled = yes
        |    latency-threshold = 3 seconds
        |  }
        |}
        |""".stripMargin
    )

    val startInstant = Instant.now()
    val parentSpan = Kamon.spanBuilder("parent-with-high-latency").start(startInstant)

    (1 to 5).foreach { _ =>
      Kamon.spanBuilder("child")
        .asChildOf(parentSpan)
        .start()
        .finish()
    }

    parentSpan.finish(startInstant.plusSeconds(5))
    var spansFromParentTrace = 0

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.trace.id, parentSpan.trace.id)
      spansFromParentTrace += 1
      assertEquals(spansFromParentTrace, 6) // The parent Span plus five child Spans
    }
  }

  test("not keep traces when tail sampling is disabled, even if they meet the criteria") {
    applyConfig(
      """
        |kamon.trace {
        |  sampler = never
        |  span-reporting-delay = 1 second
        |
        |  local-tail-sampler {
        |    enabled = no
        |    error-count-threshold= 1
        |    latency-threshold = 3 seconds
        |  }
        |}
        |""".stripMargin
    )

    val startInstant = Instant.now()
    val parentSpan = Kamon.spanBuilder("parent-with-disabled-tail-sampler").start(startInstant)

    (1 to 5).foreach { _ =>
      Kamon.spanBuilder("child")
        .asChildOf(parentSpan)
        .start()
        .fail("failure that shouldn't cause the trace to be sampled")
        .finish()
    }

    parentSpan.finish(startInstant.plusSeconds(5))

    (1 to 4).foreach { _ =>
      val allSpans = testSpanReporter().spans()
      assert(allSpans.find(_.operationName == parentSpan.operationName()).isEmpty)

      Thread.sleep(1000) // Should be enough time since all spans would be flushed after 1 second
    }
  }
}
