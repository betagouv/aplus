package serializers

import helper.UUIDHelper
import java.util.UUID
import play.api.libs.json.Json.JsValueWrapper
import play.api.libs.json._
import play.api.libs.functional.syntax._

object JsonFormats {

  implicit val jsonConfiguration: JsonConfiguration =
    JsonConfiguration(naming = JsonNaming.SnakeCase)

  implicit val mapUUIDReads: Reads[Map[UUID, String]] = new Reads[Map[UUID, String]] {

    def reads(jv: JsValue): JsResult[Map[UUID, String]] =
      JsSuccess(jv.as[Map[String, String]].map { case (k, v) =>
        UUIDHelper.fromString(k).get -> v.asInstanceOf[String]
      })

  }

  implicit val mapUUIDWrites: Writes[Map[UUID, String]] = new Writes[Map[UUID, String]] {

    def writes(map: Map[UUID, String]): JsValue =
      Json.obj(map.map { case (s, o) =>
        val ret: (String, JsValueWrapper) = s.toString -> JsString(o)
        ret
      }.toSeq: _*)

  }

  implicit val mapUUIDFormat: Format[Map[UUID, String]] = Format(mapUUIDReads, mapUUIDWrites)

}
