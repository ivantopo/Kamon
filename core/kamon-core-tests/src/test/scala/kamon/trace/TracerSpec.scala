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

import java.time.Instant
import kamon.Kamon
import kamon.tag.Lookups._
import kamon.testkit.{Reconfigure, SpanInspection}
import kamon.testkit.munit.InitAndStopKamonAfterAll
import kamon.trace.Identifier.Factory.EightBytesIdentifier
import kamon.trace.Span.Position
import kamon.trace.Trace.SamplingDecision
import kamon.trace.Hooks.{PreFinish, PreStart}
import munit.FunSuite

class TracerSpec extends FunSuite with SpanInspection.Syntax with InitAndStopKamonAfterAll {

  test("construct a minimal Span that only has a operation name and default metric tags") {
    val span = Kamon.spanBuilder("myOperation").start()

    assertEquals(span.operationName(), "myOperation")
    assert(span.spanTags().isEmpty())
    assertEquals(span.metricTags().get(plain("operation")), "myOperation")
    assertEquals(span.metricTags().get(plainBoolean("error")), Boolean.box(false))
  }

  test("pass the operation name and tags to started Span") {
    val span = Kamon.spanBuilder("myOperation")
      .tagMetrics("metric-tag", "value")
      .tagMetrics("metric-tag", "value")
      .tag("hello", "world")
      .tag("kamon", "rulez")
      .tag("number", 123)
      .tag("boolean", true)
      .start()

    assertEquals(span.operationName(), "myOperation")
    assertEquals(span.metricTags().get(plain("metric-tag")), "value")
    assertEquals(span.spanTags().get(plain("hello")), "world")
    assertEquals(span.spanTags().get(plain("kamon")), "rulez")
    assertEquals(span.spanTags().get(plainLong("number")), Long.box(123L))
    assertEquals(span.spanTags().get(plainBoolean("boolean")), Boolean.box(true))
  }

  test("not have any parent Span if there is no Span in the current context and no parent was explicitly given") {
    val span = Kamon.spanBuilder("myOperation").start()
    assertEquals(span.parentId, Identifier.Empty)
  }

  test("automatically take the Span from the current Context as parent") {
    val parent = Kamon.spanBuilder("myOperation").start()
    val child = Kamon.runWithSpan(parent) {
      Kamon.spanBuilder("childOperation").asChildOf(parent).start()
    }

    assertEquals(child.parentId, parent.id)
  }

  test("ignore the span from the current context as parent if explicitly requested") {
    val parent = Kamon.spanBuilder("myOperation").start()
    val child = Kamon.runWithSpan(parent) {
      Kamon.spanBuilder("childOperation").ignoreParentFromContext().start()
    }

    assertEquals(child.parentId, Identifier.Empty)
  }

  test("allow providing a custom start timestamp for a Span") {
    val span = Kamon.spanBuilder("myOperation").start(Instant.EPOCH.plusMillis(321)).toFinished()
    assertEquals(span.from, Instant.EPOCH.plusMillis(321))
  }

  test("preserve the same Span and Parent identifier when creating a server Span with a remote parent if join-remote-parents-with-same-span-id is enabled") {
    Reconfigure.enableJoiningRemoteParentWithSameId()

    val remoteParent = remoteSpan(SamplingDecision.Sample)
    val child = Kamon.spanBuilder("local").asChildOf(remoteParent).kind(Span.Kind.Server).start()

    assertEquals(child.id, remoteParent.id)
    assertEquals(child.parentId, remoteParent.parentId)
    assertEquals(child.trace.id, remoteParent.trace.id)

    Reconfigure.reset()
  }

  test("propagate sampling decisions from parent to child spans, if the decision is known") {
    val sampledRemoteParent = remoteSpan(SamplingDecision.Sample)
    val notSampledRemoteParent = remoteSpan(SamplingDecision.DoNotSample)

    assertEquals(
      Kamon.spanBuilder("childOfSampled").asChildOf(sampledRemoteParent).start().trace.samplingDecision,
      SamplingDecision.Sample
    )

    assertEquals(
      Kamon.spanBuilder("childOfNotSampled").asChildOf(notSampledRemoteParent).start().trace.samplingDecision,
      SamplingDecision.DoNotSample
    )
  }

  test("take a sampling decision if the parent's decision is unknown") {
    Reconfigure.sampleAlways()

    val unknownSamplingRemoteParent = remoteSpan(SamplingDecision.Unknown)
    assertEquals(
      Kamon.spanBuilder("childOfSampled").asChildOf(unknownSamplingRemoteParent).start().trace.samplingDecision,
      SamplingDecision.Sample
    )

    Reconfigure.reset()
  }

