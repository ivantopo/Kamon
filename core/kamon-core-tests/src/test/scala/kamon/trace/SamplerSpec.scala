package kamon.trace

import kamon.Kamon
import kamon.trace.Trace.SamplingDecision
import munit.FunSuite

import scala.collection.mutable
import scala.concurrent.duration._

class SamplerSpec extends FunSuite {

  private val _decisions = mutable.Map.empty[String, Int]

  override def beforeEach(context: BeforeEach): Unit =
    resetCounters()

  test("adaptive sampling: spread the throughput across operations when the overall throughput doesn't exceed the global limit") {
    implicit val sampler = adaptiveSampler()

    simulate(10.minutes) {
      (1 to 3).foreach { _ =>
        decide("op4")
        decide("op5")
        decide("op6")
      }
    }

    // Each operations should get 180 traces per minute for 10 minutes, which is below the global
    // throughput goal of 600 traces per minute, shared across all operations. Roughly all traces
    // should be sampled.
    assertWithinTolerance(sampledTraces("op4"), 1800, 50)
    assertWithinTolerance(sampledTraces("op5"), 1800, 50)
    assertWithinTolerance(sampledTraces("op6"), 1800, 50)
  }

  test("adaptive sampling: remove operations with fixed sampled decisions from the balancing algorithm") {
    implicit val sampler = adaptiveSampler()

    simulate(10.minutes) {
      (1 to 3).foreach { _ =>
        decide("op4")
        decide("op5")
        decide("op6")
        decide("op10")
        decide("op11")
      }
    }

    // Each operations should get 180 traces per minute for 10 minutes, which is below the global
    // throughput goal of 600 traces per minute, shared across all operations. Roughly all traces
    // should be sampled.
    assertWithinTolerance(sampledTraces("op4"), 1800, 50)
    assertWithinTolerance(sampledTraces("op5"), 1800, 50)
    assertWithinTolerance(sampledTraces("op6"), 1800, 50)
    assertEquals(sampledTraces("op10"), 0)
    assertEquals(sampledTraces("op11"), 1800)
  }

  test("adaptive sampling: spread the throughput across operations and cap them when the overall throughput exceeds the global limit") {
    implicit val sampler = adaptiveSampler()

    simulate(10.minutes) {
      (1 to 10).foreach { _ =>
        decide("op4")
        decide("op5")
        decide("op6")
      }
    }

    // Each operations should get 600 traces per minute for 10 minutes, which is above the global
    // throughput goal of 600 traces per minute so each operation should be capped to roughly 200
    // traces per minute.
    assertWithinTolerance(sampledTraces("op4"), 2000, 200)
    assertWithinTolerance(sampledTraces("op5"), 2000, 200)
    assertWithinTolerance(sampledTraces("op6"), 2000, 200)
    assertWithinTolerance(totalSampledTraces(), 6000, 600)
  }

  test("adaptive sampling: spread the throughput across operations and respect maximum/minimum throughput rules") {
    implicit val sampler = adaptiveSampler()

    simulate(10.minutes) {
      (1 to 10).foreach { _ =>
        decide("op4")
        decide("op5")
        decide("op6")
        decide("op9")
      }
    }

    // Each operations should get 600 traces per minute for 10 minutes, which is above the global
    // throughput goal of 600 traces per minute so each operation should be capped to roughly 200
    // traces per minute, except for op9 which is capped to 40 per minute.
    assertWithinTolerance(sampledTraces("op4"), 1830, 180)
    assertWithinTolerance(sampledTraces("op5"), 1830, 180)
    assertWithinTolerance(sampledTraces("op6"), 1830, 180)
    assertWithinTolerance(sampledTraces("op9"), 400, 100)
    assertWithinTolerance(totalSampledTraces(), 6000, 600)
  }

  test("adaptive sampling: spread throughput across operations and respect maximum/minimum throughput rules with uneven traffic") {
    implicit val sampler = adaptiveSampler()

    simulate(10.minutes) {
      (1 to 5).foreach { _ =>
        decide("op5")
      }

      (1 to 10).foreach { _ =>
        decide("op6")
        decide("op9")
      }

      (1 to 20).foreach { _ =>
        decide("op4")
      }
    }

    // Operations are getting 300, 600 and 1200 traces per minute for a total of 2100 per minute, which is above
    // the global throughput goal of 600 traces per minute so each operation should be capped to roughly 200
    // traces per minute, except for op9 which is capped to 40 per minute.
    assertWithinTolerance(sampledTraces("op4"), 1860, 186)
    assertWithinTolerance(sampledTraces("op5"), 1860, 186)
    assertWithinTolerance(sampledTraces("op6"), 1860, 186)
    assertWithinTolerance(sampledTraces("op9"), 400, 100)
    assertWithinTolerance(totalSampledTraces(), 6000, 600)
  }

