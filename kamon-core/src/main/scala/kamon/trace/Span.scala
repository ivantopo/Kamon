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
package trace

import java.time.Instant

import kamon.context.Context
import kamon.tag.TagSet
import kamon.trace.Span.Link
import kamon.util.Clock
import org.slf4j.LoggerFactory

import scala.compat.Platform.EOL

/**
  * A Span encapsulates information about an operation performed by the application and its relationship to other
  * operations. At the most basic level, all Spans have an operation name and they are bound to the time taken by the
  * application to perform that operation, meaning that they have start and end time time stamps.
  *
  * Spans connect to each other creating a hierarchical structure of operations, where an operation can have several
  * child operations but only one parent operation. Furthermore, all related Spans share the same Trace information.
  *
  * Spans can be enriched with two types of information: tags and marks. Tags are key/value pairs that give additional
  * information about the operation; for example, a Span that represents an HTTP request could have tags that indicate
  * the full request URL, the response status code and the size of the payload, information that might come handy when
  * analyzing and troubleshooting issues using the trace data. Marks represent events related to the operation and they
  * are bound to a specific instant in time; for example, a mark could be used to indicate the instant when a connection
  * was established, when the SSL handshake finished and when the payload was transferred for a single HTTP request
  * operation.
  *
  * Optionally, Spans can generate metrics about the processing time of the operations they represent. By default, all
  * Spans will generate metrics unless they are explicitly disabled. Since metrics are very susceptible to high
  * cardinality tags, the Spans handle two different sets of tags: the Span tags that can have any sort of information
  * (like URLs, user ids and so on) and the metric tags which should only include information with low cardinality (like
  * HTTP status codes or operation names).
  *
  * Once a Span is finished it will be flushed to the Span reporters and, if metrics stayed enabled, the processing time
  * of the Span will be recorded on the "span.processing-time" metric with all provided metric tags in addition to the
  * operationName and error tags which are always added.
  */
sealed abstract class Span {

  /**
    * Uniquely identifies this Span within the Trace.
    */
  def id: Identifier

  /**
    * Identifier for the parent of this this Span, if any. If a Span has no parent (e.g. it is the first Span in the
    * trace) then an empty identifier is returned.
    */
  def parentId: Identifier

  /**
    * Returns the kind of operation represented by this Span.
    */
  def kind: Span.Kind

  /**
    * Trace to which this Span belongs.
    */
  def trace: Trace

  /**
    * Returns true if this Span was initially created in another process and then transferred to this process.
    */
  def isRemote: Boolean

  /**
    * Returns true if this Span is a placeholder because no Span information is available.
    */
  def isEmpty: Boolean
  /**
    * Returns the position of this Span in the trace to which it belongs.
    */
  def position: Span.Position

  /**
    * Returns the current operation name for this Span.
    */
  def operationName(): String

  /**
    * Changes the operation name on this Span. Even though it is possible (and sometimes necessary) to change the
    * operation name in a Span, take into account that the operation name might be captured by child Spans when parent
    * operation scoping is enabled and any updates done after the child spans read the operation name will not be
    * reflected on the "parentOperation" tag.
    */
  def name(name: String): Span

  /**
    * Adds the provided key/value pair to the Span tags. If a tag with the provided key was already present then its
    * value will be overwritten.
    */
  def tag(key: String, value: String): Span

  /**
    * Adds the provided key/value pair to the Span tags. If a tag with the provided key was already present then its
    * value will be overwritten.
    */
  def tag(key: String, value: Long): Span

  /**
    * Adds the provided key/value pair to the Span tags. If a tag with the provided key was already present then its
    * value will be overwritten.
    */
  def tag(key: String, value: Boolean): Span

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetric(key: String, value: String): Span

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetric(key: String, value: Long): Span

  /**
    * Adds the provided key/value pair to the Span metric tags. If a tag with the provided key was already present then
    * its value will be overwritten.
    */
  def tagMetric(key: String, value: Boolean): Span

  /**
    * Adds a new mark with the provided key using the current instant from Kamon's clock.
    */
  def mark(key: String): Span

  /**
    * Adds a new mark with the provided key and instant.
    */
  def mark(at: Instant, key: String): Span

  /**
    * Creates a link between this Span and the provided one.
    */
  def link(span: Span, kind: Link.Kind): Span

  /**
    * Marks the operation represented by this Span as failed and adds the provided message as a Span tag using the
    * "error.message" key.
    */
  def fail(errorMessage: String): Span

