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



trait Continuation {
  def activate(): ActiveSpan
}

object Continuation {

  /**
    *
    * @param span
    * @param tracer
    */
  final class Default(span: Span, tracer: Tracer) extends Continuation {
    override def activate(): ActiveSpan =
      tracer.makeActive(span)
  }

  object Default {
    def apply(span: Span, tracer: Tracer): Default = new Default(span, tracer)
  }
}