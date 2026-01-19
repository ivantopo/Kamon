/*
 * =========================================================================================
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

import kamon.context.{Context, HttpPropagation}
import kamon.trace.Trace.SamplingDecision
import munit.FunSuite

import scala.collection.mutable

class B3SpanPropagationSpec extends FunSuite {
  val b3Propagation = SpanPropagation.B3()

  test("write the Span data into headers") {
    val headersMap = mutable.Map.empty[String, String]
    b3Propagation.write(testContext(), headerWriterFromMap(headersMap))

    assertEquals(headersMap.get("X-B3-TraceId"), Some("1234"))
    assertEquals(headersMap.get("X-B3-ParentSpanId"), Some("2222"))
    assertEquals(headersMap.get("X-B3-SpanId"), Some("4321"))
    assertEquals(headersMap.get("X-B3-Sampled"), Some("1"))
  }

  test("do not include the X-B3-ParentSpanId if there is no parent") {
    val headersMap = mutable.Map.empty[String, String]
    b3Propagation.write(testContextWithoutParent(), headerWriterFromMap(headersMap))

    assertEquals(headersMap.get("X-B3-TraceId"), Some("1234"))
    assert(headersMap.get("X-B3-ParentSpanId").isEmpty)
    assertEquals(headersMap.get("X-B3-SpanId"), Some("4321"))
    assertEquals(headersMap.get("X-B3-Sampled"), Some("1"))
  }

  test("not inject anything if there is no Span in the Context") {
    val headersMap = mutable.Map.empty[String, String]
    b3Propagation.write(Context.Empty, headerWriterFromMap(headersMap))
    assert(headersMap.values.isEmpty)
  }

  test("extract a RemoteSpan from incoming headers when all fields are set") {
    val headersMap = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-ParentSpanId" -> "2222",
      "X-B3-SpanId" -> "4321",
      "X-B3-Sampled" -> "1",
      "X-B3-Extra-Baggage" -> "some=baggage;more=baggage"
    )

    val spanContext = b3Propagation.read(headerReaderFromMap(headersMap), Context.Empty).get(Span.Key)
    assertEquals(spanContext.id.string, "4321")
    assertEquals(spanContext.parentId.string, "2222")
    assertEquals(spanContext.trace.id.string, "1234")
    assertEquals(spanContext.trace.samplingDecision, SamplingDecision.Sample)
  }

  test("decode the sampling decision based on the X-B3-Sampled header") {
    val sampledHeaders = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321",
      "X-B3-Sampled" -> "1"
    )

    val notSampledHeaders = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321",
      "X-B3-Sampled" -> "0"
    )

    val noSamplingHeaders = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321"
    )

    assertEquals(
      b3Propagation.read(headerReaderFromMap(sampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.Sample
    )

    assertEquals(
      b3Propagation.read(headerReaderFromMap(notSampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.DoNotSample
    )

    assertEquals(
      b3Propagation.read(headerReaderFromMap(noSamplingHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.Unknown
    )
  }

  test("not include the X-B3-Sampled header if the sampling decision is unknown") {
    val context = testContext()
    val sampledSpan = context.get(Span.Key)
    val notSampledSpanContext = Context.Empty.withEntry(
      Span.Key,
      new Span.Remote(sampledSpan.id, sampledSpan.parentId, Trace(sampledSpan.trace.id, SamplingDecision.DoNotSample))
    )
    val unknownSamplingSpanContext = Context.Empty.withEntry(
      Span.Key,
      new Span.Remote(sampledSpan.id, sampledSpan.parentId, Trace(sampledSpan.trace.id, SamplingDecision.Unknown))
    )
    val headersMap = mutable.Map.empty[String, String]

    b3Propagation.write(context, headerWriterFromMap(headersMap))
    assertEquals(headersMap.get("X-B3-Sampled"), Some("1"))
    headersMap.clear()

    b3Propagation.write(notSampledSpanContext, headerWriterFromMap(headersMap))
    assertEquals(headersMap.get("X-B3-Sampled"), Some("0"))
    headersMap.clear()

    b3Propagation.write(unknownSamplingSpanContext, headerWriterFromMap(headersMap))
    assert(headersMap.get("X-B3-Sampled").isEmpty)
  }

  test("use the Debug flag to override the sampling decision, if provided") {
    val headers = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321",
      "X-B3-Sampled" -> "0",
      "X-B3-Flags" -> "1"
    )

    val span = b3Propagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
    assertEquals(span.trace.samplingDecision, SamplingDecision.Sample)
  }

  test("fall back to sampling header if debug flag is set to any other value") {
    val sampledHeaders = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321",
      "X-B3-Sampled" -> "1",
      "X-B3-Flags" -> "0"
    )

    val notSampledHeaders = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321",
      "X-B3-Sampled" -> "0",
      "X-B3-Flags" -> "0"
    )

    val noSamplingHeaders = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321",
      "X-B3-Flags" -> "0"
    )

    assertEquals(
      b3Propagation.read(headerReaderFromMap(sampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.Sample
    )

    assertEquals(
      b3Propagation.read(headerReaderFromMap(notSampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.DoNotSample
    )

    assertEquals(
      b3Propagation.read(headerReaderFromMap(noSamplingHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.Unknown
    )
  }

  test("use the Debug flag as sampling decision when Sampled is not provided") {
    val headers = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321",
      "X-B3-Flags" -> "1"
    )

    val span = b3Propagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
    assertEquals(span.trace.samplingDecision, SamplingDecision.Sample)
  }

  test("extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID") {
    val headers = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321"
    )

    val span = b3Propagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
    assertEquals(span.id.string, "4321")
    assertEquals(span.parentId, Identifier.Empty)
    assertEquals(span.trace.id.string, "1234")
    assertEquals(span.trace.samplingDecision, SamplingDecision.Unknown)
  }

  test("do not extract a SpanContext if Trace ID and Span ID are not provided") {
    val onlyTraceID = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-Sampled" -> "0",
      "X-B3-Flags" -> "1"
    )

    val onlySpanID = Map(
      "X-B3-SpanId" -> "1234",
      "X-B3-Sampled" -> "0",
      "X-B3-Flags" -> "1"
    )

    val noIds = Map(
      "X-B3-Sampled" -> "0",
      "X-B3-Flags" -> "1"
    )

    assertEquals(b3Propagation.read(headerReaderFromMap(onlyTraceID), Context.Empty).get(Span.Key), Span.Empty)
    assertEquals(b3Propagation.read(headerReaderFromMap(onlySpanID), Context.Empty).get(Span.Key), Span.Empty)
    assertEquals(b3Propagation.read(headerReaderFromMap(noIds), Context.Empty).get(Span.Key), Span.Empty)
  }

  test("round trip a Span from TextMap -> Context -> TextMap") {
    val headers = Map(
      "X-B3-TraceId" -> "1234",
      "X-B3-SpanId" -> "4321",
      "X-B3-ParentSpanId" -> "2222",
      "X-B3-Sampled" -> "1"
    )

    val writenHeaders = mutable.Map.empty[String, String]
    val context = b3Propagation.read(headerReaderFromMap(headers), Context.Empty)
    b3Propagation.write(context, headerWriterFromMap(writenHeaders))
    assertEquals(writenHeaders.toMap, headers)
  }

  def headerReaderFromMap(map: Map[String, String]): HttpPropagation.HeaderReader = new HttpPropagation.HeaderReader {
    override def read(header: String): Option[String] = {
      if (map.get("fail").nonEmpty)
        sys.error("failing on purpose")

      map.get(header)
    }

    override def readAll(): Map[String, String] = map
  }

  def headerWriterFromMap(map: mutable.Map[String, String]): HttpPropagation.HeaderWriter =
    new HttpPropagation.HeaderWriter {
      override def write(header: String, value: String): Unit = map.put(header, value)
    }

  def testContext(): Context =
    Context.of(
      Span.Key,
      new Span.Remote(
        id = Identifier("4321", Array[Byte](4, 3, 2, 1)),
        parentId = Identifier("2222", Array[Byte](2, 2, 2, 2)),
        trace = Trace(
          id = Identifier("1234", Array[Byte](1, 2, 3, 4)),
          samplingDecision = SamplingDecision.Sample
        )
      )
    )

  def testContextWithoutParent(): Context =
    Context.of(
      Span.Key,
      new Span.Remote(
        id = Identifier("4321", Array[Byte](4, 3, 2, 1)),
        parentId = Identifier.Empty,
        trace = Trace(
          id = Identifier("1234", Array[Byte](1, 2, 3, 4)),
          samplingDecision = SamplingDecision.Sample
        )
      )
    )

}
