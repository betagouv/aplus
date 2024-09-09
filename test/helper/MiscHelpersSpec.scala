package helper

import cats.data.NonEmptyList
import helper.MiscHelpers.chooseByFrequency
import org.junit.runner.RunWith
import org.specs2.mutable.Specification
import org.specs2.runner.JUnitRunner
import scala.math.abs

@RunWith(classOf[JUnitRunner])
class MiscHelpersSpec extends Specification {

  "chooseByFrequency should" >> {
    "pick elements with probability equal to their weight / sum(weights)" >> {
      import cats.syntax.all.catsSyntaxEq

      val elements = NonEmptyList.of(
        (1.0, "el1"),
        (3.0, "el2")
      )
      val el1Probability = 0.25
      val el2Probability = 0.75
      val sampleSize = 1000000

      val pickabunch =
        Iterator.continually(chooseByFrequency(elements)).take(sampleSize).toVector
      val el1Frequency = pickabunch.count(_ === "el1").toDouble / sampleSize.toDouble
      val el2Frequency = pickabunch.count(_ === "el2").toDouble / sampleSize.toDouble

      val el1FreqAlmostProb = abs(el1Probability - el1Frequency) < 0.01
      val el2FreqAlmostProb = abs(el2Probability - el2Frequency) < 0.01

      el1FreqAlmostProb must beTrue
      el2FreqAlmostProb must beTrue
    }

  }

}
