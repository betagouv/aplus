package helper

import scala.language.implicitConversions

object TwirlImports {

  /** This fixes the fact that a lot of twirl helpers take `(Symbol, String)` and
    * the fact that the notation `'mySymbol` is deprecated in scala 2.13.
    */
  implicit def stringStringToSymbolString(couple: (String, String)): (Symbol, String) =
    (Symbol(couple._1), couple._2)

}
