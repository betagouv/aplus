package helper

import play.twirl.api.Html
import scala.language.implicitConversions
import scalatags.Text.all.{frag, Frag, SeqFrag, Tag}

object TwirlImports {

  /** This fixes the fact that a lot of twirl helpers take `(Symbol, String)` and
    * the fact that the notation `'mySymbol` is deprecated in scala 2.13.
    */
  implicit def stringStringToSymbolString(couple: (String, String)): (Symbol, String) =
    (Symbol(couple._1), couple._2)

  implicit def toHtml(frag: Frag): Html = Html(frag.render)

  implicit def toHtml(tags: List[Tag]): Html = toHtml(frag(tags))

}