  /**
    * Marks the operation represented by this Span as failed and optionally adds the "error.stacktrace" Span tag with
    * the stack trace from the provided throwable. See the "kamon.trace.include-error-stacktrace" setting for more
    * information.
    */
  def fail(cause: Throwable): Span

  /**
    * Marks the operation represented by this Span as failed and adds the provided message as a Span tag using the
    * "error.message" key and optionally adds the "error.stacktrace" Span tag with the stack trace from the provided
    * throwable. See the "kamon.trace.include-error-stacktrace" setting for more information.
    */
  def fail(errorMessage: String, cause: Throwable): Span

  /**
    * Enables tracking of the span.processing-time metric for this Span.
    */
  def trackProcessingTime(): Span

  /**
    * Disables tracking of the span.processing-time metric for this Span.
    */
  def doNotTrackProcessingTime(): Span

  /**
    * Finishes this Span. Even though it is possible to call any of the methods that modify/write information on the
    * Span, once it is finished no further changes are taken into account.
    */
  def finish(): Unit

  /**
    * Finishes this Span using the provided finish instant. Even though it is possible to call any of the methods that
    * modify/write information on the Span, once it is finished no further changes are taken into account.
    */
  def finish(at: Instant): Unit

}

object Span {

  /**
    * Key used to store and retrieve Span instances from the Context
    */
  val Key = Context.key[Span]("span", Span.Empty)


  /**
    * Describes the kind of operation being represented by a Span.
    */
  sealed abstract class Kind
  object Kind {

    /**
      * The Span represents an operation on the receiving side of a request/response interaction. For example,
      * instrumentation on a HTTP server will most likely create a Span with kind=server that represents the operations
      * it processes.
      */
    case object Server extends Kind

    /**
      * The Span represents an operation on the initiating side of a request/response interaction. For example,
      * instrumentation on a HTTP client library will most likely generate a Span with kind=client that represents the
      * outgoing HTTP requests that it sends to other parties.
      */
    case object Client extends Kind

    /**
      * The Span represents an operation the produces a message placed on a message broker.
      */
    case object Producer extends Kind

    /**
      * The Span represents an operation that consumes messages from a message broker.
      */
    case object Consumer extends Kind

    /**
      * The Span represents an internal operation that doesn't imply communication communication with any external
      * services. For example, if there is an interesting block of code that affects the processing of a bigger
      * operation, it might be useful to create an internal Span that represents it so that the time spent on it will
      * be shown on traces and related metrics.
      */
    case object Internal extends Kind

    /**
      * The Span represents an unknown operation kind.
      */
    case object Unknown extends Kind
  }


  /**
    * Describes a Span's position within the trace they belong to.
    */
  sealed abstract class Position
  object Position {

    /**
      * Root spans are the very first Span on each trace. They do not have a parent Span.
      */
    case object Root extends Position

    /**
      * A local root is the first Span within this process that is joining a trace started in another process
      */
    case object LocalRoot extends Position

    /**
      * A span whose location is not know or does not need to be specified
      */
    case object Unknown extends Position
  }

  /**
    * Represents an event that happens at a given instant and is related to a Span. The key is a unique identifier of
    * the type of event being associated.
    */
  case class Mark(instant: Instant, key: String)

  /**
    * Represents a connection between two different Spans that might belong to different traces.
    */
  case class Link(kind: Link.Kind, trace: Trace, spanId: Identifier)

  object Link {

    /**
      * Define type or relationship between linked Spans.
      */
    sealed abstract class Kind
    object Kind {

      /**
        * Indicates that the the current Span is a continuation of the work started by the linked Span. A use case for
        * this link kind is when having secondary or fire-and-forget operations spawned from a Trace but that do not
        * necessarily should be part of the original trace.
        */
      case object FollowsFrom extends Link.Kind
    }
  }

  /**
    * Represents a Span that has already been finished and should be exposed to the SpanReporters.
    */
  case class Finished (
    id: Identifier,
    parentId: Identifier,
    trace: Trace,
    operationName: String,
    kind: Kind,
    location: Position,
    from: Instant,
    to: Instant,
    tags: TagSet,
    metricTags: TagSet,
    marks: Seq[Mark],
    links: Seq[Link]
  )

