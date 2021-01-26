package helper

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

}
