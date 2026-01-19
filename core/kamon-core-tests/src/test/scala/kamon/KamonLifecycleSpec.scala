package kamon

import java.io.File
import java.util.concurrent.TimeUnit
import com.typesafe.config.{Config, ConfigFactory}
import kamon.metric.PeriodSnapshot
import kamon.testkit.munit.Eventually
import kamon.trace.Span
import munit.FunSuite

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

class KamonLifecycleSuite extends FunSuite with Eventually {

  test("keep the JVM running if modules are running") {
    val process = Runtime.getRuntime.exec(createProcessCommand("kamon.KamonWithRunningReporter"))
    try {
      Thread.sleep(5000)
      assert(process.isAlive)
    } finally {
      process.destroyForcibly().waitFor(5, TimeUnit.SECONDS)
    }
  }

  test("let the JVM stop after all modules are stopped") {
    val process = Runtime.getRuntime.exec(createProcessCommand("kamon.KamonWithTemporaryReporter"))
    Thread.sleep(2000)
    assert(process.isAlive)

    eventually(timeout = 7.seconds) {
      assert(!process.isAlive)
      assertEquals(process.exitValue(), 0)
    }
  }

  test("not create any threads if Kamon.init was not called") {
    val process = Runtime.getRuntime.exec(createProcessCommand("kamon.UsingKamonApisWithoutInit"))

    eventually(timeout = 7.seconds) {
      assert(!process.isAlive)
      assertEquals(process.exitValue(), 0)
    }
  }

  test("process calls to reconfigure before and after being operational") {
    val process = Runtime.getRuntime.exec(createProcessCommand("kamon.ReconfiguringBeforeInit"))

    eventually(timeout = 7.seconds) {
      assert(!process.isAlive)
      assertEquals(process.exitValue(), 0)
    }
  }

  def createProcessCommand(mainClass: String): String = {
    System.getProperty("java.home") + File.separator + "bin" + File.separator + "java" +
    " -cp " + System.getProperty("java.class.path") + " " + mainClass
  }
}

class DummyMetricReporter extends kamon.module.MetricReporter {
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}
  override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {}
}

class DummySpanReporter extends kamon.module.SpanReporter {
  override def stop(): Unit = {}
  override def reconfigure(config: Config): Unit = {}
  override def reportSpans(spans: Seq[Span.Finished]): Unit = {}
}

object KamonWithRunningReporter extends App {
  Kamon.addReporter("dummy metric reporter", new DummyMetricReporter())
  Kamon.addReporter("dummy span reporter", new DummySpanReporter())
}

object KamonWithTemporaryReporter extends App {
  Kamon.addReporter("dummy metric reporter", new DummyMetricReporter())
  Kamon.addReporter("dummy span reporter", new DummySpanReporter())

  Thread.sleep(5000)
  Kamon.stop()
}

object UsingKamonApisWithoutInit extends App {
  Kamon.counter("my-counter")
  Kamon.runWithContextTag("hello", "kamon") {
    Kamon.currentSpan().takeSamplingDecision()
  }

  val allKamonThreadNames = Thread.getAllStackTraces
    .keySet()
    .asScala
    .filter(_.getName.startsWith("kamon"))

  if (allKamonThreadNames.nonEmpty)
    sys.error("Kamon shouldn't start or create threads until init is called")
}

object ReconfiguringBeforeInit extends App {
  Kamon.reconfigure(ConfigFactory.load())
  Kamon.init()
  Kamon.stop()
  Kamon.reconfigure(ConfigFactory.load())
}
