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

class GlobPathFilterSuite extends FunSuite {
  test("match a single expression") {
    val filter = Filter.Glob("/user/actor")

    assert(filter.accept("/user/actor"))
    assert(!filter.accept("/user/actor/something"))
    assert(!filter.accept("/user/actor/somethingElse"))
  }

  test("match all expressions in the same level") {
    val filter = Filter.Glob("/user/*")

    assert(filter.accept("/user/actor"))
    assert(filter.accept("/user/otherActor"))
    assert(!filter.accept("/user/something/actor"))
    assert(!filter.accept("/user/something/otherActor"))
  }

  test("match any expressions when using double star alone (**) ") {
    val filter = Filter.Glob("**")

    assert(filter.accept("GET: /ping"))
    assert(filter.accept("GET: /ping/pong"))
    assert(filter.accept("this-doesn't_look good but-passes"))
  }

  test("match all expressions and cross the path boundaries when using double star suffix (**)") {
    val filter = Filter.Glob("/user/actor-**")

    assert(filter.accept("/user/actor-"))
    assert(filter.accept("/user/actor-one"))
    assert(filter.accept("/user/actor-one/other"))
    assert(!filter.accept("/user/something/actor"))
    assert(!filter.accept("/user/something/otherActor"))
  }

  test("match exactly one character when using question mark (?)") {
    val filter = Filter.Glob("/user/actor-?")

    assert(filter.accept("/user/actor-1"))
    assert(filter.accept("/user/actor-2"))
    assert(filter.accept("/user/actor-3"))
    assert(!filter.accept("/user/actor-one"))
    assert(!filter.accept("/user/actor-two"))
    assert(!filter.accept("/user/actor-tree"))
  }
}
