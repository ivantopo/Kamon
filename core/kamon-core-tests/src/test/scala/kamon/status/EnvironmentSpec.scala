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

package kamon.status

import com.typesafe.config.ConfigFactory
import kamon.tag.TagSet
import munit.FunSuite

class EnvironmentSpec extends FunSuite {
  private val baseConfig = ConfigFactory.parseString(
    """
      |kamon.environment {
      |  service = environment-spec
      |  host = auto
      |  instance = auto
      |}
      |""".stripMargin
  ).withFallback(ConfigFactory.defaultReference())

  test("assign a host and instance name when they are set to 'auto'") {
    val env = Environment.from(baseConfig)

    assertNotEquals(env.host, "auto")
    assertNotEquals(env.instance, "auto")
    assertEquals(env.instance, s"environment-spec@${env.host}")
    assert(env.tags.isEmpty())
  }

  test("use the configured host and instance, if provided") {
    val customConfig = ConfigFactory.parseString(
      """
        |kamon.environment {
        |  host = spec-host
        |  instance = spec-instance
        |}
        |""".stripMargin
    )

    val env = Environment.from(customConfig.withFallback(baseConfig))

    assertEquals(env.host, "spec-host")
    assertEquals(env.instance, "spec-instance")
    assert(env.tags.isEmpty())
  }

  test("read all environment tags, if provided") {
    val customConfig = ConfigFactory.parseString(
      """
        |kamon.environment.tags {
        |  custom1 = "test1"
        |  env = staging
        |}
        |""".stripMargin
    )

    val env = Environment.from(customConfig.withFallback(baseConfig))
    val tags = tagsToMap(env.tags)

    assertEquals(tags.get("custom1"), Some("test1"))
    assertEquals(tags.get("env"), Some("staging"))
  }

  test("always return the same incarnation name") {
    val envOne = Environment.from(baseConfig)
    val envTwo = Environment.from(baseConfig)

    assertEquals(envOne.incarnation, envTwo.incarnation)
  }

  private def tagsToMap(tags: TagSet): Map[String, String] = {
    val map = Map.newBuilder[String, String]
    tags.iterator(_.toString).foreach(pair => map += pair.key -> pair.value)
    map.result()
  }
}
