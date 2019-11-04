package services

import java.util.concurrent.TimeUnit

import javax.inject.Inject
import play.api.Configuration
import play.api.libs.json._
import play.api.libs.ws.{WSClient, WSResponse}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

@javax.inject.Singleton
class AreaService @Inject()(configuration: Configuration, ws: WSClient)(implicit ec: ExecutionContext) {

  private val host = configuration.underlying.getString("es.host")

  def getNameFromCode(code: String): String = {
    def extract(r: WSResponse): JsResult[JsString] = {
      val reader: Reads[JsString] = (__ \ 'hits \ 'hits \ 0 \ '_source \ 'name)
        .json
        .pick[JsString]

      Json
        .parse(r.body)
        .transform(reader)
    }

    Await.result(ws.url(s"http://$host:9200/_search?pretty")
      .addHttpHeaders("Content-Type" -> "application/json")
      .withBody(s"""{"query" : {"match" : {"code" : {"query" : "$code"}}}}""")
      .get()
      .map(extract), Duration(10, TimeUnit.SECONDS)).asOpt.map(_.value).getOrElse("")
  }

  def search(query: String): Future[JsResult[JsArray]] = {
    def extract(r: WSResponse): JsResult[JsArray] = {
      val reader: Reads[JsArray] = (__ \ 'hits \ 'hits)
        .json
        .pick[JsArray]
        .map({ jsArray: JsArray => JsArray(jsArray.\\("_source")) })

      Json
        .parse(r.body)
        .transform(reader)
    }

    ws.url(s"http://$host:9200/_search?pretty")
      .addHttpHeaders("Content-Type" -> "application/json")
      .withBody(s"""{"query" : {"match" : {"name" : {"query" : "$query","fuzziness" : 2}}}}""")
      .get()
      .map(extract)
  }
}
