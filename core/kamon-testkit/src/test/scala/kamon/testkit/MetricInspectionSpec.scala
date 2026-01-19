package kamon.testkit

import kamon.Kamon
import kamon.tag.TagSet
import _root_.munit.FunSuite

class MetricInspectionSpec extends FunSuite with MetricInspection.Syntax {

  test("extract instruments for given metric and TagSet") {
    val metric = Kamon.rangeSampler("sampler")
    val ts1 = TagSet.of("1", "one")
    val ts2 = TagSet.of("2", "two")

    metric.withTags(ts1).increment(1)
    metric.withTags(ts2).increment(2)

    val instrumentTags = metric.instruments().values.map(_.tags).toSet
    assert(instrumentTags.contains(ts1))
    assert(instrumentTags.contains(ts2))
    assertEquals(metric.instruments(ts1).keys.headOption, Some(ts1))
  }

  test("extract all possible values for a tag") {
    val metric = Kamon.counter("test-counter")
    metric.withTag("season", "summer").increment()
    metric.withTag("season", "winter").increment()
    metric.withTag("season", "spring").increment()
    metric.withTag("season", "autumn").increment()

    assert(metric.tagValues("unknown").isEmpty)

    val seasons = metric.tagValues("season")
    assert(seasons.contains("summer"))
    assert(seasons.contains("winter"))
    assert(seasons.contains("spring"))
    assert(seasons.contains("autumn"))
  }
}
