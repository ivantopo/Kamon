package kamon.trace

import kamon.tag.Lookups._
import kamon.testkit.{Reconfigure, SpanInspection}
import kamon.testkit.munit.{Eventually, InitAndStopKamonAfterAll, TestSpanReporter}
import munit.FunSuite

import java.time.Duration
import java.util.concurrent.CompletableFuture
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class QuickSpanCreationSpec extends FunSuite with SpanInspection.Syntax
    with Eventually with TestSpanReporter with Reconfigure with InitAndStopKamonAfterAll {

  import kamon.Kamon.{currentSpan, span}

  test("create a Span and set it as the current Span while running the provided function") {
    span("makeCurrent") {
      assertEquals(currentSpan().operationName(), "makeCurrent")
    }
  }

  test("finish Spans automatically") {
    span("finishSimpleSpan") {
      "I'm done right away"
    }

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.operationName, "finishSimpleSpan")
    }
  }

  test("finish and fail Spans if an exception is thrown while running the wrapped code") {
    intercept[Throwable] {
      span("failSpanWithThrowable") {
        throw new Throwable("I'm never going to finish nicely")
      }
    }

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.operationName, "failSpanWithThrowable")
      assert(reportedSpan.get.hasError)
    }
  }

  test("apply the component tag, if provided") {
    span("finishSimpleSpanWithComponent", "customComponentTag") {
      "I'm done right away"
    }

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.operationName, "finishSimpleSpanWithComponent")
      assertEquals(reportedSpan.get.metricTags.get(any("component")), "customComponentTag")
    }
  }

  test("finish Spans automatically after the returned Scala Future finishes") {
    span("finishScalaFuture") {
      Future {
        Thread.sleep(1000)
        "I'm done after a second"
      }
    }

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.operationName, "finishScalaFuture")
      assert(Duration.between(reportedSpan.get.from, reportedSpan.get.to).toMillis >= 1000L)
    }
  }

  test("finish Spans automatically after the returned CompletionStage finishes") {
    span("finishCompletionStage") {

      val completableFuture = new CompletableFuture[String]
      global.execute(new Runnable {
        override def run(): Unit = {
          Thread.sleep(1000)
          completableFuture.complete("I'm done after a second")
        }
      })

      completableFuture
    }

    eventually(timeout = 5.seconds) {
      val reportedSpan = testSpanReporter().nextSpan()
      assert(reportedSpan.isDefined)
      assertEquals(reportedSpan.get.operationName, "finishCompletionStage")
      assert(Duration.between(reportedSpan.get.from, reportedSpan.get.to).toMillis >= 1000L)
    }
  }
}
