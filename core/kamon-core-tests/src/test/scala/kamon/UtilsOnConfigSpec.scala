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

import com.typesafe.config.ConfigFactory
import munit.FunSuite

class UtilsOnConfigSuite extends FunSuite {
  val config = ConfigFactory.parseString(
    """
      | kamon.test {
      |   configuration-one {
      |     setting = value
      |     other-setting = other-value
      |   }
      |
      |   "config.two" {
      |     setting = value
      |   }
      | }
    """.stripMargin
  )

  test("list all top level keys with a configuration") {
    val keys = config.getConfig("kamon.test").topLevelKeys
    assertEquals(keys, Set("configuration-one", "config.two"))
  }

  test("create a map from top level keys to the inner configuration objects") {
    val extractedConfigurations = config.getConfig("kamon.test").configurations

    assertEquals(extractedConfigurations.keySet, Set("configuration-one", "config.two"))
    assertEquals(extractedConfigurations("configuration-one").topLevelKeys, Set("setting", "other-setting"))
    assertEquals(extractedConfigurations("config.two").topLevelKeys, Set("setting"))
  }
}
