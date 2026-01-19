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

class SingleLengthIdentifierSchemeSpec extends FunSuite {

  List(
    ("trace identifier factory", Identifier.Scheme.Single.traceIdFactory),
    ("span identifier factory", Identifier.Scheme.Single.spanIdFactory)
  ).foreach { case (factoryName, factory) =>

    test(s"$factoryName: generate random longs (8 byte) identifiers") {
      (1 to 100).foreach { _ =>
        val Identifier(string, bytes) = factory.generate()

        assertEquals(string.length, 16)
        assertEquals(bytes.length, 8)
      }
    }

    test(s"$factoryName: decode the string representation back into a identifier") {
      (1 to 100).foreach { _ =>
        val identifier = factory.generate()
        val decodedIdentifier = factory.from(identifier.string)

        assertEquals(identifier.string, decodedIdentifier.string)
        assertEquals(identifier.bytes.toSeq, decodedIdentifier.bytes.toSeq)
      }
    }

    test(s"$factoryName: decode the bytes representation back into a identifier") {
      (1 to 100).foreach { _ =>
        val identifier = factory.generate()
        val decodedIdentifier = factory.from(identifier.bytes)

        assertEquals(identifier.string, decodedIdentifier.string)
        assertEquals(identifier.bytes.toSeq, decodedIdentifier.bytes.toSeq)
      }
    }

    test(s"$factoryName: return IdentityProvider.NoIdentifier if the provided input cannot be decoded into a Identifier") {
      assertEquals(factory.from("zzzz"), Identifier.Empty)
      assertEquals(factory.from(Array[Byte](1)), Identifier.Empty)
    }
  }
}
