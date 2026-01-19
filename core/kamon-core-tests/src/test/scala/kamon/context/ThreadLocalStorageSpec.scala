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

package kamon.context

import kamon.context.Storage.Scope
import munit.FunSuite

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

class ThreadLocalStorageSpec extends FunSuite {

  private val executor = Executors.newFixedThreadPool(2)
  private implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(executor)

  test("ThreadLocal returns an empty context when nothing was set") {
    assertEquals(TLS.current(), Context.Empty)
  }

  test("ThreadLocal returns default values for missing keys") {
    assertEquals(TLS.current().get(TestKey), 42)
    assertEquals(TLS.current().get(AnotherKey), 99)
    assertEquals(TLS.current().get(BroadcastKey), "i travel around")

    assertEquals(ScopeWithKey.get(TestKey), 43)
    assertEquals(ScopeWithKey.get(AnotherKey), 99)
    assertEquals(ScopeWithKey.get(BroadcastKey), "i travel around")
  }

  test("ThreadLocal stores and clears the current context") {
    assertEquals(TLS.current(), Context.Empty)

    val scope = TLS.store(ScopeWithKey)
    assert(TLS.current() eq ScopeWithKey)
    scope.close()

    assertEquals(TLS.current(), Context.Empty)
  }

  test("CrossThreadLocal returns an empty context when nothing was set") {
    assertEquals(CrossTLS.current(), Context.Empty)
  }

  test("CrossThreadLocal returns default values for missing keys") {
    assertEquals(CrossTLS.current().get(TestKey), 42)
    assertEquals(CrossTLS.current().get(AnotherKey), 99)
    assertEquals(CrossTLS.current().get(BroadcastKey), "i travel around")

    assertEquals(ScopeWithKey.get(TestKey), 43)
    assertEquals(ScopeWithKey.get(AnotherKey), 99)
    assertEquals(ScopeWithKey.get(BroadcastKey), "i travel around")
  }

  test("CrossThreadLocal stores and clears the current context") {
    assertEquals(CrossTLS.current(), Context.Empty)

    val scope = CrossTLS.store(ScopeWithKey)
    assert(CrossTLS.current() eq ScopeWithKey)
    scope.close()

    assertEquals(CrossTLS.current(), Context.Empty)
  }

  test("CrossThreadLocal scope can be closed on a different thread") {
    @volatile var scope: Scope = null

    val f1 = Future {
      CrossTLS.store(ContextWithAnotherKey)
      scope = CrossTLS.store(ScopeWithKey)
      Thread.sleep(10)
      assert(CrossTLS.current() eq ScopeWithKey)
    }

    val f2 = Future {
      while (scope eq null) {}
      assertEquals(CrossTLS.current(), Context.Empty)
      scope.close()
      assert(CrossTLS.current() eq ContextWithAnotherKey)
    }

    f1.flatMap(_ => f2)
  }

  override def afterAll(): Unit = {
    executor.shutdown()
    super.afterAll()
  }

  private val TLS: Storage = Storage.ThreadLocal()
  private val CrossTLS: Storage = Storage.CrossThreadLocal()
  private val TestKey = Context.key("test-key", 42)
  private val AnotherKey = Context.key("another-key", 99)
  private val BroadcastKey = Context.key("broadcast", "i travel around")
  private val ScopeWithKey = Context.of(TestKey, 43)
  private val ContextWithAnotherKey = Context.of(AnotherKey, 98)
}
