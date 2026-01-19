package kamon.context

import com.typesafe.config.ConfigFactory
import kamon.context.HttpPropagation.{HeaderReader, HeaderWriter}
import kamon.context.Propagation.{EntryReader, EntryWriter}
import kamon.tag.Lookups._
import kamon.tag.TagSet
import munit.FunSuite

import scala.collection.mutable

class HttpPropagationSpec extends FunSuite {

  test("reading returns an empty context if there are no tags nor keys") {
    val context = httpPropagation.read(headerReaderFromMap(Map.empty))
    assert(context.isEmpty())
  }

  test("reading fetches tags when available") {
    val headers = Map(
      "x-content-tags" -> "hello=world;correlation=1234",
      "x-mapped-tag" -> "value"
    )

    val context = httpPropagation.read(headerReaderFromMap(headers))
    assertEquals(context.tags.get(plain("hello")), "world")
    assertEquals(context.tags.get(plain("correlation")), "1234")
    assertEquals(context.tags.get(plain("mappedTag")), "value")
  }

  test("reading handles header errors by returning empty context") {
    val headers = Map("fail" -> "")
    val context = httpPropagation.read(headerReaderFromMap(headers))
    assert(context.tags.isEmpty)
    assert(context.entries.isEmpty)

  }

  test("reading fetches tags and entries") {
    val headers = Map(
      "x-content-tags" -> "hello=world;correlation=1234",
      "string-header" -> "hey",
      "integer-header" -> "123"
    )

    val context = httpPropagation.read(headerReaderFromMap(headers))
    assertEquals(context.get(HttpPropagationSpec.StringKey), "hey")
    assertEquals(context.get(HttpPropagationSpec.IntegerKey), 123)
    assertEquals(context.get(HttpPropagationSpec.OptionalKey), Option.empty[String])
    assertEquals(context.getTag(plain("hello")), "world")
    assertEquals(context.getTag(option("correlation")), Some("1234"))
    assertEquals(context.getTag(option("unknown")), Option.empty[String])
  }

  test("reading filters out configured tags") {
    val headers = Map(
      "x-content-tags" -> "hello=world;correlation=1234;privateMappedTag=value;myLocalTag=value",
      "string-header" -> "hey",
      "integer-header" -> "123"
    )

    val context = httpPropagation.read(headerReaderFromMap(headers))
    assertEquals(context.get(HttpPropagationSpec.StringKey), "hey")
    assertEquals(context.get(HttpPropagationSpec.IntegerKey), 123)
    assertEquals(context.get(HttpPropagationSpec.OptionalKey), Option.empty[String])
    assertEquals(context.getTag(plain("hello")), "world")
    assertEquals(context.getTag(option("correlation")), Some("1234"))
    assertEquals(context.getTag(option("unknown")), Option.empty[String])
    assertEquals(context.getTag(option("myLocalTag")), Option.empty[String])
    assertEquals(context.getTag(option("privateMappedTag")), Option.empty[String])
  }

  test("writing adds the upstream name when context is empty") {
    val headers = mutable.Map.empty[String, String]
    httpPropagation.write(Context.Empty, headerWriterFromMap(headers))
    assertEquals(headers.toMap, Map("x-content-tags" -> "upstream.name=kamon-application;"))
  }

  test("writing emits context tags") {
    val headers = mutable.Map.empty[String, String]
    val context = Context.of(TagSet.from(Map(
      "hello" -> "world",
      "mappedTag" -> "value"
    )))

    httpPropagation.write(context, headerWriterFromMap(headers))
    assertEquals(headers.toMap,
      Map(
        "x-content-tags" -> "hello=world;upstream.name=kamon-application;",
        "x-mapped-tag" -> "value"
      )
    )
  }

  test("writing emits context entries") {
    val headers = mutable.Map.empty[String, String]
    val context = Context.of(
      HttpPropagationSpec.StringKey,
      "out-we-go",
      HttpPropagationSpec.IntegerKey,
      42
    )

    httpPropagation.write(context, headerWriterFromMap(headers))
    assertEquals(headers.toMap,
      Map(
        "x-content-tags" -> "upstream.name=kamon-application;",
        "string-header" -> "out-we-go"
      )
    )
  }

  test("writing filters out configured tags") {
    val headers = mutable.Map.empty[String, String]
    val context = Context.of(TagSet.from(Map(
      "hello" -> "world",
      "mappedTag" -> "value",
      "privateHello" -> "world",
      "privateMappedTag" -> "value",
      "myLocalTag" -> "value"
    )))

    httpPropagation.write(context, headerWriterFromMap(headers))
    assertEquals(headers.toMap,
      Map(
        "x-content-tags" -> "hello=world;upstream.name=kamon-application;",
        "x-mapped-tag" -> "value"
      )
    )
  }

  private val httpPropagation = HttpPropagation.from(
    ConfigFactory.parseString(
      """
        |tags {
        |  header-name = "x-content-tags"
        |  include-upstream-name = yes
        |  filter = ["private*", "myLocalTag"]
        |  mappings {
        |    mappedTag = "x-mapped-tag"
        |  }
        |}
        |
        |entries.incoming.string = "kamon.context.HttpPropagationSpec$StringEntryCodec"
        |entries.incoming.integer = "kamon.context.HttpPropagationSpec$IntegerEntryCodec"
        |entries.outgoing.string = "kamon.context.HttpPropagationSpec$StringEntryCodec"
        |
      """.stripMargin
    ).withFallback(ConfigFactory.load().getConfig("kamon.propagation")),
    identifierScheme = "single"
  )

  private def headerReaderFromMap(map: Map[String, String]): HttpPropagation.HeaderReader =
    new HttpPropagation.HeaderReader {
      override def read(header: String): Option[String] = {
        if (map.get("fail").nonEmpty)
          sys.error("failing on purpose")

        map.get(header)
      }

      override def readAll(): Map[String, String] = map
    }

  private def headerWriterFromMap(map: mutable.Map[String, String]): HttpPropagation.HeaderWriter =
    new HttpPropagation.HeaderWriter {
      override def write(header: String, value: String): Unit = map.put(header, value)
    }
}

object HttpPropagationSpec {

  val StringKey = Context.key[String]("string", null)
  val IntegerKey = Context.key[Int]("integer", 0)
  val OptionalKey = Context.key[Option[String]]("optional", None)

  class StringEntryCodec extends EntryReader[HeaderReader] with EntryWriter[HeaderWriter] {
    private val HeaderName = "string-header"

    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      reader
        .read(HeaderName)
        .map(v => context.withEntry(StringKey, v))
        .getOrElse(context)
    }

    override def write(context: Context, writer: HttpPropagation.HeaderWriter): Unit = {
      Option(context.get(StringKey)).foreach(v => writer.write(HeaderName, v))
    }
  }

  class IntegerEntryCodec extends EntryReader[HeaderReader] {
    override def read(reader: HttpPropagation.HeaderReader, context: Context): Context = {
      reader
        .read("integer-header")
        .map(v => context.withEntry(IntegerKey, v.toInt))
        .getOrElse(context)

    }
  }
}
