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

    val request =
      s"""
        |{ "query" : {
        |       "function_score" : {
        |           "query" : {
        |               "multi_match" : {
        |                   "query": "$query",
        |                   "fields" : ["code^1", "name^1"],
        |                   "fuzziness" : "10",
        |                   "prefix_length" : 2
        |               }
        |           },
        |           "functions": [{
        |               "filter" : { "match" : { "type" : "Commune" } },
        |               "weight" : 1.05
        |           }, {
        |               "filter" : { "match" : { "type" : "Region" } },
        |               "weight" : 1.20
        |           }, {
        |               "filter" : { "match" : { "type" : "Arrondissement" } },
        |               "weight" : 1.15
        |           }, {
        |               "filter" : { "match" : { "type" : "Departement" } },
        |               "weight" : 1.10
        |           }, {
        |               "filter" : { "match" : { "type" : "CantonOuVille2015" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "Canton2015" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CommunauteCommunes" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CantonOuVille" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "Canton" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CommuneDeleguee" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "MetropoleOuAssimilee" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CommuneEnDouble" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CommuneFusionnee" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CollectiviteDepartementaleOuCollectiviteTerritorialeEquivalente" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "SubdivisionPolynesieFrancaise" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "SubdivisionNouvelleCaledonie" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CommunauteCommunes" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CommunauteAgglomeration" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CirconscriptionLegislative" } },
        |               "weight" : 0
        |           }, {
        |               "filter" : { "match" : { "type" : "CirconscriptionWallisFutuna" } },
        |               "weight" : 0
        |           }]
        |       }
        |}}
        |""".stripMargin

    // CommunauteAgglomeration

    ws.url(s"http://$host:9200/_search?pretty")
      .addHttpHeaders("Content-Type" -> "application/json")
      .withBody(request)
      .get()
      .map(extract)
  }
}
