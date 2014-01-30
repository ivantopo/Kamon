/* ===================================================
 * Copyright © 2013 the kamon project <http://kamon.io/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ========================================================== */
package kamon.trace

import kamon.Kamon
import akka.actor._
import scala.Some
import kamon.trace.TraceOld.Register
import scala.concurrent.duration._
import java.util.concurrent.atomic.AtomicLong
import scala.util.Try
import java.net.InetAddress

object TraceOld extends ExtensionId[TraceExtension] with ExtensionIdProvider {
  def lookup(): ExtensionId[_ <: Extension] = TraceOld
  def createExtension(system: ExtendedActorSystem): TraceExtension = new TraceExtension(system)

  /*** Protocol */
  case object Register

  /** User API */
  //private[trace] val traceContext = new DynamicVariable[Option[TraceContext]](None)
  private[trace] val traceContext = new ThreadLocal[Option[TraceContextOld]] {
    override def initialValue(): Option[TraceContextOld] = None
  }
  private[trace] val tranid = new AtomicLong()

  def context() = traceContext.get
  private def set(ctx: Option[TraceContextOld]) = traceContext.set(ctx)

  def clear: Unit = traceContext.remove()
  def start(name: String, token: Option[String])(implicit system: ActorSystem): TraceContextOld = {
    val ctx = newTraceContext(name, token.getOrElse(TraceToken.generate()))
    ctx.start(name)
    set(Some(ctx))

    ctx
  }

  def withContext[T](ctx: Option[TraceContextOld])(thunk: ⇒ T): T = {
    val oldval = context
    set(ctx)

    try thunk
    finally set(oldval)
  }

  def transformContext(f: TraceContextOld ⇒ TraceContextOld): Unit = {
    context.map(f).foreach(ctx ⇒ set(Some(ctx)))
  }

  def finish(): Option[TraceContextOld] = {
    val ctx = context()
    ctx.map(_.finish)
    clear
    ctx
  }

  // TODO: FIX
  def newTraceContext(name: String, token: String)(implicit system: ActorSystem): TraceContextOld = TraceContextOld(Kamon(TraceOld).api, tranid.getAndIncrement, name, token)

  def startSegment(category: Segments.Category, description: String = "", attributes: Map[String, String] = Map()): SegmentCompletionHandle = {
    val start = Segments.Start(category, description, attributes)
    SegmentCompletionHandle(start)
  }

  def startSegment(start: Segments.Start): SegmentCompletionHandle = SegmentCompletionHandle(start)

  case class SegmentCompletionHandle(start: Segments.Start) {
    def complete(): Unit = {
      val end = Segments.End()
      //println(s"Completing the Segment: $start - $end")
    }
    def complete(end: Segments.End): Unit = {
      //println(s"Completing the Segment: $start - $end")
    }
  }
}

object TraceToken {
  val tokenCounter = new AtomicLong
  val hostnamePrefix = Try(InetAddress.getLocalHost.getHostName).getOrElse("unknown-localhost")

  def generate(): String = "%s-%s".format(hostnamePrefix, tokenCounter.incrementAndGet())
}

class TraceExtension(system: ExtendedActorSystem) extends Kamon.Extension {
  val api: ActorRef = system.actorOf(Props[TraceManager], "kamon-trace")
}

class TraceManager extends Actor with ActorLogging {
  var listeners: Seq[ActorRef] = Seq.empty

  def receive = {
    case Register ⇒
      listeners = sender +: listeners
      log.info("Registered [{}] as listener for Kamon traces", sender)

    case segment: UowSegment ⇒
      val tracerName = segment.id.toString
      context.child(tracerName).getOrElse(newTracer(tracerName)) ! segment

    case trace: UowTrace ⇒
      listeners foreach (_ ! trace)
  }

  def newTracer(name: String): ActorRef = {
    context.actorOf(UowTraceAggregator.props(self, 30 seconds), name)
  }
}