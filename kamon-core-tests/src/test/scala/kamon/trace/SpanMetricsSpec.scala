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

import kamon.Kamon.spanBuilder
import kamon.tag.TagSet
import kamon.testkit.{InstrumentInspection, MetricInspection, Reconfigure}
import org.scalatest.{Matchers, WordSpecLike}

import scala.util.control.NoStackTrace

class SpanMetricsSpec extends WordSpecLike with Matchers with InstrumentInspection.Syntax with MetricInspection.Syntax
    with Reconfigure {

  sampleNever()

  "Span Metrics" should {
    "be recorded for successful execution" in {
      val operation = "span-success"
      val operationTag = "operation" -> operation

      spanBuilder(operation)
        .start()
        .finish()

      val histogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag)))
      histogram.distribution().count shouldBe 1

      val errorHistogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag)))
      errorHistogram.distribution().count shouldBe 0

    }

    "not be recorded when disableMetrics() is called on the SpanBuilder or the Span" in {
      val operation = "span-with-disabled-metrics"
      spanBuilder(operation)
        .start()
        .doNotTrackProcessingTime()
        .finish()

      spanBuilder(operation)
        .doNotTrackProcessingTime()
        .start()
        .finish()

      Span.Metrics.ProcessingTime.tagValues("operation") shouldNot contain(operation)
    }

    "allow specifying custom Span metric tags" in {
      val operation = "span-with-custom-metric-tags"
      spanBuilder(operation)
        .tagMetric("custom-metric-tag-on-builder", "value")
        .start()
        .tagMetric("custom-metric-tag-on-span", "value")
        .finish()

      Span.Metrics.ProcessingTime.tagValues("custom-metric-tag-on-builder") should contain("value")
      Span.Metrics.ProcessingTime.tagValues("custom-metric-tag-on-span") should contain("value")
    }

    "be recorded if metrics are enabled by calling enableMetrics() on the Span" in {
      val operation = "span-with-re-enabled-metrics"
      spanBuilder(operation)
        .start()
        .doNotTrackProcessingTime()
        .trackProcessingTime()
        .finish()

      Span.Metrics.ProcessingTime.tagValues("operation") should contain(operation)
    }

    "record error latency and count" in {
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
      histogram.distribution().count shouldBe 0

      val errorHistogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag)))
      errorHistogram.distribution().count shouldBe 2
    }

    "add a parentOperation tag to the metrics if span metrics scoping is enabled" in {
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

      val histogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag, parentOperationTag)))
      histogram.distribution().count shouldBe 1

      val errorHistogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag, parentOperationTag)))
      errorHistogram.distribution().count shouldBe 2
    }

    "not add any parentOperation tag to the metrics if span metrics scoping is disabled" in withoutSpanScopingEnabled {
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

      val histogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, noErrorTag, parentOperationTag)))
      histogram.distribution().count shouldBe 0

      val errorHistogram = Span.Metrics.ProcessingTime.withTags(TagSet.from(Map(operationTag, errorTag, parentOperationTag)))
      errorHistogram.distribution().count shouldBe 0
    }
  }

  val errorTag = "error" -> true
  val noErrorTag = "error" -> false

  private def withoutSpanScopingEnabled[T](f: => T): T = {
    disableSpanMetricScoping()
    val evaluated = f
    enableSpanMetricScoping()
    evaluated
  }
}


