package views.helpers

import cats.syntax.all._
import models.Area
import play.api.mvc.Call
import scalatags.Text.all._
import serializers.Keys

object changeAreaSelect {

  def apply(
      currentArea: Area,
      selectableAreas: List[Area],
      redirectUrlPath: Call,
      query: (String, String)*
  ): Tag = {
    val queryString = {
      val qs = query.map { case (key, value) => s"$key=$value" }.mkString("&")
      if (qs.isEmpty) "" else (qs + "&")
    }
    val redirectUrlPrefix = s"$redirectUrlPath?$queryString${Keys.QueryParam.areaId}=";
    select(
      id := "changeAreaSelect",
      name := "area",
      data("current-area") := currentArea.id.toString,
      data("redirect-url-prefix") := redirectUrlPrefix,
      selectableAreas.map(area =>
        scalatags.Text.tags.option(
          value := area.id.toString,
          (area.id === currentArea.id).some.filter(identity).map(_ => selected),
          area.toString
        )
      )
    )
  }

}
