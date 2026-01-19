package kamon.context

import java.io.ByteArrayOutputStream
import com.typesafe.config.ConfigFactory
import kamon.context.BinaryPropagation.{ByteStreamReader, ByteStreamWriter}
import kamon.context.Propagation.{EntryReader, EntryWriter}
import kamon.tag.TagSet
import kamon.tag.Lookups._
import munit.FunSuite

import scala.util.Random

class BinaryPropagationSpec extends FunSuite {

  test("reading returns an empty context if there is no data") {
    val context = binaryPropagation.read(ByteStreamReader.of(Array.ofDim[Byte](0)))
    assert(context.isEmpty())
  }

  test("writing an empty context produces no data") {
    val writer = inspectableByteStreamWriter()
    binaryPropagation.write(Context.Empty, writer)
    assertEquals(writer.size(), 0)
  }

  test("reading handles malformed data") {
    val randomBytes = Array.ofDim[Byte](42)
    Random.nextBytes(randomBytes)

    val context = binaryPropagation.read(ByteStreamReader.of(randomBytes))
    assert(context.isEmpty())
  }

  test("reading handles entry reader errors") {
    val context = Context.of(
      BinaryPropagationSpec.StringKey,
      "string-value",
      BinaryPropagationSpec.FailStringKey,
      "fail-read"
    )
    val writer = inspectableByteStreamWriter()
    binaryPropagation.write(context, writer)

    val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
    assertEquals(rtContext.tags.get(plain("upstream.name")), "kamon-application")
    assertEquals(rtContext.get(BinaryPropagationSpec.StringKey), "string-value")
    assertEquals(rtContext.get(BinaryPropagationSpec.FailStringKey), null)
  }

  test("writing handles entry writer errors") {
    val context = Context.of(
      BinaryPropagationSpec.StringKey,
      "string-value",
      BinaryPropagationSpec.FailStringKey,
      "fail-write"
    )
    val writer = inspectableByteStreamWriter()
    binaryPropagation.write(context, writer)

    val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
    assertEquals(rtContext.tags.get(plain("upstream.name")), "kamon-application")
    assertEquals(rtContext.get(BinaryPropagationSpec.StringKey), "string-value")
    assertEquals(rtContext.get(BinaryPropagationSpec.FailStringKey), null)
  }

  test("writing fails gracefully when context exceeds max size") {
    val context = Context.of(BinaryPropagationSpec.StringKey, "string-value" * 20)
    val writer = inspectableByteStreamWriter()
    binaryPropagation.write(context, writer)

    val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
    assert(rtContext.isEmpty())
  }

  test("round-trip tags only") {
    val context = Context.of(TagSet.from(Map("hello" -> "world", "kamon" -> "rulez")))
    val writer = inspectableByteStreamWriter()
    binaryPropagation.write(context, writer)

    val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
    assert(rtContext.entries.isEmpty)
    assertEquals(rtContext.tags.get(plain("hello")), "world")
    assertEquals(rtContext.tags.get(plain("kamon")), "rulez")
  }

  test("round-trip entries only") {
    val context = Context.of(BinaryPropagationSpec.StringKey, "string-value", BinaryPropagationSpec.IntegerKey, 42)
    val writer = inspectableByteStreamWriter()
    binaryPropagation.write(context, writer)

    val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))
    assertEquals(rtContext.tags.get(plain("upstream.name")), "kamon-application")
    assertEquals(rtContext.get(BinaryPropagationSpec.StringKey), "string-value")
    assertEquals(rtContext.get(BinaryPropagationSpec.IntegerKey), 0)
  }

  test("round-trip tags and entries") {
    val context = Context.of(TagSet.from(Map("hello" -> "world", "kamon" -> "rulez")))
      .withEntry(BinaryPropagationSpec.StringKey, "string-value")
      .withEntry(BinaryPropagationSpec.IntegerKey, 42)

    val writer = inspectableByteStreamWriter()
    binaryPropagation.write(context, writer)
    val rtContext = binaryPropagation.read(ByteStreamReader.of(writer.toByteArray))

    assertEquals(rtContext.tags.get(plain("hello")), "world")
    assertEquals(rtContext.tags.get(plain("kamon")), "rulez")
    assertEquals(rtContext.get(BinaryPropagationSpec.StringKey), "string-value")
    assertEquals(rtContext.get(BinaryPropagationSpec.IntegerKey), 0)
  }

  private val binaryPropagation = BinaryPropagation.from(
    ConfigFactory.parseString(
      """
        |max-outgoing-size = 128
        |tags.include-upstream-name = yes
        |entries.incoming.string = "kamon.context.BinaryPropagationSpec$StringEntryCodec"
        |entries.incoming.failString = "kamon.context.BinaryPropagationSpec$FailStringEntryCodec"
        |entries.outgoing.string = "kamon.context.BinaryPropagationSpec$StringEntryCodec"
        |entries.outgoing.failString = "kamon.context.BinaryPropagationSpec$FailStringEntryCodec"
        |
      """.stripMargin
    ).withFallback(ConfigFactory.load().getConfig("kamon.propagation"))
  )

  private def inspectableByteStreamWriter() = new ByteArrayOutputStream(32) with ByteStreamWriter

}

object BinaryPropagationSpec {

  val StringKey = Context.key[String]("string", null)
  val FailStringKey = Context.key[String]("failString", null)
  val IntegerKey = Context.key[Int]("integer", 0)

  class StringEntryCodec extends EntryReader[ByteStreamReader] with EntryWriter[ByteStreamWriter] {

    override def read(medium: ByteStreamReader, context: Context): Context = {
      val valueData = medium.readAll()

      if (valueData.length > 0) {
        context.withEntry(StringKey, new String(valueData))
      } else context
    }

    override def write(context: Context, medium: ByteStreamWriter): Unit = {
      val value = context.get(StringKey)
      if (value != null) {
        medium.write(value.getBytes)
      }
    }
  }

  class FailStringEntryCodec extends EntryReader[ByteStreamReader] with EntryWriter[ByteStreamWriter] {

    override def read(medium: ByteStreamReader, context: Context): Context = {
      val valueData = medium.readAll()

      if (valueData.length > 0) {
        val stringValue = new String(valueData)
        if (stringValue == "fail-read") {
          sys.error("The fail string entry reader has triggered")
        }

        context.withEntry(FailStringKey, stringValue)
      } else context
    }

    override def write(context: Context, medium: ByteStreamWriter): Unit = {
      val value = context.get(FailStringKey)
      if (value != null && value != "fail-write") {
        medium.write(value.getBytes)
      } else {
        medium.write(42)
        sys.error("The fail string entry writer has triggered")
      }
    }
  }
}
