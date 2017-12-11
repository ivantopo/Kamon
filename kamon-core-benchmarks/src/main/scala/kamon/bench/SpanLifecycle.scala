package kamon.bench

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import kamon.trace.Span
import kamon.trace.SpanContext.SamplingDecision
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@Fork(1)
@Threads(1)
@Warmup(iterations = 20)
@BenchmarkMode(Array(Mode.AverageTime))
class SpanLifecycle {

  private val sampledSpan = createSpan(SamplingDecision.Sample)
  private val nonSampledSpan = createSpan(SamplingDecision.DoNotSample)

  private def createSpan(samplingDecision: SamplingDecision): Span = {
    val localSpan = Kamon.buildSpan("local-span").start().asInstanceOf[Span.Local]
    val nonSampledContext = localSpan.context().copy(samplingDecision = samplingDecision)
    Span.Local(nonSampledContext, None, "test", Map.empty, Map.empty, 0L, null, true) // null SpanSink shouldn't fail if the Span is never finished
  }

  @Setup
  def setup(): Unit = {
    Kamon.reconfigure(ConfigFactory.parseString(
      """
        |kamon.trace.sampler = always
      """.stripMargin
    ).withFallback(ConfigFactory.defaultReference()))
  }

  @Benchmark
  @OutputTimeUnit(TimeUnit.NANOSECONDS)
  def createSampledSpan(): Unit = {
    Kamon.buildSpan("test-sampled-operation")
      .asChildOf(sampledSpan)
      .start()
      .finish()
  }

//  @Benchmark
//  @OutputTimeUnit(TimeUnit.NANOSECONDS)
//  def createNotSampledSpan(): Unit = {
//    Kamon.buildSpan("test-non-sampled-operation")
//      .asChildOf(nonSampledSpan)
//      .start()
//      .finish()
//  }
//
//  @Benchmark
//  @OutputTimeUnit(TimeUnit.NANOSECONDS)
//  def createSampledSpanWithoutMetrics(): Unit = {
//    Kamon.buildSpan("test-sampled-operation")
//      .asChildOf(sampledSpan)
//      .start()
//      .disableMetrics()
//      .finish()
//  }
//
//  @Benchmark
//  @OutputTimeUnit(TimeUnit.NANOSECONDS)
//  def createNotSampledSpanWithoutMetrics(): Unit = {
//    Kamon.buildSpan("test-non-sampled-operation")
//      .asChildOf(nonSampledSpan)
//      .start()
//      .disableMetrics()
//      .finish()
//  }

//  @Benchmark
//  @OutputTimeUnit(TimeUnit.NANOSECONDS)
//  def createNotSampleSpanWithoutMetrics(): Unit = {
//    Kamon.buildSpan("test-operation")
//      .start()
//      .disableMetrics()
//      .finish()
//  }
//
//  @Benchmark
//  @OutputTimeUnit(TimeUnit.NANOSECONDS)
//  def createSpanWithMetrics(): Unit = {
//    Kamon.buildSpan("test-operation").start().finish()
//  }
}