  /**
    * A writable Span created on this process and implementing all the capabilities defined by the Span interface.
    */
  final class Local(val id: Identifier, val parentId: Identifier, val trace: Trace, val position: Position,
      val kind: Kind, localParent: Option[Span], initialOperationName: String, spanTags: TagSet.Builder, metricTags: TagSet.Builder,
      from: Instant, initialMarks: List[Mark], initialLinks: List[Link], trackMetrics: Boolean, tagWithParentOperation: Boolean,
      includeErrorStacktrace: Boolean, clock: Clock, preFinishHooks: Array[Tracer.PreFinishHook], onFinish: Span.Finished => Unit) extends Span {

    private val _isSampled: Boolean = trace.samplingDecision == Trace.SamplingDecision.Sample
    private val _metricTags = metricTags
    private val _spanTags = spanTags
    private var _trackMetrics: Boolean = trackMetrics
    private var _isOpen: Boolean = true
    private var _hasError: Boolean = false
    private var _operationName: String = initialOperationName
    private var _marks: List[Mark] = initialMarks
    private var _links: List[Link] = initialLinks

    override val isRemote: Boolean = false
    override val isEmpty: Boolean = false

    override def tag(key: String, value: String): Span = synchronized {
      if(_isSampled && _isOpen)
        _spanTags.add(key, value)
      this
    }

    override def tag(key: String, value: Long): Span = synchronized {
      if(_isSampled && _isOpen)
        _spanTags.add(key, value)
      this
    }

    override def tag(key: String, value: Boolean): Span = synchronized {
      if(_isSampled && _isOpen)
        _spanTags.add(key, value)
      this
    }

    override def tagMetric(key: String, value: String): Span = synchronized {
      if(_isOpen && _trackMetrics)
        _metricTags.add(key, value)
      this
    }

    override def tagMetric(key: String, value: Long): Span = synchronized {
      if(_isOpen && _trackMetrics)
        _metricTags.add(key, value)
      this
    }

    override def tagMetric(key: String, value: Boolean): Span = synchronized {
      if(_isOpen && _trackMetrics)
        _metricTags.add(key, value)
      this
    }

    override def mark(key: String): Span = {
      mark(clock.instant(), key)
    }

    override def mark(at: Instant, key: String): Span = synchronized {
      if(_isOpen)
        _marks = Mark(at, key) :: _marks
      this
    }

    override def link(span: Span, kind: Link.Kind): Span = synchronized {
      if(_isOpen)
        _links = Link(kind, span.trace, span.id) :: _links
      this
    }

    override def fail(message: String): Span = synchronized {
      if(_isOpen) {
        _hasError = true

        if(_isSampled)
          _spanTags.add(TagKeys.ErrorMessage, message)
      }
      this
    }

    override def fail(throwable: Throwable): Span = synchronized {
      if(_isOpen) {
        _hasError = true

        if(_isSampled && includeErrorStacktrace)
          _spanTags.add(TagKeys.ErrorStacktrace, toStackTraceString(throwable))
      }
      this
    }

    override def fail(message: String, throwable: Throwable): Span = synchronized {
      if(_isOpen) {
        _hasError = true

        if(_isSampled) {
          _spanTags.add(TagKeys.ErrorMessage, message)

          if(includeErrorStacktrace)
            _spanTags.add(TagKeys.ErrorStacktrace, toStackTraceString(throwable))
        }
      }
      this
    }

    override def trackProcessingTime(): Span = synchronized {
      _trackMetrics = true
      this
    }

    override def doNotTrackProcessingTime(): Span = synchronized {
      _trackMetrics = false
      this
    }

    override def operationName(): String = synchronized {
      _operationName
    }

    override def name(operationName: String): Span = synchronized {
      if(_isOpen)
        _operationName = operationName
      this
    }

    override def finish(): Unit =
      finish(clock.instant())

    override def finish(to: Instant): Unit = synchronized {
      import Span.Local._logger

      if (_isOpen) {

        if(preFinishHooks.nonEmpty) {
          preFinishHooks.foreach(pfh => {
            try {
              pfh.beforeFinish(this)
            } catch {
              case t: Throwable =>
                _logger.error("Failed to apply pre-finish hook", t)
            }
          })
        }

        _isOpen = false

        val finalMetricTags = createMetricTags()

        if(_trackMetrics)
          recordSpanMetrics(to, finalMetricTags)

        if(_isSampled)
          onFinish(toFinishedSpan(to, finalMetricTags))
      }
    }

    private def toStackTraceString(throwable: Throwable): String =
      throwable.getStackTrace().mkString("", EOL, EOL)

    private def toFinishedSpan(to: Instant, metricTags: TagSet): Span.Finished =
      Span.Finished(id, parentId, trace, _operationName, kind, position, from, to, _spanTags.build(), metricTags, _marks, _links)

    private def recordSpanMetrics(to: Instant, metricTags: TagSet): Unit = {
      val processingTime = Clock.nanosBetween(from, to)
      Span.Metrics.ProcessingTime.withTags(metricTags).record(processingTime)
    }

    private def createMetricTags(): TagSet = {
      _metricTags.add(TagKeys.OperationName, _operationName)
      _metricTags.add(TagKeys.Error, _hasError)

      if(tagWithParentOperation)
        localParent.foreach {
          case p: Span.Local  => _metricTags.add(TagKeys.ParentOperationName, p.operationName())
          case _              => // Can't get an operation name from anything else than a local span.
        }

      _metricTags.build()
    }
  }

