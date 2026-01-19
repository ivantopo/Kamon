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

import java.time.Instant
import kamon.testkit.SpanInspection
import kamon.testkit.munit.InitAndStopKamonAfterAll
import kamon.Kamon
import kamon.tag.Lookups._
import kamon.trace.Span.Link.Kind
import munit.FunSuite

class LocalSpanSpec extends FunSuite with SpanInspection.Syntax with InitAndStopKamonAfterAll {

  test("sampled and finished span is sent to the Span reporters") {
    val finishedSpan = Kamon.spanBuilder("test-span")
      .tag("test", "value")
      .doNotTrackMetrics()
      .trackMetrics()
      .start(Instant.EPOCH.plusSeconds(1))
      .toFinished(Instant.EPOCH.plusSeconds(10))

    assertEquals(finishedSpan.operationName, "test-span")
    assertEquals(finishedSpan.from, Instant.EPOCH.plusSeconds(1))
    assertEquals(finishedSpan.to, Instant.EPOCH.plusSeconds(10))
    assertEquals(finishedSpan.tags.get(any("test")), "value")
  }

  test("pass all the tags and marks to the FinishedSpan instance when started and finished") {
    val linkedSpan = Kamon.spanBuilder("linked").start()

    val finishedSpan = Kamon.spanBuilder("full-span")
      .tag("builder-string-tag", "value")
      .tag("builder-boolean-tag-true", true)
      .tag("builder-boolean-tag-false", false)
      .tag("builder-number-tag", 42)
      .start(Instant.EPOCH.plusSeconds(1))
      .tag("span-string-tag", "value")
      .tag("span-boolean-tag-true", true)
      .tag("span-boolean-tag-false", false)
      .tag("span-number-tag", 42)
      .mark("my-mark")
      .mark("my-custom-timetamp-mark", Instant.EPOCH.plusSeconds(4))
      .link(linkedSpan, Span.Link.Kind.FollowsFrom)
      .name("fully-populated-span")
      .toFinished(Instant.EPOCH.plusSeconds(10))

    assertEquals(finishedSpan.operationName, "fully-populated-span")
    assertEquals(finishedSpan.from, Instant.EPOCH.plusSeconds(1))
    assertEquals(finishedSpan.to, Instant.EPOCH.plusSeconds(10))
    assertEquals(finishedSpan.tags.get(plain("builder-string-tag")), "value")
    assertEquals(finishedSpan.tags.get(plain("span-string-tag")), "value")
    assertEquals(finishedSpan.tags.get(plainBoolean("builder-boolean-tag-true")), Boolean.box(true))
    assertEquals(finishedSpan.tags.get(plainBoolean("builder-boolean-tag-false")), Boolean.box(false))
    assertEquals(finishedSpan.tags.get(plainBoolean("span-boolean-tag-true")), Boolean.box(true))
    assertEquals(finishedSpan.tags.get(plainBoolean("span-boolean-tag-false")), Boolean.box(false))
    assertEquals(finishedSpan.tags.get(plainLong("builder-number-tag")), Long.box(42L))
    assertEquals(finishedSpan.tags.get(plainLong("span-number-tag")), Long.box(42L))
    assert(finishedSpan.marks.map(_.key).contains("my-mark"))
    assert(finishedSpan.marks.map(_.key).contains("my-custom-timetamp-mark"))

    assertEquals(finishedSpan.links, Seq(Span.Link(Kind.FollowsFrom, linkedSpan.trace, linkedSpan.id)))

    val customTimestampMark = finishedSpan.marks.find(_.key == "my-custom-timetamp-mark")
    assert(customTimestampMark.isDefined)
    assertEquals(customTimestampMark.get.instant, Instant.EPOCH.plusSeconds(4))
  }
}
