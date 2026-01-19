package kamon.trace

import kamon.context.{Context, HttpPropagation}
import kamon.trace.Trace.SamplingDecision
import munit.FunSuite

import scala.collection.mutable

class DataDogSpanPropagationSpec extends FunSuite {
  val dataDogPropagation = SpanPropagation.DataDog()

  test("write the Span data into headers") {
    val headersMap = mutable.Map.empty[String, String]
    dataDogPropagation.write(testContext(), headerWriterFromMap(headersMap))

    assertEquals(headersMap.get("x-datadog-trace-id"), Some(unsignedLongString("1234")))
    // not a typo, span id should be set as `x-datadog-parent-id`
    assertEquals(headersMap.get("x-datadog-parent-id"), Some(unsignedLongString("4321")))
    assertEquals(headersMap.get("x-datadog-sampling-priority"), Some("1"))
  }

  test("not inject anything if there is no Span in the Context") {
    val headersMap = mutable.Map.empty[String, String]
    dataDogPropagation.write(Context.Empty, headerWriterFromMap(headersMap))
    assert(headersMap.values.isEmpty)
  }

  test("extract a RemoteSpan from incoming headers when all fields are set") {
    val headersMap = Map(
      "x-datadog-trace-id" -> unsignedLongString("1234"),
      "x-datadog-parent-id" -> unsignedLongString("4321"),
      "x-datadog-sampling-priority" -> "1"
    )

    val spanContext = dataDogPropagation.read(headerReaderFromMap(headersMap), Context.Empty).get(Span.Key)
    assertEquals(spanContext.id.string, "4321")
    assertEquals(spanContext.trace.id.string, "1234")
    assertEquals(spanContext.trace.samplingDecision, SamplingDecision.Sample)
    assertEquals(spanContext.parentId, Identifier.Empty)
  }

  test("decode the sampling decision based on the x-datadog-sampling-priority header") {
    val sampledHeaders = Map(
      "x-datadog-trace-id" -> unsignedLongString("1234"),
      "x-datadog-parent-id" -> unsignedLongString("4321"),
      "x-datadog-sampling-priority" -> "1"
    )

    val notSampledHeaders = Map(
      "x-datadog-trace-id" -> unsignedLongString("1234"),
      "x-datadog-parent-id" -> unsignedLongString("4321"),
      "x-datadog-sampling-priority" -> "0"
    )

    val noSamplingHeaders = Map(
      "x-datadog-trace-id" -> unsignedLongString("1234"),
      "x-datadog-parent-id" -> unsignedLongString("4321")
    )

    assertEquals(
      dataDogPropagation.read(headerReaderFromMap(sampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.Sample
    )

    assertEquals(
      dataDogPropagation.read(headerReaderFromMap(notSampledHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.DoNotSample
    )

    assertEquals(
      dataDogPropagation.read(headerReaderFromMap(noSamplingHeaders), Context.Empty)
        .get(Span.Key).trace.samplingDecision,
      SamplingDecision.Unknown
    )
  }

  test("not include the x-datadog-sampling-priority header if the sampling decision is unknown") {
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

    dataDogPropagation.write(context, headerWriterFromMap(headersMap))
    assertEquals(headersMap.get("x-datadog-sampling-priority"), Some("1"))
    headersMap.clear()

    dataDogPropagation.write(notSampledSpanContext, headerWriterFromMap(headersMap))
    assertEquals(headersMap.get("x-datadog-sampling-priority"), Some("0"))
    headersMap.clear()

    dataDogPropagation.write(unknownSamplingSpanContext, headerWriterFromMap(headersMap))
    assert(headersMap.get("x-datadog-sampling-priority").isEmpty)
  }

  test("extract a minimal SpanContext from a TextMap containing only the Trace ID and Span ID") {
    val headers = Map(
      "x-datadog-trace-id" -> unsignedLongString("1234"),
      "x-datadog-parent-id" -> unsignedLongString("4321")
    )

    val span = dataDogPropagation.read(headerReaderFromMap(headers), Context.Empty).get(Span.Key)
    assertEquals(span.id.string, "4321")
    assertEquals(span.parentId, Identifier.Empty)
    assertEquals(span.trace.id.string, "1234")
    assertEquals(span.trace.samplingDecision, SamplingDecision.Unknown)
  }

  test("round trip a Span from TextMap -> Context -> TextMap") {
    val headers = Map(
      "x-datadog-trace-id" -> unsignedLongString("1234"),
      "x-datadog-parent-id" -> unsignedLongString("4321"),
      "x-datadog-sampling-priority" -> "1"
    )

    val writenHeaders = mutable.Map.empty[String, String]
    val context = dataDogPropagation.read(headerReaderFromMap(headers), Context.Empty)
    dataDogPropagation.write(context, headerWriterFromMap(writenHeaders))
    assertEquals(writenHeaders.toMap, headers)
  }

  test("decode unsigned long to expected hex value") {
    val expectedHex1 = "0"
    val actualHex1 = SpanPropagation.DataDog.decodeUnsignedLongToHex("0")
    assertEquals(expectedHex1, actualHex1)

    val expectedHex2 = "ff"
    val actualHex2 = SpanPropagation.DataDog.decodeUnsignedLongToHex("255")
    assertEquals(expectedHex2, actualHex2)

    val expectedHex3 = "c5863f7d672b65bf"
    val actualHex3 = SpanPropagation.DataDog.decodeUnsignedLongToHex("14233133480185390527")
    assertEquals(expectedHex3, actualHex3)

    val expectedHex4 = "ffffffffffffffff"
    val actualHex4 = SpanPropagation.DataDog.decodeUnsignedLongToHex("18446744073709551615")
    assertEquals(expectedHex4, actualHex4)
  }

  def unsignedLongString(id: String): String = BigInt(id, 16).toString

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
