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

import kamon.Kamon._
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, MetricInspection, Reconfigure}
import kamon.testkit.munit.InitAndStopKamonAfterAll
import munit.FunSuite

import scala.util.control.NoStackTrace

class SpanMetricsSpec extends FunSuite with InstrumentInspection.Syntax with MetricInspection.Syntax
    with Reconfigure with InitAndStopKamonAfterAll {

  sampleNever()

  val errorTag = "error" -> true
  val noErrorTag = "error" -> false

  test("track span.processing-time for successful execution on a Span") {
    val operation = "span-success"
    val operationTag = "operation" -> operation

    spanBuilder(operation)
      .start()
      .finish()

    val histogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag)))
    assertEquals(histogram.distribution().count, 1L)

    val errorHistogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag)))
    assertEquals(errorHistogram.distribution().count, 0L)
  }

  test("not track span.processing-time when doNotTrackProcessingTime() is called on the SpanBuilder or the Span") {
    val operation = "span-with-disabled-metrics"
    spanBuilder(operation)
      .start()
      .doNotTrackMetrics()
      .finish()

    spanBuilder(operation)
      .doNotTrackMetrics()
      .start()
      .finish()

    assert(!Span.Metrics.ProcessingTime.tagValues("operation").contains(operation))
  }

  test("allow specifying custom Span metric tags") {
    val operation = "span-with-custom-metric-tags"
    spanBuilder(operation)
      .tagMetrics("custom-metric-tag-on-builder", "value")
      .start()
      .tagMetrics("custom-metric-tag-on-span", "value")
      .finish()

    assert(Span.Metrics.ProcessingTime.tagValues("custom-metric-tag-on-builder").contains("value"))
    assert(Span.Metrics.ProcessingTime.tagValues("custom-metric-tag-on-span").contains("value"))
  }

  test("track span.processing-time if enabled by calling trackProcessingTime() on the Span") {
    val operation = "span-with-re-enabled-metrics"
    spanBuilder(operation)
      .start()
      .doNotTrackMetrics()
      .trackMetrics()
      .finish()

    assert(Span.Metrics.ProcessingTime.tagValues("operation").contains(operation))
  }

  test("track span.processing-time for failed execution on a Span") {
    val operation = "span-failure"
    val operationTag = "operation" -> operation

    spanBuilder(operation)
      .start()
      .fail("Terrible Error")
      .finish()

    spanBuilder(operation)
      .start()
      .fail("Terrible Error with Throwable", new Throwable with NoStackTrace)
      .finish()

    val histogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag)))
    assertEquals(histogram.distribution().count, 0L)

    val errorHistogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag)))
    assertEquals(errorHistogram.distribution().count, 2L)
  }

  test("add a parentOperation tag to the metrics if span metrics scoping is enabled") {
    val parent = spanBuilder("parent").start()
    val parentOperationTag = "parentOperation" -> "parent"

    val operation = "span-with-parent"
    val operationTag = "operation" -> operation

    spanBuilder(operation)
      .asChildOf(parent)
      .start()
      .finish()

    spanBuilder(operation)
      .asChildOf(parent)
      .start()
      .fail("Terrible Error")
      .finish()

    spanBuilder(operation)
      .asChildOf(parent)
      .start()
      .fail("Terrible Error with Throwable", new Throwable with NoStackTrace)
      .finish()

    val histogram =
      Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag, parentOperationTag)))
    assertEquals(histogram.distribution().count, 1L)

    val errorHistogram =
      Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag, parentOperationTag)))
    assertEquals(errorHistogram.distribution().count, 2L)
  }

  test("not add any parentOperation tag to the metrics if span metrics scoping is disabled") {
    withoutSpanScopingEnabled {
      val parent = spanBuilder("parent").start()
      val parentOperationTag = "parentOperation" -> "parent"

      val operation = "span-with-parent"
      val operationTag = "operation" -> operation

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .finish()

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .fail("Terrible Error")
        .finish()

      spanBuilder(operation)
        .asChildOf(parent)
        .start()
        .fail("Terrible Error with Throwable", new Throwable with NoStackTrace)
        .finish()

      val histogram =
        Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag, parentOperationTag)))
      assertEquals(histogram.distribution().count, 0L)

      val errorHistogram =
        Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag, parentOperationTag)))
      assertEquals(errorHistogram.distribution().count, 0L)
    }
  }

  test("track span.elapsed-time and span.wait-time for delayed spans") {
    val createTime = Instant.ofEpochSecond(0)
    val operation = "delayed-span-success"
    val operationTag = "operation" -> operation

    spanBuilder(operation)
      .delay(createTime)
      .start(createTime.plusNanos(10))
      .finish(createTime.plusNanos(30))

    val waitTime = Span.Metrics.WaitTime.withTags(TagSet.from(Map(operationTag, noErrorTag))).distribution()
    val elapsedTime = Span.Metrics.ElapsedTime.withTags(TagSet.from(Map(operationTag, noErrorTag))).distribution()
    val processingTime =
      Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag))).distribution()

    assertEquals(waitTime.count, 1L)
    assertEquals(waitTime.buckets.head.value, 10L)

    assertEquals(elapsedTime.count, 1L)
    assertEquals(elapsedTime.buckets.head.value, 30L)

    assertEquals(processingTime.count, 1L)
    assertEquals(processingTime.buckets.head.value, 20L)
  }

  test("include the span kind tag on all Span metrics") {
    val operation = "span-with-kind"

    spanBuilder(operation).start().finish()
    serverSpanBuilder(operation, "test").delay().start().finish()
    clientSpanBuilder(operation, "test").delay().start().finish()
    producerSpanBuilder(operation, "test").delay().start().finish()
    consumerSpanBuilder(operation, "test").delay().start().finish()
    internalSpanBuilder(operation, "test").delay().start().finish()

    val expectedSpanKinds = Seq(
      "client",
      "server",
      "producer",
      "consumer",
      "internal"
    )

    val processingTimeSpanKinds = Span.Metrics.ProcessingTime.tagValues("span.kind")
    val elapsedTimeSpanKinds = Span.Metrics.ElapsedTime.tagValues("span.kind")
    val waitTimeSpanKinds = Span.Metrics.WaitTime.tagValues("span.kind")

    expectedSpanKinds.foreach { kind =>
      assert(processingTimeSpanKinds.contains(kind), s"ProcessingTime should contain span.kind=$kind")
      assert(elapsedTimeSpanKinds.contains(kind), s"ElapsedTime should contain span.kind=$kind")
      assert(waitTimeSpanKinds.contains(kind), s"WaitTime should contain span.kind=$kind")
    }
  }

  private def withoutSpanScopingEnabled[T](f: => T): T = {
    disableSpanMetricScoping()
    val evaluated = f
    enableSpanMetricScoping()
    evaluated
  }
}
