/*
 * =========================================================================================
 * Copyright © 2013-2018 the kamon project <http://kamon.io/>
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

class UberSpanPropagationSpec extends FunSuite {
  import SpanPropagation.Uber
  val uberPropagation = Uber()

  test("return a TextMap containing the SpanContext data") {
    val headersMap = mutable.Map.empty[String, String]
    uberPropagation.write(testContext(), headerWriterFromMap(headersMap))

    assertEquals(headersMap.get("uber-trace-id"), Some("1234:4321:2222:1"))
  }

  test("do not include the ParentSpanId if there is no parent") {
    val headersMap = mutable.Map.empty[String, String]
    uberPropagation.write(testContextWithoutParent(), headerWriterFromMap(headersMap))

    assertEquals(headersMap.get(Uber.HeaderName), Some("1234:4321:0:1"))
  }

  test("not inject anything if there is no Span in the Context") {
    val headersMap = mutable.Map.empty[String, String]
    uberPropagation.write(Context.Empty, headerWriterFromMap(headersMap))

    assert(headersMap.values.isEmpty)
  }

  test("extract a RemoteSpan from a TextMap when all fields are set") {
    val headersMap = Map(Uber.HeaderName -> "1234:4321:2222:1")

    val span = uberPropagation.read(headerReaderFromMap(headersMap), Context.Empty).get(Span.Key)

    assertEquals(span.id.string, "4321")
    assertEquals(span.parentId.string, "2222")
    assertEquals(span.trace.id.string, "1234")
    assertEquals(span.trace.samplingDecision, SamplingDecision.Sample)
  }

  test("decode the sampling decision based on the 'uber-trace-id' header") {
    val sampledHeadersMap = Map(Uber.HeaderName -> "1234:4321:0:1")
    val notSampledHeadersMap = Map(Uber.HeaderName -> "1234:4321:0:0")
    val noSamplingHeadersMap = Map(Uber.HeaderName -> "1234:4321") // is not part of the spec

    assertEquals(
      uberPropagation.read(headerReaderFromMap(sampledHeadersMap), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.Sample
    )

    assertEquals(
      uberPropagation.read(headerReaderFromMap(notSampledHeadersMap), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.DoNotSample
    )

    assertEquals(
      uberPropagation.read(headerReaderFromMap(noSamplingHeadersMap), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.Unknown
    )
  }

  test("include the sampled header if the sampling decision is unknown") {
    val context = testContext()
    val sampledSpan = context.get(Span.Key)
    val notSampledSpanContext = Context.Empty.withEntry(
      Span.Key,
      Span.Remote(sampledSpan.id, sampledSpan.parentId, Trace(sampledSpan.trace.id, SamplingDecision.DoNotSample))
    )
    val unknownSamplingSpanContext = Context.Empty.withEntry(
      Span.Key,
      Span.Remote(sampledSpan.id, sampledSpan.parentId, Trace(sampledSpan.trace.id, SamplingDecision.Unknown))
    )

    val headersMap = mutable.Map.empty[String, String]

    uberPropagation.write(context, headerWriterFromMap(headersMap))
    assertEquals(headersMap.get(Uber.HeaderName), Some("1234:4321:2222:1"))
    headersMap.clear()

    uberPropagation.write(notSampledSpanContext, headerWriterFromMap(headersMap))
    assertEquals(headersMap.get(Uber.HeaderName), Some("1234:4321:2222:0"))
    headersMap.clear()

    uberPropagation.write(unknownSamplingSpanContext, headerWriterFromMap(headersMap))
    assertEquals(headersMap.get(Uber.HeaderName), Some("1234:4321:2222:0"))
    headersMap.clear()
  }

  test("use the Debug flag to override the sampling decision, if provided") {
    val headers = Map(Uber.HeaderName -> "1234:4321:2222:d")

    val span = uberPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
    assertEquals(span.trace.samplingDecision, SamplingDecision.Sample)
  }

  test("use the Debug flag as sampling decision when Sampled is not provided") {
    val headers = Map(Uber.HeaderName -> "1234:4321:0:d")

    val span = uberPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
    assertEquals(span.trace.samplingDecision, SamplingDecision.Sample)
  }

  test("extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID") {
    val headers = Map(Uber.HeaderName -> "1234:4321")

    val span = uberPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
    assertEquals(span.id.string, "4321")
    assertEquals(span.parentId, Identifier.Empty)
    assertEquals(span.trace.id.string, "1234")
    assertEquals(span.trace.samplingDecision, SamplingDecision.Unknown)
  }

  test("do not extract a SpanContext if Trace ID and Span ID are not provided") {
    val onlyTraceID = Map(Uber.HeaderName -> "1234::0")
    val onlySpanID = Map(Uber.HeaderName -> ":4321:d")
    val noIds = Map(Uber.HeaderName -> "::0")

    assertEquals(uberPropagation.read(headerReaderFromMap(onlyTraceID), Context.Empty).get(Span.Key), Span.Empty)
    assertEquals(uberPropagation.read(headerReaderFromMap(onlySpanID), Context.Empty).get(Span.Key), Span.Empty)
    assertEquals(uberPropagation.read(headerReaderFromMap(noIds), Context.Empty).get(Span.Key), Span.Empty)
  }

  test("round trip a Span from TextMap -> Context -> TextMap") {
    val headers = Map(Uber.HeaderName -> "1234:4312:2222:1")

    val writenHeaders = mutable.Map.empty[String, String]
    val context = uberPropagation.read(headerReaderFromMap(headers), Context.Empty)
    uberPropagation.write(context, headerWriterFromMap(writenHeaders))
    assertEquals(
      writenHeaders.map { case (k, v) =>
        k -> SpanPropagation.Util.urlDecode(v)
      }.toMap,
      headers
    )
  }

  test("extract a SpanContext from a URL-encoded header") {
    val headers = Map(Uber.HeaderName -> "1234:5678:4321:1")

    val span = uberPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
    assertEquals(span.id.string, "5678")
    assertEquals(span.parentId.string, "4321")
    assertEquals(span.trace.id.string, "1234")
    assertEquals(span.trace.samplingDecision, SamplingDecision.Sample)
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
