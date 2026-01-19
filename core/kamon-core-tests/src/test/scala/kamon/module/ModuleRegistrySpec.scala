/* =========================================================================================
 * Copyright © 2013-2017 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions
 * and limitations under the License.
 * =========================================================================================
 */

package kamon
package module

import java.util.concurrent.atomic.AtomicLong
import com.typesafe.config.Config
import kamon.metric.PeriodSnapshot
import kamon.testkit.Reconfigure
import kamon.testkit.munit.{Eventually, InitAndStopKamonAfterAll}
import munit.FunSuite

import scala.concurrent.Await
import scala.concurrent.duration._

class ModuleRegistrySpec
    extends FunSuite
    with Reconfigure
    with InitAndStopKamonAfterAll
    with Eventually {
  override def beforeAll(): Unit = {
    super.beforeAll()
    applyConfig(
      """
        |kamon.metric.tick-interval = 10 millis
        |test-metric-filter {
        |  includes = [ "filtered**" ]
        |}
        |
        |""".stripMargin
    )
  }

  override def afterAll(): Unit = {
    reset()
    super.afterAll()
  }

  test("report all metrics if no filters are applied") {
    Kamon.counter("test.hello").withoutTags().increment()
    Kamon.counter("test.world").withoutTags().increment()
    Kamon.counter("other.hello").withoutTags().increment()

    val reporter = new SeenMetricsReporter()
    val subscription = Kamon.addReporter("reporter-registry-spec", reporter)

    try {
      eventually() {
        val metrics = reporter.metrics()
        assert(reporter.snapshotCount() >= 1)
        assert(metrics.contains("test.hello"))
        assert(metrics.contains("test.world"))
        assert(metrics.contains("other.hello"))
      }
    } finally {
      subscription.cancel()
    }
  }

  test("default to deny all metrics if a provided filter name doesn't exist") {
    Kamon.counter("test.hello").withoutTags().increment()
    Kamon.counter("test.world").withoutTags().increment()
    Kamon.counter("other.hello").withoutTags().increment()

    val originalReporter = new SeenMetricsReporter()
    val reporter =
      MetricReporter.withTransformations(originalReporter, MetricReporter.filterMetrics("does-not-exist"))
    val subscription = Kamon.addReporter("reporter-registry-spec", reporter)

    try {
      eventually() {
        assert(originalReporter.snapshotCount() >= 1)
        assertEquals(originalReporter.metrics(), Seq.empty)
      }
    } finally {
      subscription.cancel()
    }
  }

  test("apply existent filters") {
    Kamon.counter("filtered.hello").withoutTags().increment()
    Kamon.counter("filtered.world").withoutTags().increment()
    Kamon.counter("other.hello").withoutTags().increment()

    val originalReporter = new SeenMetricsReporter()
    val reporter =
      MetricReporter.withTransformations(originalReporter, MetricReporter.filterMetrics("test-metric-filter"))
    val subscription = Kamon.addReporter("reporter-registry-spec", reporter)

    try {
      eventually() {
        val metrics = originalReporter.metrics().toSet
        assert(originalReporter.snapshotCount() >= 1)
        assertEquals(metrics, Set("filtered.hello", "filtered.world"))
      }
    } finally {
      subscription.cancel()
    }
  }

  test("allow modules to stop and start repeatedly") {
    (1 to 10).foreach { _ =>
      val module = new DummyModule()
      Kamon.registerModule("dummy", module)
      Await.ready(Kamon.stopModules(), 5.seconds)
    }
  }

  private class SeenMetricsReporter extends MetricReporter {
    @volatile private var count = 0
    @volatile private var seenMetrics = Seq.empty[String]

    override def reportPeriodSnapshot(snapshot: PeriodSnapshot): Unit = {
      count += 1
      seenMetrics =
        snapshot.counters.map(_.name) ++
        snapshot.histograms.map(_.name) ++
        snapshot.gauges.map(_.name) ++
        snapshot.rangeSamplers.map(_.name) ++
        snapshot.timers.map(_.name)
    }

    def metrics(): Seq[String] =
      seenMetrics

    def snapshotCount(): Int =
      count

    override def stop(): Unit = {}
    override def reconfigure(config: Config): Unit = {}
  }

  private val dummyModuleCount = new AtomicLong(0L)

  private class DummyModule extends Module {
    dummyModuleCount.incrementAndGet()

    override def reconfigure(newConfig: Config): Unit = {}
    override def stop(): Unit = dummyModuleCount.decrementAndGet()
  }
}