  test("never sample ignored operations") {
    Reconfigure.sampleAlways()

    assertEquals(Kamon.spanBuilder("/ready").start().trace.samplingDecision, SamplingDecision.DoNotSample)
    assertEquals(Kamon.spanBuilder("/status").start().trace.samplingDecision, SamplingDecision.DoNotSample)
    assertEquals(Kamon.spanBuilder("/other").start().trace.samplingDecision, SamplingDecision.Sample)

    Reconfigure.reset()
  }

  test("figure out the position of a Span in its trace") {
    assertEquals(Kamon.spanBuilder("root").start().position, Position.Root)
    assertEquals(Kamon.spanBuilder("localRoot").asChildOf(remoteSpan()).start().position, Position.LocalRoot)
  }

  test("ignore sampling decision suggestions when the parent Span's trace has a decision already") {
    val sampledParent = remoteSpan(SamplingDecision.Sample)
    val notSampledParent = remoteSpan(SamplingDecision.DoNotSample)

    assertEquals(
      Kamon.spanBuilder("suggestions")
        .asChildOf(sampledParent)
        .samplingDecision(SamplingDecision.Unknown)
        .start()
        .trace.samplingDecision,
      SamplingDecision.Sample
    )

    assertEquals(
      Kamon.spanBuilder("suggestions")
        .asChildOf(notSampledParent)
        .samplingDecision(SamplingDecision.Unknown)
        .start()
        .trace.samplingDecision,
      SamplingDecision.DoNotSample
    )
  }

  test("use sampling decision suggestions when there is no parent") {
    assertEquals(
      Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.Unknown)
        .start()
        .trace.samplingDecision,
      SamplingDecision.Unknown
    )