  object Local {
    private val _logger = LoggerFactory.getLogger(classOf[Span.Local])
  }

  /**
    * A immutable, no-op Span that can be used to signal that there is no Span information. An empty Span completely
    * ignores all writes made to it.
    */
  object Empty extends Span {
    override def id: Identifier = Identifier.Empty
    override def parentId: Identifier = Identifier.Empty
    override def trace: Trace = Trace.Empty
    override def kind: Kind = Kind.Unknown
    override def isRemote: Boolean = false
    override def isEmpty: Boolean = true
    override def position(): Position = Position.Unknown
    override def tag(key: String, value: String): Span = this
    override def tag(key: String, value: Long): Span = this
    override def tag(key: String, value: Boolean): Span = this
    override def tagMetric(key: String, value: String): Span = this
    override def tagMetric(key: String, value: Long): Span = this
    override def tagMetric(key: String, value: Boolean): Span = this
    override def mark(key: String): Span = this
    override def mark(at: Instant, key: String): Span = this
    override def link(span: Span, kind: Link.Kind): Span = this
    override def fail(errorMessage: String): Span = this
    override def fail(cause: Throwable): Span = this
    override def fail(errorMessage: String, cause: Throwable): Span = this
    override def name(name: String): Span = this
    override def trackProcessingTime(): Span = this
    override def doNotTrackProcessingTime(): Span = this
    override def finish(): Unit = {}
    override def finish(at: Instant): Unit = {}
    override def operationName(): String = "empty"
  }


  /**
    * A immutable, no-op Span that holds information from a Span that was initially created in another process and then
    * transferred to this process. This is the minimal representation of a Span that gets transferred through Context
    * propagation channels. A remote Span completely ignores all writes made to it.
    */
  final case class Remote(id: Identifier, parentId: Identifier, trace: Trace) extends Span {
    override def kind: Kind = Kind.Unknown
    override def isRemote: Boolean = true
    override def isEmpty: Boolean = false
    override def position(): Position = Position.Unknown
    override def tag(key: String, value: String): Span = this
    override def tag(key: String, value: Long): Span = this
    override def tag(key: String, value: Boolean): Span = this
    override def tagMetric(key: String, value: String): Span = this
    override def tagMetric(key: String, value: Long): Span = this
    override def tagMetric(key: String, value: Boolean): Span = this
    override def mark(key: String): Span = this
    override def mark(at: Instant, key: String): Span = this
    override def link(span: Span, kind: Link.Kind): Span = this
    override def fail(errorMessage: String): Span = this
    override def fail(cause: Throwable): Span = this
    override def fail(errorMessage: String, cause: Throwable): Span = this
    override def name(name: String): Span = this
    override def trackProcessingTime(): Span = this
    override def doNotTrackProcessingTime(): Span = this
    override def finish(): Unit = {}
    override def finish(at: Instant): Unit = {}
    override def operationName(): String = "empty"
  }


  /**
    * Metrics tracked by the Span implementation.
    */
  object Metrics {

    val ProcessingTime = Kamon.timer(
      name = "span.processing-time",
      description = "Tracks the elapsed time between the starting and finishing a Span."
    )
  }


  /**
    * Tag keys used by the implementations to record Span and metric tags.
    */
  object TagKeys {
    val Error = "error"
    val ErrorMessage = "error.message"
    val ErrorStacktrace = "error.stacktrace"
    val Component = "component"
    val OperationName = "operation"
    val ParentOperationName = "parentOperation"
    val UpstreamName = "upstream.name"
  }
}
