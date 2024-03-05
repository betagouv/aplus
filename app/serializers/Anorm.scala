package serializers

import anorm.{Column, MetaDataItem, RowParser, ToStatement, TypeDoesNotMatch}
import anorm.Column.nonNull
import anorm.SqlParser.get
import helper.UUIDHelper
import java.util.UUID
import play.api.libs.json.{JsValue, Json}

object Anorm {

  @SuppressWarnings(Array("scalafix:DisableSyntax.=="))
  @inline def className(that: Any): String =
    if (that == null) "<null>" else that.getClass.getName

  implicit val fieldsMapStringParser: anorm.Column[Map[String, String]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Right(Json.parse(json.getValue).as[Map[String, String]])
        case json: String =>
          Right(Json.parse(json).as[Map[String, String]])
        case _ =>
          Left(
            TypeDoesNotMatch(
              s"Cannot convert $value: ${className(value)} to Map[String,String] for column $qualified"
            )
          )
      }
    }

  implicit val fieldsMapLongParser: anorm.Column[Map[String, Long]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Right(Json.parse(json.getValue).as[Map[String, Long]])
        case json: String =>
          Right(Json.parse(json).as[Map[String, Long]])
        case _ =>
          Left(
            TypeDoesNotMatch(
              s"Cannot convert $value: ${className(value)} to Map[String,Long] for column $qualified"
            )
          )
      }
    }

  private def convertStringMapToUUIDMap(map: Map[String, String]) =
    map.flatMap { case (key, value) =>
      UUIDHelper.fromString(key).map(_ -> value)
    }

  implicit val fieldsMapUUIDParser: anorm.Column[Map[UUID, String]] =
    nonNull { (value, meta) =>
      val MetaDataItem(qualified, _, _) = meta
      value match {
        case json: org.postgresql.util.PGobject =>
          Right(convertStringMapToUUIDMap(Json.parse(json.getValue).as[Map[String, String]]))
        case json: String =>
          Right(convertStringMapToUUIDMap(Json.parse(json).as[Map[String, String]]))
        case _ =>
          Left(
            TypeDoesNotMatch(
              s"Cannot convert $value: ${className(value)} to Map[UUID,String] for column $qualified"
            )
          )
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
      case json: String                       => Right(Json.parse(json))
      case _ =>
        Left(
          TypeDoesNotMatch(
            s"Cannot convert $value: ${value.asInstanceOf[AnyRef].getClass} to Json for column $qualified"
          )
        )
    }
  }

  protected[this] def json(name: String): RowParser[JsValue] = get[JsValue](name)
}
