package kamon.metric

import kamon.Kamon
import kamon.tag.TagSet
import kamon.testkit.MetricInspection
import munit.FunSuite

class InstrumentGroupSuite extends FunSuite with MetricInspection.Syntax {

  val Counter = Kamon.counter("metric.group.counter")
  val Gauge = Kamon.gauge("metric.group.gauge")
  val Histogram = Kamon.histogram("metric.group.histogram")
  val RangeSampler = Kamon.rangeSampler("metric.group.range-sampler")

  test("register instruments with common tags and remove them when cleaning up") {
    val group = new CommonTagsOnly(TagSet.of("type", "common"))

    assertEquals(Counter.tagValues("type"), Seq("common"))
    assertEquals(Gauge.tagValues("type"), Seq("common"))
    assertEquals(Histogram.tagValues("type"), Seq("common"))
    assertEquals(RangeSampler.tagValues("type"), Seq("common"))

    group.remove()

    assert(Counter.tagValues("type").isEmpty)
    assert(Gauge.tagValues("type").isEmpty)
    assert(Histogram.tagValues("type").isEmpty)
    assert(RangeSampler.tagValues("type").isEmpty)
  }

  test("override common tags with tags supplied to the register method") {
    val group = new MixedTags(TagSet.of("type", "basic"))

    assertEquals(Counter.tagValues("type"), Seq("basic"))
    assertEquals(Gauge.tagValues("type"), Seq("simple"))
    assertEquals(Histogram.tagValues("type"), Seq("42"))
    assertEquals(RangeSampler.tagValues("type"), Seq("true"))

    group.remove()

    assert(Counter.tagValues("type").isEmpty)
    assert(Gauge.tagValues("type").isEmpty)
    assert(Histogram.tagValues("type").isEmpty)
    assert(RangeSampler.tagValues("type").isEmpty)
  }

  class CommonTagsOnly(tags: TagSet) extends InstrumentGroup(tags) {
    val counter = register(Counter)
    val gauge = register(Gauge)
    val histogram = register(Histogram)
    val rangeSampler = register(RangeSampler)
  }

  class MixedTags(tags: TagSet) extends InstrumentGroup(tags) {
    val counter = register(Counter)
    val gauge = register(Gauge, "type", "simple")
    val histogram = register(Histogram, "type", 42)
    val rangeSampler = register(RangeSampler, "type", true)
  }
}
