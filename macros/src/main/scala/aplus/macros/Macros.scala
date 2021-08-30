package aplus.macros

import anorm.RowParser
import language.experimental.macros
import scala.reflect.macros.whitebox.Context

object Macros {

  def parserWithFields[T](names: String*): (RowParser[T], List[String]) =
    macro parserWithFieldsImpl[T]

  def parserWithFieldsImpl[T: c.WeakTypeTag](c: Context)(names: c.Expr[String]*): c.Expr[T] = {
    import c.universe._

    val anormImpl = anorm.Macro.namedParserImpl2[T](c)(names: _*)
    c.Expr(
      q"($anormImpl, List(..$names))"
    )
  }

}
