package kamon.tag

import munit.FunSuite

import java.util.Optional
import scala.jdk.CollectionConverters._

class TagSetSpec extends FunSuite {
  import Lookups._

  private val NullString: java.lang.String = null
  private val NullBoolean: java.lang.Boolean = null
  private val NullLong: java.lang.Long = null
  private val EmptyString: java.lang.String = ""

  private val GoodScalaTagMap: Map[String, Any] = Map(
    "age" -> 5L,
    "name" -> "Kamon",
    "isAwesome" -> true
  )

  private val BadScalaTagMap: Map[String, Any] = Map(
    NullString -> NullString,
    EmptyString -> NullString,
    NullString -> NullString,
    EmptyString -> NullString,
    EmptyString -> "value",
    NullString -> "value",
    "key" -> NullString,
    "key" -> NullBoolean,
    "key" -> NullLong
  )

  private val GoodJavaTagMap = GoodScalaTagMap.asJava
  private val BadJavaTagMap = BadScalaTagMap.asJava

  test("silently drop null and unacceptable keys/values from builders") {
    assertEquals(TagSet.of(NullString, NullString).all().size, 0)
    assertEquals(TagSet.of(EmptyString, NullString).all().size, 0)
    assertEquals(TagSet.of(EmptyString, "value").all().size, 0)
    assertEquals(TagSet.of(NullString, "value").all().size, 0)
    assertEquals(TagSet.of("key", NullString).all().size, 0)
    assertEquals(TagSet.of("key", NullBoolean).all().size, 0)
    assertEquals(TagSet.of("key", NullLong).all().size, 0)

    assertEquals(TagSet.from(BadScalaTagMap).all().size, 0)
    assertEquals(TagSet.from(BadJavaTagMap).all().size, 0)
  }

  test("silently drop null keys/values when using withTag/withTags") {
    val tags = TagSet.of("initialKey", "initialValue")
      .withTag(NullString, NullString)
      .withTag(EmptyString, NullString)
      .withTag(EmptyString, "value")
      .withTag(NullString, "value")
      .withTag("key", NullString)
      .withTag("key", NullBoolean)
      .withTag("key", NullLong)
      .withTag(NullString, NullString)
      .withTag(EmptyString, NullString)
      .withTag(EmptyString, "value")
      .withTag(NullString, "value")
      .withTag("key", NullString)
      .withTag("key", NullBoolean)
      .withTag("key", NullLong)

    val entries = tags.all()
    assertEquals(entries.length, 1)
    val head = entries.head.asInstanceOf[Tag.String]
    assertEquals(head.key, "initialKey")
    assertEquals(head.value, "initialValue")
  }

  test("create a properly populated instance when valid pairs are provided") {
    assertEquals(TagSet.of("isAwesome", true).all().size, 1)
    assertEquals(TagSet.of("name", "kamon").all().size, 1)
    assertEquals(TagSet.of("age", 5L).all().size, 1)

    assertEquals(TagSet.from(GoodScalaTagMap).all().size, 3)
    assertEquals(TagSet.from(GoodJavaTagMap).all().size, 3)

    val merged = TagSet.of("initial", "initial")
      .withTag("isAwesome", true)
      .withTag("name", "Kamon")
      .withTag("age", 5L)
      .withTag("isAvailable", true)
      .withTag("website", "kamon.io")
      .withTag("supportedPlatforms", 1L)
      .all()

    assertEquals(merged.size, 7)
  }

  test("override pre-existent tags when merging with other TagSet instances") {
    val leftTags = TagSet.from(GoodScalaTagMap)
    val rightTags = TagSet
      .of("name", "New Kamon")
      .withTag("age", 42L)
      .withTag("isAwesome", false)

    val tags = leftTags.withTags(rightTags)
    assertEquals(tags.get(plain("name")), "New Kamon")
    assertEquals(tags.get(plainLong("age")), Long.box(42L))
    assertEquals(tags.get(plainBoolean("isAwesome")), Boolean.box(false))

    val andTags = tags.withTags(leftTags)
    assertEquals(andTags.get(plain("name")), "Kamon")
    assertEquals(andTags.get(plainLong("age")), Long.box(5L))
    assertEquals(andTags.get(plainBoolean("isAwesome")), Boolean.box(true))
  }

  test("provide typed access to the contained pairs when looking up values") {
    val tags = TagSet.from(GoodScalaTagMap)

    assertEquals(tags.get(plain("name")), "Kamon")
    assertEquals(tags.get(plain("none")), null)
    assertEquals(tags.get(option("name")), Option("Kamon"))
    assertEquals(tags.get(option("none")), Option.empty[String])
    assertEquals(tags.get(optional("name")), Optional.of("Kamon"))
    assertEquals(tags.get(optional("none")), Optional.empty[String])

    assertEquals(tags.get(plainLong("age")), Long.box(5L))
    assertEquals(tags.get(plainBoolean("isAwesome")), Boolean.box(true))

  }

  test("allow removing keys") {
    val tags = TagSet.from(
      Map(
        "age" -> 5L,
        "name" -> "Kamon",
        "isAwesome" -> true,
        "hasTracing" -> true,
        "website" -> "kamon.io",
        "luckyNumber" -> 7L
      )
    )

    assertEquals(tags.without("name").get(option("name")), Option.empty[String])
    assertEquals(tags.without("website").get(plain("name")), "Kamon")
  }

  private def matchPair(key: String, value: Any): Tag => Boolean = {
    case t: Tag.String  => t.key == key && t.value == value
    case t: Tag.Long    => t.key == key && t.value == value
    case t: Tag.Boolean => t.key == key && t.value == value
  }
}
