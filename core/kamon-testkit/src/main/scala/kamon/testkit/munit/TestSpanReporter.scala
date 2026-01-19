/* =========================================================================================
 * Copyright © 2013-2026 the kamon project <http://kamon.io/>
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

package kamon.testkit.munit

import kamon.Kamon
import kamon.module.Module.Registration
import kamon.testkit.Reconfigure
import kamon.testkit.TestSpanReporter.BufferingSpanReporter
import munit.Suite

trait TestSpanReporter extends Suite with Reconfigure {
  private val reporter = new BufferingSpanReporter()
  private var registration: Option[Registration] = None

  abstract override def beforeAll(): Unit = {
    super.beforeAll()

    sampleAlways()
    enableFastSpanFlushing()
    registration = Some(Kamon.addReporter(s"test-span-reporter-${getClass.getSimpleName}", reporter))
  }

  abstract override def afterAll(): Unit = {
    registration.foreach(_.cancel())
    registration = None
    super.afterAll()
  }

  def testSpanReporter(): BufferingSpanReporter = reporter

  def shutdownTestSpanReporter(): Unit = registration.foreach(_.cancel())
}
