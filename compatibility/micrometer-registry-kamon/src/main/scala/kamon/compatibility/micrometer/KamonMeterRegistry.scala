package kamon.compatibility.micrometer

import com.typesafe.config.ConfigFactory
import io.micrometer.core.instrument.cumulative.{CumulativeCounter, CumulativeDistributionSummary, CumulativeFunctionCounter, CumulativeFunctionTimer, CumulativeTimer}
import io.micrometer.core.instrument.distribution.{DistributionStatisticConfig, HistogramSnapshot}
import io.micrometer.core.instrument.distribution.pause.PauseDetector
import io.micrometer.core.instrument.internal.{DefaultGauge, DefaultMeter}
import io.micrometer.core.instrument.util.TimeUtils
import io.micrometer.core.instrument.{AbstractDistributionSummary, AbstractTimer, Clock, Counter, DistributionSummary, FunctionCounter, FunctionTimer, Gauge, Measurement, Meter, MeterRegistry, Tag, Timer}
import kamon.Kamon
import kamon.metric.Instrument.Snapshotting
import kamon.metric.{Distribution, Instrument, MeasurementUnit, Metric}
import kamon.tag.TagSet

import java.lang
import java.time.Duration
import java.util.concurrent.{Callable, TimeUnit}
import java.util.function.{Supplier, ToDoubleFunction, ToLongFunction}
import scala.collection.JavaConverters.iterableAsScalaIterableConverter

class KamonMeterRegistry(config: KamonConfig, clock: Clock) extends MeterRegistry(clock) {

  Kamon.initWithoutAttaching(ConfigFactory
    .parseString(
      s"""
        |kamon.environment.service = ${config.appName}
        |kamon.metric.tick-interval = ${config.step().getSeconds} seconds
        |kamon.apm.agent=micrometer
        |kamon.apm.api-key = ${config.apiKey()}
        |""".stripMargin)
    .withFallback(Kamon.config()))

  override def newGauge[T](id: Meter.Id, obj: T, valueFunction: ToDoubleFunction[T]): Gauge = {
    new Gauge with KamonInstrumentReference[kamon.metric.Gauge, Metric.Settings.ForValueInstrument] {
      val kamonInstrument = Kamon.gauge(id.getName, id.getDescription, unitTextToMeasurementUnit(id.getBaseUnit))
        .withTags(tagsToTagSet(id))
        .autoUpdate(g => g.update(valueFunction.applyAsDouble(obj)): Unit)

      override def value(): Double =
        valueFunction.applyAsDouble(obj)

      override def getId: Meter.Id =
        id

    }
  }

  override def newCounter(id: Meter.Id): Counter = {
    new Counter with KamonInstrumentReference[kamon.metric.Counter, Metric.Settings.ForValueInstrument] {
      val kamonInstrument = Kamon.counter(id.getName, id.getDescription, unitTextToMeasurementUnit(id.getBaseUnit))
        .withTags(tagsToTagSet(id))

      override def increment(amount: Double): Unit =
        kamonInstrument.increment(amount.toLong)

      override def count(): Double =
        kamonInstrument.asInstanceOf[Snapshotting[Long]].snapshot(resetState = false).toDouble

      override def getId: Meter.Id =
        id

    }
  }

  override def newTimer(id: Meter.Id, distributionStatisticConfig: DistributionStatisticConfig, pauseDetector: PauseDetector): Timer = {
    new AbstractTimer(id, clock, distributionStatisticConfig, pauseDetector, TimeUnit.NANOSECONDS, true)
        with KamonInstrumentReference[kamon.metric.Timer, Metric.Settings.ForDistributionInstrument] {

      println("Creating a new TIMER " + id.getName + tagsToTagSet(id))

      val kamonInstrument = Kamon.timer(id.getName, id.getDescription)
        .withTags(tagsToTagSet(id))

      override def recordNonNegative(amount: Long, unit: TimeUnit): Unit =
        kamonInstrument.record(amount, unit)

      override def count(): Long =
        snapshot().count

      override def totalTime(unit: TimeUnit): Double =
        TimeUtils.nanosToUnit(snapshot().sum.toDouble, unit)

      override def max(unit: TimeUnit): Double =
        TimeUtils.nanosToUnit(snapshot().max.toDouble, unit)

      private def snapshot(): Distribution =
        kamonInstrument.asInstanceOf[Snapshotting[Distribution]].snapshot(resetState = false)
    }
  }

