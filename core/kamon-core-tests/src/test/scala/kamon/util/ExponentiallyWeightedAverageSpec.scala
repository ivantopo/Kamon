package kamon.util

import munit.FunSuite

class ExponentiallyWeightedAverageSuite extends FunSuite {

  test("converge to the actual average in few iterations from startup with the default weighting factor") {
    val ewma = EWMA.create()
    Seq(60d, 40d, 55d, 45d, 50d, 50d, 50d).foreach(ewma.add)

    assertEqualsDouble(ewma.average(), 50d, 5d)
  }

  test("catch up with an up trend") {
    val ewma = EWMA.create()
    var value = 500

    (1 to 200).foreach { _ =>
      value += 5
      ewma.add(value)
    }

    assertEqualsDouble(ewma.average(), value.toDouble, 50d)
  }

  test("take many iterations to converge on the average with a high weighting factor") {
    val ewma = EWMA.create(0.99d)
    Seq(60d, 40d, 50d, 50d, 50d).foreach(ewma.add)

    (1 to 30).foreach { _ =>
      ewma.add(50d)
      ewma.add(50d)
      ewma.add(50d)
    }

    assertEqualsDouble(ewma.average(), 50d, 5d)
  }
}
