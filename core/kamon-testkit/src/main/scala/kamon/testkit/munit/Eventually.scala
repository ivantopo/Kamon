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

import scala.concurrent.duration._
import scala.util.control.NonFatal

trait Eventually {
  def eventually[T](timeout: FiniteDuration = 5.seconds, interval: FiniteDuration = 100.millis)(
      body: => T
  ): T = {
    val sleepMillis = math.max(interval.toMillis, 1L)
    val deadline = timeout.fromNow

    while (deadline.hasTimeLeft()) {
      try return body
      catch {
        case NonFatal(_) => Thread.sleep(sleepMillis)
      }
    }

    body
  }
}
