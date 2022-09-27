package helper

import cats.data.NonEmptyList
import java.security.SecureRandom
import scala.util.Random

object MiscHelpers {

  // https://github.com/typelevel/cats/blob/master/core/src/main/scala/cats/Foldable.scala#L775
  def intersperseList[A](xs: List[A], x: A): List[A] = {
    val bld = List.newBuilder[A]
    val it = xs.iterator
    if (it.hasNext) {
      bld += it.next()
      while (it.hasNext) {
        bld += x
        bld += it.next()
      }
    }
    bld.result()
  }

  /** `elements` is a list of (weight, element) This method chooses elements in the list randomly
    * such that the probability of choosing an element is its frequency (weight / sum(weights)).
    * (see tests for examples)
    */
  def chooseByFrequency[A](elements: NonEmptyList[(Double, A)]): A = {
    val weightSum = elements.map { case (weight, _) => weight }.toList.sum
    val random: Double = Random.between(0.0, 1.0)
    val (randomElement, _) = elements
      .foldLeft[(Option[A], Double)]((None, 0.0)) {
        case ((lastChosen, lastBound), (weight, element)) =>
          val bound = lastBound + weight / weightSum
          val chosen = lastChosen.orElse(if (random <= bound) Some(element) else None)
          (chosen, bound)
      }
    // .getOrElse here is for rare case of float arithmetics that would not sum to 1.0
    randomElement.getOrElse(elements.last._2)
  }

  def secureRandom = new Random(SecureRandom.getInstanceStrong())

}
