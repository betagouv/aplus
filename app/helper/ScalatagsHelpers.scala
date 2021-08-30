package helper

import play.api.http.{ContentTypes, Writeable}
import play.api.mvc.Codec
import scalatags.Text.all.Modifier

object ScalatagsHelpers {

  /** Import this implicit to create a HTML doc from a scalatags Modifier.
    *
    * If `page: Tag` then `play.api.mvc.Results.Ok(page)` works.
    */
  implicit def writeableOf_Modifier(implicit codec: Codec): Writeable[Modifier] =
    Writeable(content => codec.encode("<!DOCTYPE html>" + content), Some(ContentTypes.HTML))

}
