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

package kamon.util

import com.typesafe.config.ConfigFactory
import kamon.Kamon
import munit.FunSuite

class FilterSuite extends FunSuite {
  private val testConfig = ConfigFactory.parseString(
    """
      |kamon.util.filters {
      |
      |  some-filter {
      |    includes = ["**"]
      |    excludes = ["not-me"]
      |  }
      |
      |  only-includes {
      |    includes = ["only-me"]
      |  }
      |
      |  only-excludes {
      |    excludes = ["not-me"]
      |  }
      |
      |  specific-rules {
      |    includes = ["glob:/user/**", "regex:test-[0-5]"]
      |  }
      |
      |  "filter.with.quotes" {
      |    includes = ["**"]
      |    excludes = ["not-me"]
      |  }
      |}
    """.stripMargin
  )

  private val configured = Kamon.reconfigure(testConfig.withFallback(Kamon.config()))

  test("reject anything that doesn't match any configured filter") {
    assert(!Kamon.filter("kamon.util.filters.not-a-filter").accept("hello"))
  }

  test("evaluate patterns for filters with includes and excludes") {
    val filter = Kamon.filter("kamon.util.filters.some-filter")
    assert(filter.accept("anything"))
    assert(filter.accept("some-other"))
    assert(!filter.accept("not-me"))
  }

  test("allow configuring includes only or excludes only for any filter") {
    val filter = Kamon.filter("kamon.util.filters.only-includes")
    assert(filter.accept("only-me"))
    assert(!filter.accept("anything"))
    assert(!filter.accept("any-other"))
    assert(!filter.accept("not-me"))
  }

  test("allow to explicitly decide whether patterns are treated as Glob or Regex") {
    val filter = Kamon.filter("kamon.util.filters.specific-rules")
    assert(filter.accept("/user/accepted"))
    assert(!filter.accept("/other/rejected/"))
    assert(filter.accept("test-5"))
    assert(!filter.accept("test-6"))
  }

  test("allow filters with quoted names") {
    val filter = Kamon.filter("kamon.util.filters.\"filter.with.quotes\"")
    assert(filter.accept("anything"))
    assert(filter.accept("some-other"))
    assert(!filter.accept("not-me"))
  }
}