  test("adaptive sampling: spread unused throughput across operations and respect maximum/minimum throughput rules with uneven traffic") {
    implicit val sampler = adaptiveSampler()

    simulate(10.minutes) {
      decide("op4")

      (1 to 10).foreach { _ =>
        decide("op5")
        decide("op9")
      }

      (1 to 20).foreach { _ =>
        decide("op6")
      }
    }

    // Since op4 is getting just 60 requests per minute it leaves some available throughput that could be used by
    // other more active operations like op5 and op6, and the sampler ensure that it happens. It should be sampling
    // all traces for op4, roughly 20 traces for op9 because of its cap and split the remaining throughput between
    // op5 and op6.
    assertWithinTolerance(sampledTraces("op4"), 600, 60)
    assertWithinTolerance(sampledTraces("op5"), 2500, 250)
    assertWithinTolerance(sampledTraces("op6"), 2500, 250)
    assertWithinTolerance(sampledTraces("op9"), 400, 100)
    assertWithinTolerance(totalSampledTraces(), 6000, 600)
  }

  test("adaptive sampling: adapt to changes in behavior over time") {
    implicit val sampler = adaptiveSampler()

    simulate(10.minutes) {
      decide("op4")

      (1 to 10).foreach { _ =>
        decide("op5")
        decide("op9")
      }

      (1 to 20).foreach { _ =>
        decide("op6")
      }
    }

    // Since op4 is getting just 60 requests per minute it leaves some available throughput that could be used by
    // other more active operations like op5 and op6, and the sampler ensure that it happens. It should be sampling
    // all traces for op4, roughly 20 traces for op9 because of its cap and split the remaining throughput between
    // op5 and op6.
    assertWithinTolerance(sampledTraces("op4"), 600, 60)
    assertWithinTolerance(sampledTraces("op5"), 2500, 250)
    assertWithinTolerance(sampledTraces("op6"), 2500, 250)
    assertWithinTolerance(sampledTraces("op9"), 400, 100)
    assertWithinTolerance(totalSampledTraces(), 6000, 600)
    resetCounters()

    simulate(5.minutes) {
      decide("op4")

      (1 to 10).foreach { _ =>
        decide("op5")
        decide("op7")
        decide("op8")
        decide("op9")
      }

      (1 to 20).foreach { _ =>
        decide("op6")
      }
    }

    // During the last 5 minutes we introduced op7 and op8. Op8 has a minimum throughput rule of 100 so it will end
    // up taking a bigger chunk of the throughput and reducing the available throughput for the rest of the
    // operations
    assertWithinTolerance(sampledTraces("op4"), 300, 30)
    assertWithinTolerance(sampledTraces("op5"), 600, 100)
    assertWithinTolerance(sampledTraces("op6"), 600, 100)
    assertWithinTolerance(sampledTraces("op7"), 600, 100)
    assertWithinTolerance(sampledTraces("op8"), 550, 100)
    assertWithinTolerance(sampledTraces("op9"), 200, 50)
    assertWithinTolerance(totalSampledTraces(), 3000, 300)
    resetCounters()

    simulate(10.minutes) {
      decide("op4")
      decide("op5")
      decide("op6")

      (1 to 2).foreach { _ =>
        decide("op7")
        decide("op8")
        decide("op9")
      }
    }

    // During the last 5 minutes we reduced throughput from op4, op5 and op7 to 60 per minute and op6, op8 and op9
    // to 120 per minute, for a total of 540 requests per minute. At this point all traces should be captured since
    // there is enough room to get them all, except for op9 which has a cap.
    assertWithinTolerance(sampledTraces("op4"), 600, 100)
    assertWithinTolerance(sampledTraces("op5"), 600, 100)
    assertWithinTolerance(sampledTraces("op6"), 600, 100)
    assertWithinTolerance(sampledTraces("op7"), 1200, 120)
    assertWithinTolerance(sampledTraces("op8"), 1200, 120)
    assertWithinTolerance(sampledTraces("op9"), 400, 100)
    assertWithinTolerance(totalSampledTraces(), 4400, 440)
    resetCounters()
  }

  // Helper method to assert value is within tolerance
  private def assertWithinTolerance(actual: Int, expected: Int, tolerance: Int): Unit = {
    assert(
      actual >= expected - tolerance && actual <= expected + tolerance,
      s"Expected $expected +/- $tolerance but got $actual"
    )
  }

  // Gets a decision from an adaptive sampler for the provided operation name and stores the number of sampled
  // responses for it.
  def decide(operationName: String)(implicit sampler: AdaptiveSampler): Unit =
    if (sampler.decide(Kamon.spanBuilder(operationName)) == SamplingDecision.Sample) {
      val current = _decisions.get(operationName).getOrElse(0)
      _decisions.put(operationName, current + 1)
    }

  def sampledTraces(operationName: String): Int =
    _decisions.get(operationName).getOrElse(0)

  def totalSampledTraces(): Int =
    _decisions.values.sum

  def resetCounters(): Unit =
    _decisions.clear()

  def simulate(duration: Duration)(perSecond: => Unit)(implicit sampler: AdaptiveSampler): Unit = {
    (1 to duration.toSeconds.toInt).foreach { _ =>
      perSecond
      sampler.adapt()
    }
  }

  def adaptiveSampler(): AdaptiveSampler =
    new AdaptiveSampler()

}