    assertEquals(
      Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.Sample)
        .start()
        .trace.samplingDecision,
      SamplingDecision.Sample
    )

    assertEquals(
      Kamon.spanBuilder("suggestions")
        .samplingDecision(SamplingDecision.DoNotSample)
        .start()
        .trace.samplingDecision,
      SamplingDecision.DoNotSample
    )
  }

  test("use sampling decision suggestions when the parent has an unknown sampling decision") {
    assertEquals(
      Kamon.spanBuilder("suggestions")
        .asChildOf(remoteSpan(SamplingDecision.Unknown))
        .samplingDecision(SamplingDecision.Sample)
        .start()
        .trace.samplingDecision,
      SamplingDecision.Sample
    )

    assertEquals(
      Kamon.spanBuilder("suggestions")
        .asChildOf(remoteSpan(SamplingDecision.Unknown))
        .samplingDecision(SamplingDecision.DoNotSample)
        .start()
        .trace.samplingDecision,
      SamplingDecision.DoNotSample
    )

    assertEquals(
      Kamon.spanBuilder("suggestions")
        .asChildOf(remoteSpan(SamplingDecision.Unknown))
        .samplingDecision(SamplingDecision.Unknown)
        .start()
        .trace.samplingDecision,
      SamplingDecision.Unknown
    )
  }

  test("not let Spans with remote parents remain with a Unknown sampling decision, even without suggestions") {
    assertNotEquals(
      Kamon.spanBuilder("suggestions")
        .asChildOf(remoteSpan(SamplingDecision.Unknown))
        .start()
        .trace.samplingDecision,
      SamplingDecision.Unknown
    )
  }

  test("not change a Spans sampling decision if they were created with Sample or DoNotSample decisions sampling decision") {
    val sampledSpan = Kamon.spanBuilder("suggestions")
      .samplingDecision(SamplingDecision.Sample)
      .start()

    val notSampledSpan = Kamon.spanBuilder("suggestions")
      .samplingDecision(SamplingDecision.DoNotSample)
      .start()

    assertEquals(sampledSpan.trace.samplingDecision, SamplingDecision.Sample)
    sampledSpan.takeSamplingDecision()
    assertEquals(sampledSpan.trace.samplingDecision, SamplingDecision.Sample)

    assertEquals(notSampledSpan.trace.samplingDecision, SamplingDecision.DoNotSample)
    notSampledSpan.takeSamplingDecision()
    assertEquals(notSampledSpan.trace.samplingDecision, SamplingDecision.DoNotSample)
  }

  test("allow Spans to take a sampling decision if they were created with Unknown sampling decision") {
    val span = Kamon.spanBuilder("suggestions")
      .samplingDecision(SamplingDecision.Unknown)
      .start()

    assertEquals(span.trace.samplingDecision, SamplingDecision.Unknown)
    span.takeSamplingDecision()
    assertNotEquals(span.trace.samplingDecision, SamplingDecision.Unknown)
  }

  test("ensure that all local Spans share the exact same Trace instance") {
    val remoteParent = remoteSpan()
    val parent = Kamon.spanBuilder("parent").asChildOf(remoteParent).start()
    val child = Kamon.spanBuilder("child").asChildOf(parent).start()
    val grandChild = Kamon.spanBuilder("grandChild").asChildOf(child).start()

    assert(parent.trace eq remoteParent.trace)
    assert(child.trace eq remoteParent.trace)
    assert(grandChild.trace eq remoteParent.trace)
  }

  test("allow explicitly dropping traces") {
    val span = Kamon.spanBuilder("suggestions")
      .samplingDecision(SamplingDecision.Sample)
      .start()

    assertEquals(span.trace.samplingDecision, SamplingDecision.Sample)
    span.trace.drop()
    assertEquals(span.trace.samplingDecision, SamplingDecision.DoNotSample)
  }

  test("allow explicitly keep traces") {
    val span = Kamon.spanBuilder("suggestions")
      .samplingDecision(SamplingDecision.DoNotSample)
      .start()

    assertEquals(span.trace.samplingDecision, SamplingDecision.DoNotSample)
    span.trace.keep()
    assertEquals(span.trace.samplingDecision, SamplingDecision.Sample)
  }

  test("apply pre-start hooks to all Spans") {
    val span = Kamon.runWithContextEntry(PreStart.Key, PreStart.updateOperationName("customName")) {
      Kamon.spanBuilder("defaultOperationName").start()
    }

    assertEquals(span.operationName(), "customName")
  }

  test("apply pre-finish hooks to all Spans") {
    val span = Kamon.spanBuilder("defaultOperationName").start()
    Kamon.runWithContextEntry(PreFinish.Key, PreFinish.updateOperationName("customName")) {
      span.finish()
    }

    assertEquals(span.operationName(), "customName")
  }

  test("collect exception information for failed Spans") {
    val byMessage = Kamon.spanBuilder("o1").start()
      .fail("byMessage")
    val byException = Kamon.spanBuilder("o2").start()
      .fail(new RuntimeException("byException"))
    val byMessageAndException = Kamon.spanBuilder("o3").start()
      .fail("byMessageAndException", new RuntimeException("byException"))

    Reconfigure.applyConfig("kamon.trace.include-error-stacktrace=false")
    val byExceptionStacktraceDisabled = Kamon.spanBuilder("o4").start()
      .fail(new RuntimeException("byExceptionStacktraceDisabled"))

    Reconfigure.applyConfig("kamon.trace.include-error-type=false")
    val byExceptionTypeDisabled = Kamon.spanBuilder("o5").start()
      .fail(new RuntimeException("byExceptionTypeEnabled"))

    assertEquals(byMessage.metricTags().get(plainBoolean("error")), Boolean.box(true))
    assertEquals(byMessage.spanTags().get(plain("error.message")), "byMessage")
    assertEquals(byMessage.spanTags().get(option("error.stacktrace")), None)
    assertEquals(byMessage.spanTags().get(option("error.type")), None)

    assertEquals(byException.metricTags().get(plainBoolean("error")), Boolean.box(true))
    assertEquals(byException.spanTags().get(plain("error.message")), "byException")
    assert(byException.spanTags().get(option("error.stacktrace")).isDefined)
    assertEquals(byException.spanTags().get(option("error.type")), Some("java.lang.RuntimeException"))

    assertEquals(byMessageAndException.metricTags().get(plainBoolean("error")), Boolean.box(true))
    assertEquals(byMessageAndException.spanTags().get(plain("error.message")), "byMessageAndException")
    assert(byMessageAndException.spanTags().get(option("error.stacktrace")).isDefined)
    assertEquals(byMessageAndException.spanTags().get(option("error.type")), Some("java.lang.RuntimeException"))

    assertEquals(byExceptionStacktraceDisabled.metricTags().get(plainBoolean("error")), Boolean.box(true))
    assertEquals(byExceptionStacktraceDisabled.spanTags().get(plain("error.message")), "byExceptionStacktraceDisabled")
    assertEquals(byExceptionStacktraceDisabled.spanTags().get(option("error.stacktrace")), None)
    assertEquals(byExceptionStacktraceDisabled.spanTags().get(option("error.type")), Some("java.lang.RuntimeException"))

    assertEquals(byExceptionTypeDisabled.metricTags().get(plainBoolean("error")), Boolean.box(true))
    assertEquals(byExceptionTypeDisabled.spanTags().get(plain("error.message")), "byExceptionTypeEnabled")
    assertEquals(byExceptionTypeDisabled.spanTags().get(option("error.stacktrace")), None)
    assertEquals(byExceptionTypeDisabled.spanTags().get(option("error.type")), None)
  }

  private def remoteSpan(samplingDecision: SamplingDecision = SamplingDecision.Sample): Span.Remote =
    Span.Remote(
      EightBytesIdentifier.generate(),
      EightBytesIdentifier.generate(),
      Trace(EightBytesIdentifier.generate(), samplingDecision)
    )

}
