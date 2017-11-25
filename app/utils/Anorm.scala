package utils

import anorm.Column.nonNull
import anorm.{Column, MetaDataItem, RowParser, ToStatement, TypeDoesNotMatch}
import play.api.libs.json.{JsValue, Json}
import anorm.SqlParser.get

object Anorm {
  @inline private def className(that: Any): String =
    if (that == null) "<null>" else that.getClass.getName

   implicit val fieldsMapParser: anorm.Column[Map[String,String]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, nullable, clazz) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Right(Json.parse(json.getValue).as[Map[String,String]])
        case json: String =>
          Right(Json.parse(json).as[Map[String,String]])
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${className(value)} to Map[String,String] for column $qualified"))
      }
    }


    implicit object jsonToStatement extends ToStatement[JsValue] {
      override def set(s: java.sql.PreparedStatement, index: Int, v: JsValue): Unit = {
        val jsonObject = new org.postgresql.util.PGobject()
        jsonObject.setType("json")
        jsonObject.setValue(Json.stringify(v))
        s.setObject(index, jsonObject)
      }
    }

    implicit val columnToJson: Column[JsValue] = Column.nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta
      value match {
        case json: org.postgresql.util.PGobject => Right(Json.parse(json.getValue))
        case json: String => Right(Json.parse(json))
        case _ => Left(TypeDoesNotMatch(s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Json for column $qualified"))
      }
    }

    protected[this] def json(name: String): RowParser[JsValue] = get[JsValue](name)

}
