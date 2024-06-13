package helper

import cats.syntax.all._

object Math {

  // From : https://rosettacode.org/wiki/Averages/Median#Scala
  def median[T](s: Seq[T])(implicit n: Fractional[T]): T = {
    import n._
    val (lower, upper) = s.sortWith(_ < _).splitAt(s.size / 2)
    if (s.size % 2 === 0) (lower.last + upper.head) / fromInt(2) else upper.head
  }

}
