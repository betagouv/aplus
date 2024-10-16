package aplus.macros

import anorm.RowParser
import scala.quoted._

object Macros {

  inline def parserWithFields[T](inline names: String*): (RowParser[T], List[String]) =
    ${ parserWithFieldsImpl[T]('names) }

  private def parserWithFieldsImpl[T: Type](
      names: Expr[Seq[String]]
  )(using Quotes): Expr[(RowParser[T], List[String])] = {
    import quotes.reflect._

    val anormImpl = '{ anorm.Macro.parser[T]($names: _*) }

    '{ ($anormImpl, $names.toList) }
  }

}
