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
import kamon.tag.TagSet
import munit.FunSuite

import java.util.Collections.{singletonMap => javaMap}

class MetricLookupSuite extends FunSuite {

  test("always return the same histogram metric") {
    val histogramOne = Kamon.histogram("histogram-lookup")
    val histogramTwo = Kamon.histogram("histogram-lookup")
    assert(histogramOne eq histogramTwo)
  }

  test("always return the same counter metric") {
    val counterOne = Kamon.counter("counter-lookup")
    val counterTwo = Kamon.counter("counter-lookup")
    assert(counterOne eq counterTwo)
  }

  test("always return the same gauge metric") {
    val gaugeOne = Kamon.gauge("gauge-lookup")
    val gaugeTwo = Kamon.gauge("gauge-lookup")
    assert(gaugeOne eq gaugeTwo)
  }

  test("always return the same range sampler metric") {
    val rangeSamplerOne = Kamon.rangeSampler("range-sampler-lookup")
    val rangeSamplerTwo = Kamon.rangeSampler("range-sampler-lookup")
    assert(rangeSamplerOne eq rangeSamplerTwo)
  }

  test("throw an IllegalArgumentException when a metric redefinition is attempted") {
    def redefinitionError(name: String, currentType: String, newType: String): String =
      s"Cannot redefine metric [$name] as a [$newType], it was already registered as a [$currentType]"

    val counterGaugeEx = intercept[IllegalArgumentException] {
      Kamon.counter("original-counter")
      Kamon.gauge("original-counter")
    }
    assertEquals(counterGaugeEx.getMessage, redefinitionError("original-counter", "counter", "gauge"))

    val counterHistogramEx = intercept[IllegalArgumentException] {
      Kamon.counter("original-counter")
      Kamon.histogram("original-counter")
    }
    assertEquals(counterHistogramEx.getMessage, redefinitionError("original-counter", "counter", "histogram"))

    val counterRangeSamplerEx = intercept[IllegalArgumentException] {
      Kamon.counter("original-counter")
      Kamon.rangeSampler("original-counter")
    }
    assertEquals(counterRangeSamplerEx.getMessage, redefinitionError("original-counter", "counter", "rangeSampler"))

    val counterTimerEx = intercept[IllegalArgumentException] {
      Kamon.counter("original-counter")
      Kamon.timer("original-counter")
    }
    assertEquals(counterTimerEx.getMessage, redefinitionError("original-counter", "counter", "timer"))

    val histogramCounterEx = intercept[IllegalArgumentException] {
      Kamon.histogram("original-histogram")
      Kamon.counter("original-histogram")
    }
    assertEquals(histogramCounterEx.getMessage, redefinitionError("original-histogram", "histogram", "counter"))
  }

  test("always return the same histogram for a set of tags") {
    val histogramOne = Kamon.histogram("histogram-lookup").withTag("tag", "value")
    val histogramTwo = Kamon.histogram("histogram-lookup").withTag("tag", "value")
    val histogramThree = Kamon.histogram("histogram-lookup").withTags(TagSet.from(javaMap("tag", "value": Any)))

    assert(histogramOne eq histogramTwo)
    assert(histogramOne eq histogramThree)
  }

  test("always return the same counter for a set of tags") {
    val counterOne = Kamon.counter("counter-lookup").withTag("tag", "value")
    val counterTwo = Kamon.counter("counter-lookup").withTag("tag", "value")
    val counterThree = Kamon.counter("counter-lookup").withTags(TagSet.from(javaMap("tag", "value": Any)))

    assert(counterOne eq counterTwo)
    assert(counterOne eq counterThree)
  }

  test("always return the same gauge for a set of tags") {
    val gaugeOne = Kamon.gauge("gauge-lookup").withTag("tag", "value")
    val gaugeTwo = Kamon.gauge("gauge-lookup").withTag("tag", "value")
    val gaugeThree = Kamon.gauge("gauge-lookup").withTags(TagSet.from(javaMap("tag", "value": Any)))

    assert(gaugeOne eq gaugeTwo)
    assert(gaugeOne eq gaugeThree)
  }

  test("always return the same range-sampler for a set of tags") {
    val rangeSamplerOne = Kamon.rangeSampler("range-sampler-lookup").withTag("tag", "value")
    val rangeSamplerTwo = Kamon.rangeSampler("range-sampler-lookup").withTag("tag", "value")
    val rangeSamplerThree =
      Kamon.rangeSampler("range-sampler-lookup").withTags(TagSet.from(javaMap("tag", "value": Any)))

    assert(rangeSamplerOne eq rangeSamplerTwo)
    assert(rangeSamplerOne eq rangeSamplerThree)
  }
}