  override def newDistributionSummary(id: Meter.Id, distributionStatisticConfig: DistributionStatisticConfig, scale: Double): DistributionSummary = {
    new AbstractDistributionSummary(id, clock, distributionStatisticConfig, scale, true)
       with KamonInstrumentReference[kamon.metric.Histogram, Metric.Settings.ForDistributionInstrument] {

      val kamonInstrument = Kamon.histogram(id.getName, id.getDescription)
        .withTags(tagsToTagSet(id))

      override def recordNonNegative(amount: Double): Unit =
        kamonInstrument.record(amount.toLong)

      override def count(): Long =
        snapshot().count

      override def totalAmount(): Double =
        snapshot().sum.toDouble

      override def max(): Double =
        snapshot().max.toDouble

      private def snapshot(): Distribution =
        kamonInstrument.asInstanceOf[Snapshotting[Distribution]].snapshot(resetState = false)
    }
  }

  override def newMeter(id: Meter.Id, `type`: Meter.Type, measurements: lang.Iterable[Measurement]): Meter = {
    // TODO: Figure out if there is any place where plain meters are used in real life.
    new DefaultMeter(id, `type`, measurements)
  }

  override def newFunctionTimer[T](id: Meter.Id, obj: T, countFunction: ToLongFunction[T], totalTimeFunction: ToDoubleFunction[T], totalTimeFunctionUnit: TimeUnit): FunctionTimer = {
    new CumulativeFunctionTimer[T](id, obj, countFunction, totalTimeFunction, totalTimeFunctionUnit, totalTimeFunctionUnit)
  }

  override def newFunctionCounter[T](id: Meter.Id, obj: T, countFunction: ToDoubleFunction[T]): FunctionCounter = {
    new CumulativeFunctionCounter[T](id, obj, countFunction)
  }

  override def remove(mappedId: Meter.Id): Meter = {
    super.remove(mappedId) match {
      case kit: KamonInstrumentReference[_, _] =>
        kit.kamonInstrument.remove()
        kit
      case other => other
    }
  }

  override def getBaseTimeUnit: TimeUnit =
    TimeUnit.NANOSECONDS

  override def defaultHistogramConfig(): DistributionStatisticConfig =
    DistributionStatisticConfig.builder()
      .percentilesHistogram(false)
      .percentiles()
      .build()
      .merge(DistributionStatisticConfig.DEFAULT)


  private def tagsToTagSet(id: Meter.Id): TagSet = {
    val tagsBuilder = TagSet.builder()
    id.getTagsAsIterable.asScala.foreach(t => tagsBuilder.add(t.getKey, t.getValue))
    tagsBuilder.build()
  }

  private def unitTextToMeasurementUnit(baseUnit: String): MeasurementUnit =
    baseUnit match {
      case "nanoseconds" => MeasurementUnit.time.nanoseconds
      case "microseconds" => MeasurementUnit.time.microseconds
      case "milliseconds" => MeasurementUnit.time.milliseconds
      case "seconds" => MeasurementUnit.time.seconds
      case "bytes" => MeasurementUnit.information.bytes
      case "kilobytes" => MeasurementUnit.information.kilobytes
      case "megabytes" => MeasurementUnit.information.megabytes
      case "gigabytes" => MeasurementUnit.information.gigabytes
      case "percent" => MeasurementUnit.percentage
      case _ => MeasurementUnit.none
    }

  private trait KamonInstrumentReference[Inst <: Instrument[Inst, Sett], Sett <: Metric.Settings] {
    def kamonInstrument: Inst
  }
}

