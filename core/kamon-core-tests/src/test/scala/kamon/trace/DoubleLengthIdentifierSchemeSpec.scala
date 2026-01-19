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

package kamon.trace

import munit.FunSuite

class DoubleLengthIdentifierSchemeSpec extends FunSuite {
  import Identifier.Scheme.Double.{spanIdFactory, traceIdFactory}

  test("trace identifiers: generate random longs (16 byte) identifiers") {
    (1 to 100).foreach { _ =>
      val Identifier(string, bytes) = traceIdFactory.generate()

      assertEquals(string.length, 32)
      assertEquals(bytes.length, 16)
    }
  }

  test("trace identifiers: decode the string representation back into a identifier") {
    (1 to 100).foreach { _ =>
      val identifier = traceIdFactory.generate()
      val decodedIdentifier = traceIdFactory.from(identifier.string)

      assertEquals(identifier.string, decodedIdentifier.string)
      assertEquals(identifier.bytes.toSeq, decodedIdentifier.bytes.toSeq)
    }
  }

  test("trace identifiers: decode the bytes representation back into a identifier") {
    (1 to 100).foreach { _ =>
      val identifier = traceIdFactory.generate()
      val decodedIdentifier = traceIdFactory.from(identifier.bytes)

      assertEquals(identifier.string, decodedIdentifier.string)
      assertEquals(identifier.bytes.toSeq, decodedIdentifier.bytes.toSeq)
    }
  }

  test("trace identifiers: return IdentityProvider.NoIdentifier if the provided input cannot be decoded into a Identifier") {
    assertEquals(traceIdFactory.from("zzzz"), Identifier.Empty)
    assertEquals(traceIdFactory.from(Array[Byte](1)), Identifier.Empty)
  }

  test("span identifiers: generate random longs (8 byte) identifiers") {
    (1 to 100).foreach { _ =>
      val Identifier(string, bytes) = spanIdFactory.generate()

      assertEquals(string.length, 16)
      assertEquals(bytes.length, 8)
    }
  }

  test("span identifiers: decode the string representation back into a identifier") {
    (1 to 100).foreach { _ =>
      val identifier = spanIdFactory.generate()
      val decodedIdentifier = spanIdFactory.from(identifier.string)

      assertEquals(identifier.string, decodedIdentifier.string)
      assertEquals(identifier.bytes.toSeq, decodedIdentifier.bytes.toSeq)
    }
  }

  test("span identifiers: decode the bytes representation back into a identifier") {
    (1 to 100).foreach { _ =>
      val identifier = spanIdFactory.generate()
      val decodedIdentifier = spanIdFactory.from(identifier.bytes)

      assertEquals(identifier.string, decodedIdentifier.string)
      assertEquals(identifier.bytes.toSeq, decodedIdentifier.bytes.toSeq)
    }
  }

  test("span identifiers: return IdentityProvider.NoIdentifier if the provided input cannot be decoded into a Identifier") {
    assertEquals(spanIdFactory.from("zzzz"), Identifier.Empty)
    assertEquals(spanIdFactory.from(Array[Byte](1)), Identifier.Empty)
  }
}
