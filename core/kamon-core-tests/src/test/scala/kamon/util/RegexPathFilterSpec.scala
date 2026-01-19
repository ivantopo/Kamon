/*
 * =========================================================================================
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

import munit.FunSuite

class RegexPathFilterSuite extends FunSuite {
  test("match a single expression") {
    val filter = Filter.Regex("/user/actor")

    assert(filter.accept("/user/actor"))
    assert(!filter.accept("/user/actor/something"))
    assert(!filter.accept("/user/actor/somethingElse"))
  }

  test("match arbitrary expressions ending with wildcard") {
    val filter = Filter.Regex("/user/.*")

    assert(filter.accept("/user/actor"))
    assert(filter.accept("/user/otherActor"))
    assert(filter.accept("/user/something/actor"))
    assert(filter.accept("/user/something/otherActor"))

    assert(!filter.accept("/otheruser/actor"))
    assert(!filter.accept("/otheruser/otherActor"))
    assert(!filter.accept("/otheruser/something/actor"))
    assert(!filter.accept("/otheruser/something/otherActor"))
  }

  test("match numbers") {
    val filter = Filter.Regex("/user/actor-\\d")

    assert(filter.accept("/user/actor-1"))
    assert(filter.accept("/user/actor-2"))
    assert(filter.accept("/user/actor-3"))

    assert(!filter.accept("/user/actor-one"))
    assert(!filter.accept("/user/actor-two"))
    assert(!filter.accept("/user/actor-tree"))
  }
}
